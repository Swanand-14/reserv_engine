package com.reserv_engine.booking.dto.response;

import com.reserv_engine.booking.repository.SeatMapRow;

import java.math.BigDecimal;

public record SeatMapEntryResponse(String seatId, String label, String tierName, BigDecimal price, String status) {
    public static SeatMapEntryResponse from(SeatMapRow row) {
        return new SeatMapEntryResponse(row.getSeatId(), row.getLabel(), row.getTierName(),
                row.getPrice(), row.getStatus() != null ? row.getStatus().name() : null);
    }
}