package com.reserv_engine.service;

import com.reserv_engine.core.domain.HoldStatus;
import com.reserv_engine.core.domain.ResourceUnitStatus;
import com.reserv_engine.dto.ConfirmReservationRequest;
import com.reserv_engine.dto.ConfirmReservationRequest.LinePriceRequest;
import com.reserv_engine.dto.ReservationResponse;
import com.reserv_engine.dto.ReservationResponse.ReservationLineResponse;
import com.reserv_engine.entity.Hold;
import com.reserv_engine.entity.HoldLine;
import com.reserv_engine.entity.Reservation;
import com.reserv_engine.entity.ReservationLine;
import com.reserv_engine.entity.ResourceUnit;
import com.reserv_engine.exception.ResourceConflictException;
import com.reserv_engine.exception.ResourceNotFoundException;
import com.reserv_engine.repository.HoldRepository;
import com.reserv_engine.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * [Engine] Public-facing confirm (Hold -> Reservation) API.
 *
 * Unlike HoldService/HoldWriteService, this is deliberately a SINGLE
 * @Transactional method, not a two-bean REQUIRES_NEW split. That split
 * existed to solve one specific problem: two concurrent INSERTs racing on
 * a UNIQUE constraint, where the loser needs an untainted transaction to
 * fall back and read the winner's already-committed row. Confirm's
 * uniqueness contention (reservation.hold_id) is not expected to see that
 * kind of two-different-callers race under normal traffic — a Hold can
 * only be confirmed by whoever is holding it, so "two concurrent confirms
 * for the same Hold" realistically means a retry/double-click, not two
 * strangers racing for the same resource the way hold-creation was.
 *
 * KNOWN LIMITATION (accepted for now, not silently ignored): if a true
 * concurrent double-confirm ever DID happen, the loser's INSERT would
 * fail on the hold_id unique constraint and roll back this whole
 * transaction — including its own read of "does a Reservation already
 * exist" from earlier — so it would surface as a 500, not a clean
 * "already confirmed" response. If that ever becomes a real scenario
 * (not just same-user retry), revisit with the same REQUIRES_NEW pattern
 * used in HoldWriteService.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final HoldRepository holdRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    public ReservationResponse confirm(ConfirmReservationRequest request) {
        var existing = reservationRepository.findByHoldIdWithLines(request.holdId());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Hold hold = holdRepository.findByIdWithLines(request.holdId())
                .orElseThrow(() -> new ResourceNotFoundException("Hold not found: " + request.holdId()));


        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new ResourceConflictException(
                    "Hold is not ACTIVE, cannot confirm: " + hold.getId() + " (status=" + hold.getStatus() + ")");
        }
        if (hold.isExpired()) {
            throw new ResourceConflictException("Hold has expired, cannot confirm: " + hold.getId());
        }

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

        Reservation saved = reservationRepository.save(reservation);
        return toResponse(saved);
    }

    private ReservationLine buildReservationLine(Reservation reservation, HoldLine line, BigDecimal lockedPrice) {
        if (line.getResourceUnit() != null) {
            ResourceUnit unit = line.getResourceUnit();
            // No lock needed here the way create-hold needed one: this unit
            // is already exclusively HELD by this same Hold, nobody else can
            // be racing to touch it. @Version still protects against the
            // expiry-reaper-race case above, since a losing confirm would
            // fail this transaction's flush, not corrupt the unit.
            unit.setStatus(ResourceUnitStatus.RESERVED);
            return new ReservationLine(reservation, unit, lockedPrice);
        }
        // COUNTER_BASED: capacity was already decremented at hold-creation
        // time and stays decremented — confirm does not touch ResourcePool.
        return new ReservationLine(reservation, line.getResourcePool(), line.getQuantity(), lockedPrice);
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