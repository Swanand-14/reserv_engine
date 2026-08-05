package com.reserv_engine.entity;

import com.reserv_engine.core.domain.PoolMode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * [Engine] The unit of protected, limited capacity — what the whole engine
 * exists to prevent from being oversold. Does not know it represents
 * "seats" or "rooms"; that meaning lives entirely in the App layer
 * (e.g. TicketTier holds this pool's id as an opaque cross-layer reference).
 *
 * Owns the FK to AvailabilityWindow (@ManyToOne) and is the "one" side of
 * ResourceUnit (@OneToMany) — both real Engine-internal JPA relationships.
 *
 * Invariants (enforced in service layer / DB constraints):
 *  - remainingCapacity never negative, never exceeds totalCapacity.
 *  - UNIT_BASED pools: totalCapacity must equal count of child ResourceUnits.
 *  - COUNTER_BASED pools: no ResourceUnit rows exist.
 *  - poolMode is immutable once any Hold has been created against this pool.
 */
@Entity
@Table(name = "resource_pool")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResourcePool {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    // fetch = LAZY explicitly — @ManyToOne defaults to EAGER in plain JPA,
    // which is the single most common source of accidental N+1 / over-fetching.
    // Always override it.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "availability_window_id", nullable = false, updatable = false)
    private AvailabilityWindow availabilityWindow;

    @Column(name = "owner_id", length = 36, nullable = false, updatable = false)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pool_mode", length = 20, nullable = false, updatable = false)
    private PoolMode poolMode;

    @Column(name = "total_capacity", nullable = false)
    private int totalCapacity;

    @Column(name = "remaining_capacity", nullable = false)
    private int remainingCapacity;

    // Optimistic lock — meaningful once locking is introduced; harmless before that.
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // orphanRemoval = true here (unlike the window->pool relationship above) is
    // deliberate: a ResourceUnit has no meaning or lifecycle outside its pool —
    // it's a dependent/child entity, the textbook case for orphanRemoval.
    // Cascade REMOVE is intentionally NOT added at the entity-delete level;
    // the service layer must still verify no unit is HELD/RESERVED before a
    // pool (or a unit removed from its collection) is actually deleted.
    @OneToMany(mappedBy = "resourcePool", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ResourceUnit> resourceUnits = new ArrayList<>();

    public ResourcePool(AvailabilityWindow availabilityWindow, String ownerId, PoolMode poolMode, int totalCapacity) {
        if (totalCapacity < 0) {
            throw new IllegalArgumentException("totalCapacity cannot be negative");
        }
        this.id = UUID.randomUUID().toString();
        this.availabilityWindow = availabilityWindow;
        this.ownerId = ownerId;
        this.poolMode = poolMode;
        this.totalCapacity = totalCapacity;
        this.remainingCapacity = totalCapacity;
    }

    public void addResourceUnit(ResourceUnit unit) {
        if (poolMode != PoolMode.UNIT_BASED) {
            throw new IllegalStateException("Only UNIT_BASED pools may contain ResourceUnits");
        }
        resourceUnits.add(unit);
        unit.setResourcePool(this);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}