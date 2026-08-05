package com.reserv_engine.repository;

import com.reserv_engine.entity.ResourceUnit;
import com.reservengine.core.domain.ResourceUnitStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceUnitRepository extends JpaRepository<ResourceUnit, String> {

    // Used by the browse endpoint to show remaining capacity for a UNIT_BASED pool.
    long countByResourcePool_IdAndStatus(String resourcePoolId, ResourceUnitStatus status);

    List<ResourceUnit> findByResourcePool_Id(String resourcePoolId);

    // NOTE: when optimistic locking is introduced for UNIT_BASED pools
    // (step 6 of the build plan), hold creation will load specific units by id,
    // rely on @Version, and catch OptimisticLockException on save/flush.
    // Not needed yet — this step is read-only browsing.
}