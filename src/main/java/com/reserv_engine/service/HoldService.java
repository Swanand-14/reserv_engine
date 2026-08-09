package com.reserv_engine.service;

import com.reserv_engine.core.domain.PoolMode;
import com.reserv_engine.dto.CreateHoldRequest;
import com.reserv_engine.dto.HoldLineRequest;
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
import com.reserv_engine.repository.ResourceUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class HoldService {
    private static final Duration HOLD_TTL = Duration.ofMinutes(5);

    private final HoldRepository holdRepository;
    private final ResourcePoolRepository poolRepository;
    private final ResourceUnitRepository unitRepository;
    @Transactional
    public HoldResponse createHold(CreateHoldRequest request) {
        var existing = holdRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        if (request.lines() == null || request.lines().isEmpty()) {
            throw new IllegalArgumentException("A hold must contain at least one line");
        }

        Hold hold = new Hold(request.holderId(), HOLD_TTL, request.idempotencyKey());

        for (HoldLineRequest lineRequest : request.lines()) {
            hold.addHoldLine(buildLine(hold, lineRequest));
        }

        Hold saved = holdRepository.save(hold); // cascades to hold_line via CascadeType.PERSIST
        return toResponse(saved);
    }

    private HoldLine buildLine(Hold hold,HoldLineRequest lineRequest){
        PoolMode mode = poolRepository.findPoolModeById(lineRequest.resourcePoolId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ResourcePool not found: " + lineRequest.resourcePoolId()));

        if (mode == PoolMode.UNIT_BASED) {
            ResourcePool pool = poolRepository.findById(lineRequest.resourcePoolId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "ResourcePool not found: " + lineRequest.resourcePoolId()));
            return buildUnitLine(hold, pool, lineRequest);
        }
        ResourcePool lockedPool = poolRepository.findByIdForUpdate(lineRequest.resourcePoolId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ResourcePool not found: " + lineRequest.resourcePoolId()));
        return buildCounterLine(hold, lockedPool, lineRequest);
    }

    private HoldLine buildUnitLine(Hold hold, ResourcePool pool, HoldLineRequest lineRequest) {
        if (lineRequest.resourceUnitId() == null) {
            throw new IllegalArgumentException(
                    "resourceUnitId is required for UNIT_BASED pool " + pool.getId());
        }
        ResourceUnit unit = unitRepository.findById(lineRequest.resourceUnitId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ResourceUnit not found: " + lineRequest.resourceUnitId()));

        if (unit.getStatus() != com.reservengine.core.domain.ResourceUnitStatus.AVAILABLE) {
            throw new ResourceConflictException("ResourceUnit is not available: " + unit.getId());
        }

        unit.setStatus(com.reservengine.core.domain.ResourceUnitStatus.HELD);
        //below step actually updates the database ,. Flushing here establishes a consistent
        //        // "exclusive lock first, insert second" order across every
        //        // transaction, without adding any explicit pessimistic locking —
        //        // @Version is still what actually resolves genuine conflicts.
        unitRepository.saveAndFlush(unit);
        return new HoldLine(hold, unit);
    }
    private HoldLine buildCounterLine(Hold hold, ResourcePool pool, HoldLineRequest lineRequest) {
        int quantity = lineRequest.quantity() == null ? 0 : lineRequest.quantity();
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "A positive quantity is required for COUNTER_BASED pool " + pool.getId());
        }
        if (pool.getRemainingCapacity() < quantity) {
            throw new ResourceConflictException("Insufficient capacity in pool: " + pool.getId());
        }

        pool.setRemainingCapacity(pool.getRemainingCapacity() - quantity);
        return new HoldLine(hold, pool, quantity);
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
