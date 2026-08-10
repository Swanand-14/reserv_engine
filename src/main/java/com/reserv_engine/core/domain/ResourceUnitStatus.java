package com.reserv_engine.core.domain;

/**
 * Lifecycle state of an individual ResourceUnit (unit-based pools only).
 *
 * AVAILABLE -> free to be held
 * HELD      -> referenced by exactly one ACTIVE Hold's HoldLine
 * RESERVED  -> referenced by exactly one CONFIRMED Reservation's ReservationLine
 */
public enum ResourceUnitStatus {
    AVAILABLE,
    HELD,
    RESERVED
}