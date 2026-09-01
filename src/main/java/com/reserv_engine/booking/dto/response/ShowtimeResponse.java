package com.reserv_engine.booking.dto.response;

import com.reserv_engine.booking.entity.Showtime;

import java.time.LocalDateTime;

public record ShowtimeResponse(String id, String eventId, String hallId, String availabilityWindowId,
                               LocalDateTime startTime, LocalDateTime endTime, LocalDateTime createdAt) {
    public static ShowtimeResponse from(Showtime showtime) {
        return new ShowtimeResponse(showtime.getId(), showtime.getEvent().getId(), showtime.getHall().getId(),
                showtime.getAvailabilityWindow().getId(), showtime.getStartTime(), showtime.getEndTime(),
                showtime.getCreatedAt());
    }
}