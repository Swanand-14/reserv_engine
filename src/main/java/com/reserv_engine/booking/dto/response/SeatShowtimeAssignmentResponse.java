package com.reserv_engine.booking.dto.response;

import com.reserv_engine.booking.entity.SeatShowtimeAssignment;

import java.time.LocalDateTime;

public record SeatShowtimeAssignmentResponse(String id, String seatId, String showtimeId,
                                             String resourceUnitId, LocalDateTime createdAt) {
    public static SeatShowtimeAssignmentResponse from(SeatShowtimeAssignment a) {
        return new SeatShowtimeAssignmentResponse(a.getId(), a.getSeat().getId(), a.getShowtime().getId(),
                a.getResourceUnit().getId(), a.getCreatedAt());
    }
}