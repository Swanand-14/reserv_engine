package com.reserv_engine.booking.repository;

import com.reserv_engine.booking.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VenueRepository extends JpaRepository<Venue, String> {
    List<Venue> findByManagerId(String managerId);
    @Query("""
            SELECT v.id AS id, v.name AS name, v.createdAt AS createdAt,
                   (SELECT COUNT(h) FROM Hall h WHERE h.venue = v) AS hallCount,
                   (SELECT COUNT(s) FROM Seat s WHERE s.hall.venue = v) AS totalSeatCount
            FROM Venue v
            WHERE v.manager.id = :managerId
            ORDER BY v.createdAt DESC
            """)
    List<VenueSummaryRow> findSummaryByManagerId(@Param("managerId") String managerId);
}