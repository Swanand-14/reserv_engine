package com.reserv_engine.booking.dto.response;

import com.reserv_engine.core.domain.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** [Booking] One card per reservation, seats grouped underneath — not one row per seat. */
public record MyReservationResponse(String reservationId, ReservationStatus status, LocalDateTime confirmedAt,
                                    String eventTitle, String showtimeId, LocalDateTime startTime, LocalDateTime endTime,
                                    List<SeatLine> seats, BigDecimal totalPaid) {

    public record SeatLine(String seatLabel, String tierName, BigDecimal price) {}
}