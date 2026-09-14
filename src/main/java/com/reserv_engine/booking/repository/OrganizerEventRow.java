package com.reserv_engine.booking.repository;

import com.reserv_engine.booking.types.EventLifecycleStatus;

import java.time.LocalDateTime;

/**
 * [Booking] Flat, join-driven row for an organizer's own event list —
 * distinct from EventBrowseRow because this exposes lifecycleStatus
 * (DRAFT/PUBLISHED), which a public customer-facing browse card never
 * should. No entity references, matching SeatMapRow's convention.
 */
public interface OrganizerEventRow {
    String getId();
    String getTitle();
    EventLifecycleStatus getLifecycleStatus();
    long getShowtimeCount();
    LocalDateTime getCreatedAt();
}