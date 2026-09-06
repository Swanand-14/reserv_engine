package com.reserv_engine.booking.repository;

import com.reserv_engine.core.domain.ResourceUnitStatus;

import java.math.BigDecimal;

/**
 * [Booking] Flat, join-driven seat-map row. status is null when the seat
 * has no SeatShowtimeAssignment for this Showtime yet (not bookable —
 * Organizer hasn't finished assigning seats to tiers). No entity
 * references, matching the Engine's own PoolAvailableCount/
 * AvailabilityWindowDto convention.
 */
public interface SeatMapRow {
    String getSeatId();
    String getLabel();
    String getTierName();
    BigDecimal getPrice();
    ResourceUnitStatus getStatus();
}