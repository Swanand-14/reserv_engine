package com.reserv_engine.service;

import com.reserv_engine.core.domain.PoolMode;
import com.reserv_engine.dto.CreateHoldRequest;
import com.reserv_engine.dto.HoldLineRequest;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.reserv_engine.core.domain.ResourceUnitStatus;
import java.time.Duration;

/**
 * [Engine] The actual transactional attempt to create a Hold — deliberately
 * a SEPARATE bean from HoldService, not just a private method there, AND
 * deliberately using Propagation.REQUIRES_NEW.
 *
 * Two distinct reasons, both necessary:
 *  1. Separate bean: Spring's @Transactional works via a proxy wrapping
 *     this bean. If HoldService called a @Transactional method on ITSELF
 *     (self-invocation), that call would bypass the proxy entirely and
 *     @Transactional would be silently ignored.
 *  2. REQUIRES_NEW: even calling through the proxy, DEFAULT propagation
 *     would make this method JOIN HoldService's already-open transaction
 *     rather than start its own. If it failed under REQUIRED propagation,
 *     the shared transaction would be marked rollback-only, and
 *     HoldService's subsequent fallback read (after catching the
 *     exception) would fail too — you can't keep using a transaction
 *     that's already been marked for rollback. REQUIRES_NEW guarantees a
 *     genuinely independent transaction: it suspends the caller's
 *     transaction, runs this one to completion (commit OR rollback) on
 *     its own, then resumes the caller's — so a failure here is fully
 *     isolated and HoldService's transaction is completely unaffected.
 *
 * UNIT_BASED lines rely on ResourceUnit's @Version column for actual
 * conflict resolution. buildUnitLine() explicitly flushes the unit's
 * status UPDATE before the HoldLine INSERT — without that, Hibernate's
 * default insert-before-update flush ordering meant every transaction
 * took a shared FK-check lock on the unit before trying to upgrade to
 * exclusive, causing an InnoDB deadlock under concurrency. Flushing early
 * fixes the lock ORDER; @Version still resolves the actual conflicts.
 *
 * COUNTER_BASED lines use explicit pessimistic locking (see buildLine and
 * ResourcePoolRepository.findByIdForUpdate) — a SELECT ... FOR UPDATE
 * takes the pool's exclusive lock immediately, before any child row
 * referencing it is inserted, for the same deadlock-prevention reason.
 */
@Service
@RequiredArgsConstructor
class HoldWriteService {

    private static final Duration HOLD_TTL = Duration.ofMinutes(5);

    private final HoldRepository holdRepository;
    private final ResourcePoolRepository poolRepository;
    private final ResourceUnitRepository unitRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Hold attemptCreate(CreateHoldRequest request) {
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new IllegalArgumentException("A hold must contain at least one line");
        }

        Hold hold = new Hold(request.holderId(), HOLD_TTL, request.idempotencyKey());

        for (HoldLineRequest lineRequest : request.lines()) {
            hold.addHoldLine(buildLine(hold, lineRequest));
        }

        // If another concurrent request with the same idempotencyKey wins the
        // race to commit first, THIS insert fails on the unique constraint —
        // DataIntegrityViolationException propagates out of this method,
        // through the real proxy boundary, and Spring rolls back everything
        // this transaction did (unit status flips, pool decrements, all of
        // it) automatically. HoldService is what catches it and recovers.
        return holdRepository.save(hold); // cascades to hold_line via CascadeType.PERSIST
    }

    private HoldLine buildLine(Hold hold, HoldLineRequest lineRequest) {
        // Projection only — deliberately does NOT load a managed ResourcePool
        // entity. If it did, the later findByIdForUpdate() call for the same
        // id would hit Hibernate's identity map and return that same
        // already-cached (and by-then-stale) object instead of a freshly
        // locked read. See ResourcePoolRepository for the full explanation.
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

        if (unit.getStatus() != ResourceUnitStatus.AVAILABLE) {
            throw new ResourceConflictException("ResourceUnit is not available: " + unit.getId());
        }

        unit.setStatus(ResourceUnitStatus.HELD);
        unitRepository.saveAndFlush(unit); // see class javadoc: fixes lock order, prevents deadlock

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
}