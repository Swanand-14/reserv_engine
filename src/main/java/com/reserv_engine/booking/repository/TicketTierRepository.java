package com.reserv_engine.booking.repository;

import com.reserv_engine.booking.entity.TicketTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TicketTierRepository extends JpaRepository<TicketTier, String> {
    List<TicketTier> findByShowtimeId(String showtimeId);
    Optional<TicketTier> findByResourcePoolId(String resourcePoolId);
}