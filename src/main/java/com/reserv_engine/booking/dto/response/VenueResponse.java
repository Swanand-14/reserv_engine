package com.reserv_engine.booking.dto.response;

import com.reserv_engine.booking.entity.Venue;

import java.time.LocalDateTime;

public record VenueResponse(String id, String managerId, String name, LocalDateTime createdAt) {
    public static VenueResponse from(Venue venue) {
        return new VenueResponse(venue.getId(), venue.getManager().getId(), venue.getName(), venue.getCreatedAt());
    }
}