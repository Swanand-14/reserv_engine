package com.reserv_engine.booking.repository;

import com.reserv_engine.booking.entity.SeatShowtimeAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeatShowtimeAssignmentRepository extends JpaRepository<SeatShowtimeAssignment, String> {

    List<SeatShowtimeAssignment> findByShowtimeId(String showtimeId);

    Optional<SeatShowtimeAssignment> findBySeatIdAndShowtimeId(String seatId, String showtimeId);

    Optional<SeatShowtimeAssignment> findByResourceUnitId(String resourceUnitId);
}