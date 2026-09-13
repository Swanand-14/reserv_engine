package com.reserv_engine.booking.repository;

import com.reserv_engine.core.domain.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * [Booking] Flat, join-driven "my reservations" row — one row per booked
 * seat. Deliberately bypasses ReservationService.findMyReservations()
 * (Engine) for this read: that query only knows resourcePoolId/
 * resourceUnitId, which means nothing on a customer-facing screen. This
 * lives here (Booking), not in the Engine's ReservationRepository,
 * because it joins into SeatShowtimeAssignment/Event/TicketTier — the
 * Engine must never depend on Booking. The service groups these rows by
 * reservationId into one card per reservation (a multi-seat reservation
 * produces multiple rows here). No entity references, matching
 * SeatMapRow's convention.
 */
public interface MyReservationRow {
    String getReservationId();
    ReservationStatus getReservationStatus();
    LocalDateTime getConfirmedAt();
    String getEventTitle();
    String getShowtimeId();
    LocalDateTime getStartTime();
    LocalDateTime getEndTime();
    String getSeatLabel();
    String getTierName();
    BigDecimal getLockedPrice();
}