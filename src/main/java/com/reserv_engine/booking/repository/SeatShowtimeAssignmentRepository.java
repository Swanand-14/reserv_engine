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
            JOIN FETCH ssa.seat
            WHERE ssa.seat.id = :seatId AND ssa.showtime.id = :showtimeId
            """)
    Optional<SeatShowtimeAssignment> findBySeatIdAndShowtimeIdWithResourceUnitAndPool(
            @Param("seatId") String seatId, @Param("showtimeId") String showtimeId);

    @Query("""
            SELECT r.id AS reservationId, r.status AS reservationStatus, r.confirmedAt AS confirmedAt,
                   ev.title AS eventTitle, st.id AS showtimeId, st.startTime AS startTime, st.endTime AS endTime,
                   s.label AS seatLabel, tt.name AS tierName, rl.lockedPrice AS lockedPrice
            FROM SeatShowtimeAssignment ssa
            JOIN ssa.seat s
            JOIN ssa.showtime st
            JOIN st.event ev
            JOIN ReservationLine rl ON rl.resourceUnit = ssa.resourceUnit
            JOIN rl.reservation r
            JOIN TicketTier tt ON tt.resourcePool = rl.resourcePool
            WHERE r.holderId = :holderId
            ORDER BY r.confirmedAt DESC
            """)
    List<MyReservationRow> findMyReservationRows(@Param("holderId") String holderId);



}