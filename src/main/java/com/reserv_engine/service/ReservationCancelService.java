package com.reserv_engine.service;

import com.reserv_engine.core.domain.ReservationStatus;
import com.reserv_engine.core.domain.ResourceUnitStatus;
import com.reserv_engine.dto.ReservationResponse;
import com.reserv_engine.dto.ReservationResponse.ReservationLineResponse;
import com.reserv_engine.entity.Reservation;
import com.reserv_engine.entity.ReservationLine;
import com.reserv_engine.entity.ResourcePool;
import com.reserv_engine.entity.ResourceUnit;
import com.reserv_engine.exception.ResourceConflictException;
import com.reserv_engine.exception.ResourceNotFoundException;
import com.reserv_engine.repository.ReservationRepository;
import com.reserv_engine.repository.ResourcePoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Engine] Cancels an already-CONFIRMED Reservation, releasing its
 * resources back to availability.
 *
 * Deliberately does NOT touch the originating Hold. Per the frozen
 * invariant, Hold -> CONSUMED is permanent — the Hold's job ended the
 * moment confirm created this Reservation. Reservation cancellation is a
 * separate lifecycle event entirely; only the Reservation and the
 * resources it named move.
 *
 * State changes on cancel, per line:
 *  - UNIT_BASED: ResourceUnit RESERVED -> AVAILABLE (not back to HELD —
 *    nothing is holding it anymore, it's simply free again).
 *  - COUNTER_BASED: ResourcePool.remainingCapacity gets the quantity back.
 *    This is the first point in the whole lifecycle where a counter-based
 *    pool's capacity is restored after confirm — confirm itself never
 *    touches the pool, and Hold-cancel only applies pre-confirm.
 *
 * Same small-own-copy-of-release-logic convention as HoldCancelService /
 * HoldExpiryService, for the same reason: each trigger-specific service
 * owns its release logic rather than sharing a helper across classes that
 * have each been independently proven under load.
 */
@Service
@RequiredArgsConstructor
public class ReservationCancelService {

    private final ReservationRepository reservationRepository;
    private final ResourcePoolRepository poolRepository;

    @Transactional
    public ReservationResponse cancel(String reservationId) {
        Reservation reservation = reservationRepository.findByIdWithLines(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new ResourceConflictException(
                    "Reservation is not CONFIRMED, cannot cancel: " + reservation.getId()
                            + " (status=" + reservation.getStatus() + ")");
        }

        for (ReservationLine line : reservation.getReservationLines()) {
            if (line.getResourceUnit() != null) {
                releaseUnitLine(line);
            } else {
                releaseCounterLine(line);
            }
        }

        reservation.setStatus(ReservationStatus.CANCELLED);

        return toResponse(reservation);
    }

    private void releaseUnitLine(ReservationLine line) {
        ResourceUnit unit = line.getResourceUnit();
        if (unit.getStatus() == ResourceUnitStatus.RESERVED) {
            unit.setStatus(ResourceUnitStatus.AVAILABLE);
        }
    }

    private void releaseCounterLine(ReservationLine line) {
        String poolId = line.getResourcePool().getId(); // proxy id access only, no query
        ResourcePool lockedPool = poolRepository.findByIdForUpdate(poolId)
                .orElseThrow(() -> new ResourceNotFoundException("ResourcePool not found: " + poolId));
        lockedPool.setRemainingCapacity(lockedPool.getRemainingCapacity() + line.getQuantity());
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