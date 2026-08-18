package com.reserv_engine.service;

import com.reserv_engine.core.domain.HoldStatus;
import com.reserv_engine.core.domain.PaymentAttemptStatus;
import com.reserv_engine.dto.CreatePaymentAttemptRequest;
import com.reserv_engine.dto.PaymentAttemptResponse;
import com.reserv_engine.entity.Hold;
import com.reserv_engine.entity.PaymentAttempt;
import com.reserv_engine.exception.ResourceConflictException;
import com.reserv_engine.exception.ResourceNotFoundException;
import com.reserv_engine.repository.HoldRepository;
import com.reserv_engine.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Engine] Public-facing "attempt payment" API.
 *
 * Single @Transactional method, same style decision as ReservationService
 * and for the same reasoning: the two-bean REQUIRES_NEW split in
 * HoldWriteService exists to let a losing INSERT's transaction roll back
 * WITHOUT poisoning a fallback read in the same request. That split
 * matters when two DIFFERENT callers can race to create the same logical
 * row. A payment attempt is scoped to whoever holds the Hold — a genuine
 * two-different-callers race here is not the expected case, so a retried
 * request (same idempotencyKey) is the scenario this actually needs to
 * handle cleanly, not concurrent strangers.
 *
 * KNOWN LIMITATION (same shape as ReservationService's): a true concurrent
 * double-submit with the same idempotencyKey would fail this transaction's
 * INSERT on the unique constraint and surface as a raw error rather than
 * gracefully falling back to the existing row. Revisit with REQUIRES_NEW
 * if that ever becomes a real scenario.
 */
@Service
@RequiredArgsConstructor
public class PaymentAttemptService {

    private final PaymentAttemptRepository paymentAttemptRepository;
    private final HoldRepository holdRepository;

    @Transactional
    public PaymentAttemptResponse attemptPayment(CreatePaymentAttemptRequest request) {

        // Lock the Hold FIRST, before any other read — this must be the
        // transaction's very first DB statement. A locking read (FOR
        // UPDATE) does NOT itself establish this transaction's REPEATABLE
        // READ snapshot (only plain/consistent reads do, per InnoDB docs).
        // So placing it first means it serializes concurrent callers via
        // the row lock WITHOUT pinning a stale snapshot — the next plain
        // read (the idempotency check, right below) becomes the actual
        // "first consistent read" and establishes ITS snapshot only after
        // this thread's wait on the lock ends, i.e. only after whichever
        // transaction held the lock before it has already committed. That
        // ordering is what makes the SUCCESS-check below actually see a
        // just-committed winner instead of a stale pre-lock snapshot —
        // which was the root cause of ConcurrentPaymentSuccessRaceTest
        // finding up to 10 SUCCESS rows per Hold.

        Hold hold = holdRepository.findByIdForUpdate(request.holdId())
                .orElseThrow(() -> new ResourceNotFoundException("Hold not found: " + request.holdId()));
        var existing = paymentAttemptRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }



        // Same re-check principle as HoldExpiryService / ReservationService:
        // don't trust anything the client believed about the Hold before
        // this request landed.
        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new ResourceConflictException(
                    "Hold is not ACTIVE, cannot attempt payment: " + hold.getId()
                            + " (status=" + hold.getStatus() + ")");
        }
        if (hold.isExpired()) {
            throw new ResourceConflictException("Hold has expired, cannot attempt payment: " + hold.getId());
        }

        paymentAttemptRepository.findByHoldIdAndStatus(hold.getId(), PaymentAttemptStatus.SUCCESS)
                .ifPresent(success -> {
                    throw new ResourceConflictException(
                            "Hold already has a successful payment attempt: " + hold.getId());
                });

        PaymentAttempt attempt = new PaymentAttempt(hold.getId(), request.idempotencyKey(), request.amount());

        // Mock resolution — see CreatePaymentAttemptRequest.simulateSuccess
        // javadoc. A real gateway integration replaces this branch.
        if (request.simulateSuccess()) {
            attempt.markSuccess();
        } else {
            attempt.markFailed();
        }

        PaymentAttempt saved = paymentAttemptRepository.save(attempt);
        return toResponse(saved);
    }

    private PaymentAttemptResponse toResponse(PaymentAttempt attempt) {
        return new PaymentAttemptResponse(
                attempt.getId(),
                attempt.getHoldId(),
                attempt.getStatus().name(),
                attempt.getAmount(),
                attempt.getCreatedAt()
        );
    }
}