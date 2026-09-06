package com.reserv_engine.booking.repository;

import com.reserv_engine.booking.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, String> {
    List<Seat> findByHallId(String hallId);
    boolean existsByHallIdAndLabel(String hallId, String label);
    @Query("""
            SELECT s.id AS seatId, s.label AS label,
                   tt.name AS tierName, tt.price AS price, ru.status AS status
            FROM Seat s
            LEFT JOIN SeatShowtimeAssignment ssa
                ON ssa.seat = s AND ssa.showtime.id = :showtimeId
            LEFT JOIN ssa.resourceUnit ru
            LEFT JOIN ru.resourcePool rp
            LEFT JOIN TicketTier tt ON tt.resourcePool = rp
            WHERE s.hall.id = :hallId
            ORDER BY s.label
            """)
    List<SeatMapRow> findSeatMapByHallIdAndShowtimeId(
            @Param("hallId") String hallId, @Param("showtimeId") String showtimeId);



}