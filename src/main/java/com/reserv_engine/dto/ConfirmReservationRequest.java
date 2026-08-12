package com.reserv_engine.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * [Engine] Request to confirm a Hold into a Reservation.
 *
 * linePrices exists because the Engine deliberately does not own pricing
 * (see TicketTier.price in the App layer) — the caller (App layer /
 * client) supplies the price to lock in per HoldLine, keyed by that
 * HoldLine's id. This is the one point where a domain-specific concept
 * (price) has to cross into the Engine's request shape, since
 * ReservationLine.lockedPrice must be set at creation and never change.
 */
public record ConfirmReservationRequest(
        String holdId,
        List<LinePriceRequest> linePrices
) {
    public record LinePriceRequest(
            String holdLineId,
            BigDecimal price
    ) {
    }
}