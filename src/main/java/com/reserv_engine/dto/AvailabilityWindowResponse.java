package com.reserv_engine.dto;

import java.time.LocalDateTime;

public record AvailabilityWindowResponse(
        String id,
        String ownerId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createdAt
) {
    public static AvailabilityWindowResponse from(com.reserv_engine.entity.AvailabilityWindow window) {
        return new AvailabilityWindowResponse(
                window.getId(), window.getOwnerId(), window.getStartTime(), window.getEndTime(), window.getCreatedAt()
        );
    }
}