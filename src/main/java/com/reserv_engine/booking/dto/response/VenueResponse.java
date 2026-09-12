package com.reserv_engine.booking.dto.response;

import com.reserv_engine.booking.entity.Venue;
import com.reserv_engine.booking.repository.VenueSummaryRow;

import java.time.LocalDateTime;

public record VenueResponse(String id, String name, LocalDateTime createdAt, long hallCount, long totalSeatCount) {

    public static VenueResponse from(VenueSummaryRow row) {
        return new VenueResponse(row.getId(), row.getName(), row.getCreatedAt(), row.getHallCount(), row.getTotalSeatCount());
    }

    // Used only right after creation, where hallCount/totalSeatCount are
    // trivially zero — a brand new Venue can't have halls or seats yet.
    public static VenueResponse from(Venue venue) {
        return new VenueResponse(venue.getId(), venue.getName(), venue.getCreatedAt(), 0, 0);
    }
}