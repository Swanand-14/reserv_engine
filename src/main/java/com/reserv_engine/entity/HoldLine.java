package com.reserv_engine.entity;

import com.reserv_engine.core.domain.PoolMode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "hold_line")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HoldLine {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hold_id", nullable = false, updatable = false)
    private Hold hold;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_pool_id", nullable = false, updatable = false)
    private ResourcePool resourcePool;

    // Null for COUNTER_BASED lines — see class-level invariant.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_unit_id", updatable = false)
    private ResourceUnit resourceUnit;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    /** Unit-based line: claims exactly one specific, identifiable unit. */
    public HoldLine(Hold hold, ResourceUnit resourceUnit) {
        if (resourceUnit.getResourcePool().getPoolMode() != PoolMode.UNIT_BASED) {
            throw new IllegalArgumentException("ResourceUnit lines require a UNIT_BASED pool");
        }
        this.id = UUID.randomUUID().toString();
        this.hold = hold;
        this.resourceUnit = resourceUnit;
        this.resourcePool = resourceUnit.getResourcePool();
        this.quantity = 1;
    }

    /** Counter-based line: claims a quantity against an undifferentiated pool. */
    public HoldLine(Hold hold, ResourcePool resourcePool, int quantity) {
        if (resourcePool.getPoolMode() != PoolMode.COUNTER_BASED) {
            throw new IllegalArgumentException("Quantity lines require a COUNTER_BASED pool");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.id = UUID.randomUUID().toString();
        this.hold = hold;
        this.resourcePool = resourcePool;
        this.resourceUnit = null;
        this.quantity = quantity;
    }
}