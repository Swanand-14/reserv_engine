package com.reserv_engine.booking.repository;

import com.reserv_engine.booking.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, String> {
    List<Seat> findByHallId(String hallId);
    boolean existsByHallIdAndLabel(String hallId, String label);
}