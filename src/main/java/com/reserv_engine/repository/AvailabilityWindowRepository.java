package com.reserv_engine.repository;

import com.reserv_engine.entity.AvailabilityWindow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface AvailabilityWindowRepository extends JpaRepository<AvailabilityWindow, String> {
    Page<AvailabilityWindow> findByStartTimeAfterOrderByStartTimeAsc(LocalDateTime now, Pageable pageable);
}
