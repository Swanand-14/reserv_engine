package com.reserv_engine.booking.entity;

import com.reserv_engine.entity.ResourcePool;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * [Booking] A priced category (e.g. "Standard", "Premium") for one
 * Showtime, backed by its own dedicated Engine ResourcePool — one pool per
 * tier, all pools sharing the Showtime's single AvailabilityWindow. This is
 * how independent per-tier capacity is achieved using an Engine that has
 * no concept of "tier" at all: the Engine already supports multiple
 * ResourcePools per AvailabilityWindow, this just uses that as-is.
 *
 * resourcePool is a real @ManyToOne into the Engine (Booking -> Engine,
 * the allowed direction). The reverse never happens: ResourcePool has no
 * idea a TicketTier exists.
 *
 * A seat's tier for a given Showtime is NOT stored directly on the seat —
 * it's derived transitively via SeatShowtimeAssignment.resourceUnitId ->
 * resourceUnit.resourcePoolId -> this table. Deliberate: the same physical
 * seat can be a different tier for a different Showtime.
 */
@Entity
@Table(name = "ticket_tiers")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketTier {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "showtime_id", nullable = false, updatable = false)
    private Showtime showtime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_pool_id", nullable = false, updatable = false, unique = true)
    private ResourcePool resourcePool;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public TicketTier(Showtime showtime, ResourcePool resourcePool, String name, BigDecimal price) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        this.id = UUID.randomUUID().toString();
        this.showtime = showtime;
        this.resourcePool = resourcePool;
        this.name = name;
        this.price = price;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}