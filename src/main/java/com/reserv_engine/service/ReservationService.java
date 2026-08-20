package com.reserv_engine.service;

import com.reserv_engine.dto.ConfirmReservationRequest;
import com.reserv_engine.dto.ReservationResponse;
import com.reserv_engine.dto.ReservationResponse.ReservationLineResponse;
import com.reserv_engine.entity.Reservation;
import com.reserv_engine.exception.ResourceConflictException;
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
        } catch (DataIntegrityViolationException | CannotAcquireLockException | ResourceConflictException ex) {
            // Three distinct causes now land here, all meaning "someone else may
            // have already committed while we were racing them":
            //  - DataIntegrityViolationException / CannotAcquireLockException:
            //    the INSERT itself lost (clean duplicate-key rejection or
            //    InnoDB deadlock victim).
            //  - ResourceConflictException: attemptCreate's own hold.getStatus()
            //    != ACTIVE check fired — which happens both when the winner has
            //    already committed CONSUMED (a race we should recover from) AND
            //    for genuinely non-confirmable holds (CANCELLED/EXPIRED/no
            //    payment — a real rejection, unrelated to this race).
            // The re-query below disambiguates the ResourceConflictException
            // case for free: if a reservation now exists, we lost the race and
            // should return it; if none exists, it was a genuine rejection and
            // the original exception is the correct thing to surface.
            return reservationRepository.findByHoldIdWithLines(request.holdId())
                    .map(this::toResponse)
                    .orElseThrow(() -> ex);
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