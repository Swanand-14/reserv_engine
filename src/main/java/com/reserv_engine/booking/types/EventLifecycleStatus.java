package com.reserv_engine.booking.types;

/**
 * [Booking] Event visibility/lifecycle state. Transitions enforced in the
 * service layer, not here — mirrors Engine's HoldStatus/PoolMode pattern
 * of "enum here, rules elsewhere".
 */
public enum EventLifecycleStatus {
    DRAFT,      // being set up by Organizer, not visible to customers
    PUBLISHED,  // visible and bookable
    CANCELLED,  // Organizer cancelled; showtimes should not accept new holds
    COMPLETED   // all showtimes have occurred
}