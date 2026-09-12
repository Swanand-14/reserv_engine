package com.reserv_engine.booking.repository;

import com.reserv_engine.booking.entity.Event;
import com.reserv_engine.booking.types.EventLifecycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, String> {
    List<Event> findByOrganizerId(String organizerId);
    List<Event> findByLifecycleStatus(EventLifecycleStatus status);

    // Subquery counts/aggregates (not JOINs) — same reasoning as
    // VenueRepository.findSummaryByManagerId: joining Showtime and
    // TicketTier directly would multiply rows per event.
    @Query("""
            SELECT e.id AS id, e.title AS title,
                   (SELECT COUNT(st) FROM Showtime st WHERE st.event = e) AS showtimeCount,
                   (SELECT MIN(tt.price) FROM TicketTier tt WHERE tt.showtime.event = e) AS startingPrice
            FROM Event e
            WHERE e.lifecycleStatus = :status
            ORDER BY e.createdAt DESC
            """)
    List<EventBrowseRow> findBrowseRowsByLifecycleStatus(@Param("status") EventLifecycleStatus status);

    // Scoped to PUBLISHED on purpose: a customer hitting this by id
    // (deep link/refresh) must not be able to see another organizer's
    // DRAFT event just by guessing/copying its id.
    @Query("""
            SELECT e.id AS id, e.title AS title,
                   (SELECT COUNT(st) FROM Showtime st WHERE st.event = e) AS showtimeCount,
                   (SELECT MIN(tt.price) FROM TicketTier tt WHERE tt.showtime.event = e) AS startingPrice
            FROM Event e
            WHERE e.id = :eventId AND e.lifecycleStatus = :status
            """)
    Optional<EventBrowseRow> findBrowseRowByIdAndLifecycleStatus(
            @Param("eventId") String eventId, @Param("status") EventLifecycleStatus status);
}