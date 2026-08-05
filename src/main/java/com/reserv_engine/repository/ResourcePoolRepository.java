package com.reserv_engine.repository;

import com.reserv_engine.entity.ResourcePool;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ResourcePoolRepository extends JpaRepository<ResourcePool, String> {


    List<ResourcePool> findByAvailabilityWindow_Id(String availabilityWindowId);


    @EntityGraph(attributePaths = "availabilityWindow")
    List<ResourcePool> findWithWindowByAvailabilityWindow_Id(String availabilityWindowId);


    @Query("SELECT p FROM ResourcePool p JOIN FETCH p.availabilityWindow WHERE p.availabilityWindow.id = :windowId")
    List<ResourcePool> findWithWindowJoinFetch(@Param("windowId") String availabilityWindowId);

    // NOTE: when pessimistic locking is introduced for COUNTER_BASED pools
    // (step 6 of the build plan), add a @Lock(PESSIMISTIC_WRITE) query here,

}