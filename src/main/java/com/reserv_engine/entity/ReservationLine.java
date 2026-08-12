package com.reserv_engine.entity;

import com.reserv_engine.core.domain.PoolMode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * [Engine] Captures which unit/quantity was ultimately reserved, and the
 * price locked in at confirmation. Mirrors HoldLine's unit-XOR-quantity
 * shape on purpose — a ReservationLine is built directly from its
 * originating HoldLine, one-to-one.
 *
 * Invariants (enforced here + service layer):
 *  - lockedPrice is set once at construction (no setter exposed beyond
 *    Lombok's, but service layer must never call it again after creation)
 *    and must never change afterward, regardless of later pool/tier price
 *    changes.
 *  - Must correspond one-to-one with a HoldLine from the originating Hold
 *    (enforced in service layer — one ReservationLine built per HoldLine).
 *  - A referenced ResourceUnit must hold status = RESERVED for as long as
 *    this line's Reservation remains CONFIRMED (enforced in service layer).
 */
@Entity
@Table(name = "reservation_line")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationLine {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false, updatable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_pool_id", nullable = false, updatable = false)
    private ResourcePool resourcePool;

    // Null for COUNTER_BASED lines — same reasoning as HoldLine.resourceUnit.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_unit_id", updatable = false)
    private ResourceUnit resourceUnit;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Column(name = "locked_price", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal lockedPrice;

    /** Unit-based line: the specific unit that was reserved. */
    public ReservationLine(Reservation reservation, ResourceUnit resourceUnit, BigDecimal lockedPrice) {
        if (resourceUnit.getResourcePool().getPoolMode() != PoolMode.UNIT_BASED) {
            throw new IllegalArgumentException("ResourceUnit lines require a UNIT_BASED pool");
        }
        this.id = UUID.randomUUID().toString();
        this.reservation = reservation;
        this.resourceUnit = resourceUnit;
        this.resourcePool = resourceUnit.getResourcePool();
        this.quantity = 1;
        this.lockedPrice = lockedPrice;
    }

    /** Counter-based line: the quantity that was reserved against a pool. */
    public ReservationLine(Reservation reservation, ResourcePool resourcePool, int quantity, BigDecimal lockedPrice) {
        if (resourcePool.getPoolMode() != PoolMode.COUNTER_BASED) {
            throw new IllegalArgumentException("Quantity lines require a COUNTER_BASED pool");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.id = UUID.randomUUID().toString();
        this.reservation = reservation;
        this.resourcePool = resourcePool;
        this.resourceUnit = null;
        this.quantity = quantity;
        this.lockedPrice = lockedPrice;
    }
}