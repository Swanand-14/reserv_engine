package com.reserv_engine.service;

import com.reserv_engine.core.domain.HoldStatus;
import com.reserv_engine.core.domain.PaymentAttemptStatus;
import com.reserv_engine.core.domain.ResourceUnitStatus;
import com.reserv_engine.dto.ConfirmReservationRequest;
import com.reserv_engine.dto.ConfirmReservationRequest.LinePriceRequest;
import com.reserv_engine.entity.Hold;
import com.reserv_engine.entity.HoldLine;
import com.reserv_engine.entity.Reservation;
import com.reserv_engine.entity.ReservationLine;
import com.reserv_engine.entity.ResourceUnit;
import com.reserv_engine.exception.ResourceConflictException;
import com.reserv_engine.exception.ResourceNotFoundException;
import com.reserv_engine.repository.HoldRepository;
import com.reserv_engine.repository.PaymentAttemptRepository;
import com.reserv_engine.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * [Engine] The actual transactional attempt to create a Reservation —
 * separate bean, Propagation.REQUIRES_NEW, exact same reasoning as
 * HoldWriteService's own javadoc: if this ran under DEFAULT propagation
 * inside ReservationService's transaction, a losing INSERT here would
 * mark that shared transaction rollback-only, poisoning
 * ReservationService's subsequent fallback read. REQUIRES_NEW makes this
 * transaction's failure fully isolated — it rolls back independently
 * (including the hold.setStatus(CONSUMED) write below), and
 * ReservationService's recovery read runs in an unaffected transaction.
 */
@Service
@RequiredArgsConstructor
class ReservationWriteService {

    private final HoldRepository holdRepository;
    private final ReservationRepository reservationRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation attemptCreate(ConfirmReservationRequest request) {
        Hold hold = holdRepository.findByIdWithLines(request.holdId())
                .orElseThrow(() -> new ResourceNotFoundException("Hold not found: " + request.holdId()));

        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new ResourceConflictException(
                    "Hold is not ACTIVE, cannot confirm: " + hold.getId() + " (status=" + hold.getStatus() + ")");
        }
        if (hold.isExpired()) {
            throw new ResourceConflictException("Hold has expired, cannot confirm: " + hold.getId());
        }

        paymentAttemptRepository.findByHoldIdAndStatus(hold.getId(), PaymentAttemptStatus.SUCCESS)
                .orElseThrow(() -> new ResourceConflictException(
                        "No successful payment attempt found for Hold, cannot confirm: " + hold.getId()));

        Map<String, BigDecimal> pricesByHoldLineId = request.linePrices().stream()
                .collect(Collectors.toMap(LinePriceRequest::holdLineId, LinePriceRequest::price));

        Reservation reservation = new Reservation(hold.getId(), hold.getHolderId());

        for (HoldLine line : hold.getHoldLines()) {
            BigDecimal lockedPrice = pricesByHoldLineId.get(line.getId());
            if (lockedPrice == null) {
                throw new IllegalArgumentException("Missing price for hold line: " + line.getId());
            }
            reservation.addReservationLine(buildReservationLine(reservation, line, lockedPrice));
        }

        hold.setStatus(HoldStatus.CONSUMED);

        // If another concurrent request wins the race to commit first,
        // THIS insert fails on uq_reservation_hold_id —
        // DataIntegrityViolationException propagates out, through the real
        // proxy boundary, and Spring rolls back everything this
        // transaction did (including the hold.setStatus above)
        // automatically. ReservationService is what catches it and recovers.
        return reservationRepository.save(reservation);
    }

    private ReservationLine buildReservationLine(Reservation reservation, HoldLine line, BigDecimal lockedPrice) {
        if (line.getResourceUnit() != null) {
            ResourceUnit unit = line.getResourceUnit();
            unit.setStatus(ResourceUnitStatus.RESERVED);
            return new ReservationLine(reservation, unit, lockedPrice);
        }
        return new ReservationLine(reservation, line.getResourcePool(), line.getQuantity(), lockedPrice);
    }
}