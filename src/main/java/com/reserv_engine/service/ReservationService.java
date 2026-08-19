package com.reserv_engine.service;

import com.reserv_engine.dto.ConfirmReservationRequest;
import com.reserv_engine.dto.ReservationResponse;
import com.reserv_engine.dto.ReservationResponse.ReservationLineResponse;
import com.reserv_engine.entity.Reservation;
import com.reserv_engine.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * [Engine] Public-facing confirm (Hold -> Reservation) API.
 *
 * Split from the actual write, same shape as HoldService/HoldWriteService,
 * for the same reason: DuplicateConfirmDiscoveryTest proved that under 20
 * concurrent identical confirm requests, ~45% (9/20) of losers were
 * surfacing as raw 500s — the losing INSERT hit uq_reservation_hold_id's
 * DataIntegrityViolationException, which nothing was catching. This
 * class's job now is ONLY the fast-path check and the recovery catch;
 * ReservationWriteService owns the actual REQUIRES_NEW write attempt.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationWriteService reservationWriteService;

    public ReservationResponse confirm(ConfirmReservationRequest request) {
        var existing = reservationRepository.findByHoldIdWithLines(request.holdId());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        try {
            Reservation created = reservationWriteService.attemptCreate(request);
            return toResponse(created);
        } catch (DataIntegrityViolationException | CannotAcquireLockException ex) {
            // Two distinct causes land here, both meaning the same thing —
            // someone else already committed while we were racing them:
            //  - DataIntegrityViolationException: a clean uq_reservation_hold_id
            //    duplicate-key rejection.
            //  - CannotAcquireLockException: MySQL chose this transaction as
            //    the deadlock victim (error 1213) instead of cleanly
            //    rejecting the duplicate key. Well-documented InnoDB
            //    behavior — concurrent inserts colliding on the same unique
            //    key each take a shared lock on the duplicate index record
            //    while waiting, and with enough concurrent losers some of
            //    them deadlock with each other rather than each getting a
            //    clean rejection. DuplicateConfirmDiscoveryTest is what
            //    surfaced this: catching only DataIntegrityViolationException
            //    left 9/16 losers as raw 500s instead of 7/16 — the
            //    deadlock-victim slice specifically.
            // Either way, the recovery is identical: the winner has already
            // committed by the time we're here, so re-querying finds it.
            return reservationRepository.findByHoldIdWithLines(request.holdId())
                    .map(this::toResponse)
                    .orElseThrow(() -> ex); // extremely unlikely: not found, rethrow original
        }
    }

    private ReservationResponse toResponse(Reservation reservation) {
        var lines = reservation.getReservationLines().stream()
                .map(line -> new ReservationLineResponse(
                        line.getResourcePool().getId(),
                        line.getResourceUnit() != null ? line.getResourceUnit().getId() : null,
                        line.getQuantity(),
                        line.getLockedPrice()
                ))
                .toList();
        return new ReservationResponse(
                reservation.getId(),
                reservation.getHoldId(),
                reservation.getStatus().name(),
                reservation.getConfirmedAt(),
                lines
        );
    }
}