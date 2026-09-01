package com.reserv_engine.booking.dto.response;

import com.reserv_engine.booking.entity.Seat;

import java.time.LocalDateTime;

public record SeatResponse(String id, String hallId, String label, LocalDateTime createdAt) {
    public static SeatResponse from(Seat seat) {
        return new SeatResponse(seat.getId(), seat.getHall().getId(), seat.getLabel(), seat.getCreatedAt());
    }
}