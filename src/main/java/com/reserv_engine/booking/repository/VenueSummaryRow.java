
package com.reserv_engine.booking.repository;

import java.time.LocalDateTime;

/**
 * [Booking] Flat, join-driven venue summary row for the organizer's venue
 * list — hallCount/totalSeatCount are computed via correlated subqueries
 * (see VenueRepository), not JOINs, to avoid row multiplication. No entity
 * references, matching SeatMapRow's convention.
 */
public interface VenueSummaryRow {
    String getId();
    String getName();
    LocalDateTime getCreatedAt();
    long getHallCount();
    long getTotalSeatCount();
}