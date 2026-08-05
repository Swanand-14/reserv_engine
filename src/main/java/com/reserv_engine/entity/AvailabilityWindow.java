package com.reserv_engine.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * [Engine] Generic bounded time period during which one or more ResourcePools
 * are offered. Deliberately has no knowledge of "showtime", "flight", etc. —
 * the App layer's Showtime (for example) instantiates one of these and stores
 * its id as an opaque reference, never the other way around.
 *
 * This is the "one" side of a real @OneToMany/@ManyToOne mapping with
 * ResourcePool — an internal Engine-to-Engine relationship, unlike the
 * cross-layer Engine<->App links, which stay opaque ids by design.
 *
 * Invariants (enforced in service layer / DB constraints, not shown here):
 *  - endTime must be strictly after startTime.
 *  - Cannot be deleted while any child ResourcePool has an ACTIVE Hold
 *    or CONFIRMED Reservation.
 */
@Entity
@Table(name = "availability_window")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA only — use the constructor below elsewhere
public class AvailabilityWindow {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "owner_id", length = 36, nullable = false, updatable = false)
    private String ownerId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Inverse side — mappedBy means ResourcePool.availabilityWindow owns the FK column.
    // No REMOVE cascade and no orphanRemoval: deleting a window must NOT silently
    // delete pools that may still have active holds/reservations against them —
    // that's a business rule enforced in the service layer, not something JPA
    // should do automatically via cascade.
    @OneToMany(mappedBy = "availabilityWindow", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    private List<ResourcePool> resourcePools = new ArrayList<>();

    public AvailabilityWindow(String ownerId, LocalDateTime startTime, LocalDateTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        this.id = UUID.randomUUID().toString();
        this.ownerId = ownerId;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    // Keeps both sides of the bidirectional relationship in sync — the classic
    // JPA gotcha is setting only one side and getting a stale in-memory graph
    // even though the DB FK is correct. Always go through helpers like this
    // instead of calling getResourcePools().add(...) directly.
    public void addResourcePool(ResourcePool pool) {
        resourcePools.add(pool);
        pool.setAvailabilityWindow(this);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }



}