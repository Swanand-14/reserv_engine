package com.reserv_engine.core.domain;

/**
 * Lifecycle state of a Reservation — the confirmed outcome of a Hold after
 * successful payment.
 *
 * CONFIRMED -> active, resources RESERVED
 * CANCELLED -> terminal, resources released (future work, not this step)
 * COMPLETED -> terminal, the reserved thing actually happened/was consumed
 */
public enum ReservationStatus {
    CONFIRMED,
    CANCELLED,
    COMPLETED
}