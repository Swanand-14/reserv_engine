package com.reserv_engine.repository;

import com.reserv_engine.core.domain.PoolMode;
import com.reserv_engine.entity.ResourcePool;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResourcePoolRepository extends JpaRepository<ResourcePool, String> {


    List<ResourcePool> findByAvailabilityWindow_Id(String availabilityWindowId);


    @EntityGraph(attributePaths = "availabilityWindow")
    List<ResourcePool> findWithWindowByAvailabilityWindow_Id(String availabilityWindowId);


    @Query("SELECT p FROM ResourcePool p JOIN FETCH p.availabilityWindow WHERE p.availabilityWindow.id = :windowId")
    List<ResourcePool> findWithWindowJoinFetch(@Param("windowId") String availabilityWindowId);
    @Query("SELECT p.poolMode FROM ResourcePool p WHERE p.id = :id")
    Optional<PoolMode> findPoolModeById(@Param("id") String id);

    // NOTE: when pessimistic locking is introduced for COUNTER_BASED pools
    // (step 6 of the build plan), add a @Lock(PESSIMISTIC_WRITE) query here,
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ResourcePool p WHERE p.id = :id")
    Optional<ResourcePool> findByIdForUpdate(@Param("id") String id);

}