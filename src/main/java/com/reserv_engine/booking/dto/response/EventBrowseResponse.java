package com.reserv_engine.booking.dto.response;

import com.reserv_engine.booking.repository.EventBrowseRow;

import java.math.BigDecimal;

/** [Booking] Read-only browse projection — deliberately no relation traversal. */
public record EventBrowseResponse(String id, String title, long showtimeCount, BigDecimal startingPrice) {
    public static EventBrowseResponse from(EventBrowseRow row) {
        return new EventBrowseResponse(row.getId(), row.getTitle(), row.getShowtimeCount(), row.getStartingPrice());
    }
}