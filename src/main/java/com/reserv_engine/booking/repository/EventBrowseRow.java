package com.reserv_engine.booking.repository;

import java.math.BigDecimal;

/**
 * [Booking] Flat, join-driven event browse row. startingPrice is null when
 * the event has showtimes but no TicketTiers assigned yet on any of them
 * (shouldn't normally reach PUBLISHED in that state, but not assumed here)
 * — the response DTO/frontend decide how to render that case, this row
 * just reports what's true. No entity references, matching SeatMapRow's
 * convention.
 */
public interface EventBrowseRow {
    String getId();
    String getTitle();
    long getShowtimeCount();
    BigDecimal getStartingPrice();
}