package com.reserv_engine.booking.dto.response;

import com.reserv_engine.booking.repository.ShowtimeBrowseRow;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowtimeBrowseResponse(String id, LocalDateTime startTime, LocalDateTime endTime,
                                     String hallName, String venueName, BigDecimal startingPrice) {
    public static ShowtimeBrowseResponse from(ShowtimeBrowseRow row) {
        return new ShowtimeBrowseResponse(row.getId(), row.getStartTime(), row.getEndTime(),
                row.getHallName(), row.getVenueName(), row.getStartingPrice());
    }
}