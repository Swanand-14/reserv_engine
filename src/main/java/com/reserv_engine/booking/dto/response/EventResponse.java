package com.reserv_engine.booking.dto.response;

import com.reserv_engine.booking.entity.Event;
import com.reserv_engine.booking.types.EventLifecycleStatus;

import java.time.LocalDateTime;

public record EventResponse(String id, String organizerId, String title,
                            EventLifecycleStatus lifecycleStatus, LocalDateTime createdAt) {
    public static EventResponse from(Event event) {
        return new EventResponse(event.getId(), event.getOrganizer().getId(), event.getTitle(),
                event.getLifecycleStatus(), event.getCreatedAt());
    }
}