package com.reserv_engine.booking.dto.response;

import com.reserv_engine.booking.repository.OrganizerEventRow;
import com.reserv_engine.booking.types.EventLifecycleStatus;

import java.time.LocalDateTime;

/** [Booking] Organizer's own event list — includes lifecycleStatus (DRAFT/PUBLISHED), unlike EventBrowseResponse. */
public record OrganizerEventResponse(String id, String title, EventLifecycleStatus lifecycleStatus,
                                     long showtimeCount, LocalDateTime createdAt) {
    public static OrganizerEventResponse from(OrganizerEventRow row) {
        return new OrganizerEventResponse(row.getId(), row.getTitle(), row.getLifecycleStatus(),
                row.getShowtimeCount(), row.getCreatedAt());
    }
}