package com.reserv_engine.service;

import com.reserv_engine.core.domain.HoldStatus;
import com.reserv_engine.core.domain.ResourceUnitStatus;
import com.reserv_engine.entity.Hold;
import com.reserv_engine.entity.HoldLine;
import com.reserv_engine.entity.ResourcePool;
import com.reserv_engine.entity.ResourceUnit;
import com.reserv_engine.exception.ResourceNotFoundException;
import com.reserv_engine.repository.HoldRepository;
import com.reserv_engine.repository.ResourcePoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HoldExpiryService {
    private final HoldRepository holdRepository;
    private final ResourcePoolRepository poolRepository;
    @Transactional
    public void releaseHold(String holdId) {
        Hold hold = holdRepository.findByIdWithLines(holdId)
                .orElseThrow(() -> new ResourceNotFoundException("Hold not found: " + holdId));

        if (hold.getStatus() != HoldStatus.ACTIVE || !hold.isExpired()) {
            return; // already confirmed/cancelled, or no longer actually expired — not ours to touch
        }

        for (HoldLine line : hold.getHoldLines()) {
            if (line.getResourceUnit() != null) {
                releaseUnitLine(line);
            } else {
                releaseCounterLine(line);
            }
        }

        hold.setStatus(HoldStatus.EXPIRED);
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



}
