package com.reserv_engine.booking.dto.response;

import com.reserv_engine.booking.entity.Hall;

import java.time.LocalDateTime;

public record HallResponse(String id, String venueId, String name, LocalDateTime createdAt) {
    public static HallResponse from(Hall hall) {
        return new HallResponse(hall.getId(), hall.getVenue().getId(), hall.getName(), hall.getCreatedAt());
    }
}