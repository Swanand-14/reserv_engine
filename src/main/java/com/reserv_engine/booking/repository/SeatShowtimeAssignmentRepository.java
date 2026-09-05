package com.reserv_engine.booking.repository;

import com.reserv_engine.booking.entity.SeatShowtimeAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeatShowtimeAssignmentRepository extends JpaRepository<SeatShowtimeAssignment, String> {

    List<SeatShowtimeAssignment> findByShowtimeId(String showtimeId);

    Optional<SeatShowtimeAssignment> findBySeatIdAndShowtimeId(String seatId, String showtimeId);

    Optional<SeatShowtimeAssignment> findByResourceUnitId(String resourceUnitId);
    @Query("""
            SELECT ssa FROM SeatShowtimeAssignment ssa
            JOIN FETCH ssa.resourceUnit ru
            JOIN FETCH ru.resourcePool
            WHERE ssa.seat.id = :seatId AND ssa.showtime.id = :showtimeId
            """)
    Optional<SeatShowtimeAssignment> findBySeatIdAndShowtimeIdWithResourceUnitAndPool(
            @Param("seatId") String seatId, @Param("showtimeId") String showtimeId);
}