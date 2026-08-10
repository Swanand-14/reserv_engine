package com.reserv_engine.repository;

import com.reserv_engine.core.domain.ResourceUnitStatus;
import com.reserv_engine.entity.ResourceUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResourceUnitRepository extends JpaRepository<ResourceUnit, String> {

    long countByResourcePool_IdAndStatus(String resourcePoolId, ResourceUnitStatus status);

    List<ResourceUnit> findByResourcePool_Id(String resourcePoolId);

    // Currently unused (browsePoolsForWindow was simplified to skip this),
    // kept for when the batched-count optimization comes back. Status is
    // now a bind parameter (:status) instead of a hardcoded fully-qualified
    // enum literal in the JPQL string — that hardcoded form is exactly what
    // just broke: it silently duplicates the package path as a string, with
    // nothing to catch it if the path is ever wrong, unlike a real Java
    // reference which the compiler checks.
    @Query("""
            SELECT ru.resourcePool.id AS poolId, COUNT(ru) AS availableCount
            FROM ResourceUnit ru
            WHERE ru.resourcePool.id IN :poolIds AND ru.status = :status
            GROUP BY ru.resourcePool.id
            """)
    List<PoolAvailableCount> countAvailableByPoolIds(
            @Param("poolIds") List<String> poolIds,
            @Param("status") ResourceUnitStatus status);
}