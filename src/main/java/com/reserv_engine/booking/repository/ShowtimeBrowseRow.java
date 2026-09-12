package com.reserv_engine.booking.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * [Booking] Flat, join-driven showtime browse row — hallName/venueName so
 * the frontend never needs a second round trip just to render "Hall 1,
 * PVR Koregaon Park". startingPrice is per-showtime (min across that
 * showtime's own TicketTiers), distinct from EventBrowseRow's event-wide
 * starting price. No entity references, matching SeatMapRow's convention.
 */
public interface ShowtimeBrowseRow {
    String getId();
    LocalDateTime getStartTime();
    LocalDateTime getEndTime();
    String getHallName();
    String getVenueName();
    BigDecimal getStartingPrice();
}