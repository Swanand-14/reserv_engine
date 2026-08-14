package com.reserv_engine.service;

import com.reserv_engine.core.domain.HoldStatus;
import com.reserv_engine.core.domain.ResourceUnitStatus;
import com.reserv_engine.dto.HoldLineResponse;
import com.reserv_engine.dto.HoldResponse;
import com.reserv_engine.entity.Hold;
import com.reserv_engine.entity.HoldLine;
import com.reserv_engine.entity.ResourcePool;
import com.reserv_engine.entity.ResourceUnit;
import com.reserv_engine.exception.ResourceConflictException;
import com.reserv_engine.exception.ResourceNotFoundException;
import com.reserv_engine.repository.HoldRepository;
import com.reserv_engine.repository.ResourcePoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Engine] User-triggered cancel of an ACTIVE Hold — releases its resources
 * immediately rather than waiting for TTL expiry.
 *
 * Deliberately a SEPARATE class from HoldExpiryService, with its own small
 * copies of the unit/counter release logic, rather than extracting a
 * shared helper. Same convention already established in this codebase
 * (HoldService/HoldWriteService, HoldExpiryReaper/HoldExpiryService) —
 * each trigger owns its release logic. Avoids touching the
 * already-proven-under-load HoldExpiryService for a cosmetic DRY win.
 *
 * CONCURRENCY: cancel and the expiry reaper could both try to release the
 * same Hold at once (an expired-but-not-yet-reaped Hold is still ACTIVE
 * when a cancel request lands). Neither path locks the Hold row up front,
 * but Hold carries @Version, and both paths flip hold.status in the SAME
 * transaction as the resource release. Whichever commits second fails its
 * OWN Hold update on the version check, and Spring rolls back that entire
 * transaction — including the resource release it just performed. No
 * extra locking needed; Hold's existing @Version is the safety net here,
 * the same principle as the confirm-vs-reaper re-check reasoning.
 */
@Service
@RequiredArgsConstructor
public class HoldCancelService {

    private final HoldRepository holdRepository;
    private final ResourcePoolRepository poolRepository;

    @Transactional
    public HoldResponse cancel(String holdId) {
        Hold hold = holdRepository.findByIdWithLines(holdId)
                .orElseThrow(() -> new ResourceNotFoundException("Hold not found: " + holdId));

        if (hold.getStatus() != HoldStatus.ACTIVE) {
            throw new ResourceConflictException(
                    "Hold is not ACTIVE, cannot cancel: " + hold.getId()
                            + " (status=" + hold.getStatus() + ")");
        }
        // Deliberately no isExpired() check here, unlike HoldExpiryService —
        // cancelling an already-expired-but-not-yet-reaped Hold is fine and
        // arguably preferable (frees resources sooner). Only a non-ACTIVE
        // status blocks cancellation.

        for (HoldLine line : hold.getHoldLines()) {
            if (line.getResourceUnit() != null) {
                releaseUnitLine(line);
            } else {
                releaseCounterLine(line);
            }
        }

        hold.setStatus(HoldStatus.CANCELLED);

        return toResponse(hold);
    }

    private void releaseUnitLine(HoldLine line) {
        ResourceUnit unit = line.getResourceUnit();
        if (unit.getStatus() == ResourceUnitStatus.HELD) {
            unit.setStatus(ResourceUnitStatus.AVAILABLE);
        }
    }

    private void releaseCounterLine(HoldLine line) {
        String poolId = line.getResourcePool().getId(); // proxy id access only, no query
        ResourcePool lockedPool = poolRepository.findByIdForUpdate(poolId)
                .orElseThrow(() -> new ResourceNotFoundException("ResourcePool not found: " + poolId));
        lockedPool.setRemainingCapacity(lockedPool.getRemainingCapacity() + line.getQuantity());
    }

    private HoldResponse toResponse(Hold hold) {
        var lines = hold.getHoldLines().stream()
                .map(line -> new HoldLineResponse(
                        line.getResourcePool().getId(),
                        line.getResourceUnit() != null ? line.getResourceUnit().getId() : null,
                        line.getQuantity()
                ))
                .toList();
        return new HoldResponse(hold.getId(), hold.getStatus().name(), hold.getExpiresAt(), lines);
    }
}