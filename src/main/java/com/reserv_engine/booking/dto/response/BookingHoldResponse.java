package com.reserv_engine.booking.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * [Booking] Customer-facing wrapper around the Engine's HoldResponse.
 * Exists because HoldLineResponse (Engine) only carries
 * resourcePoolId/resourceUnitId/quantity — meaningless to a customer, and
 * missing the one thing they'd actually need if we returned it directly:
 * holdLineId, required by ConfirmReservationRequest.linePrices. This
 * response gives the frontend everything the next two steps (payment,
 * confirm) need without another round trip: holdLineId per seat, plus
 * what to render (seat label, tier, price, total).
 */
public record BookingHoldResponse(String holdId, String status, LocalDateTime expiresAt,
                                  List<Line> lines, BigDecimal totalPrice) {

    public record Line(String holdLineId, String seatLabel, String tierName, BigDecimal price) {}
}