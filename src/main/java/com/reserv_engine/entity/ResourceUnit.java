package com.reserv_engine.entity;

import com.reserv_engine.core.domain.ResourceUnitStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;
import com.reserv_engine.core.domain.ResourceUnitStatus;
/**
 * [Engine] An individually tracked reservable unit, for pools where identity
 * (not just quantity) matters — e.g. one numbered seat's reservable slot for
 * one specific showtime. Carries no seat number/label — that meaning lives
 * in the App layer's SeatShowtimeAssignment, which points at this unit's id
 * as an opaque cross-layer reference.
 *
 * Owns the FK to ResourcePool (@ManyToOne) — the "many" side of the
 * pool<->unit relationship.
 *
 * Invariants (enforced in service layer):
 *  - status = HELD only while referenced by exactly one ACTIVE Hold's HoldLine.
 *  - status = RESERVED only while referenced by exactly one CONFIRMED
 *    Reservation's ReservationLine.
 *  - Must return to AVAILABLE the moment its owning Hold leaves ACTIVE state
 *    without converting to a Reservation.
 */
@Entity
@Table(name = "resource_unit")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResourceUnit {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_pool_id", nullable = false, updatable = false)
    private ResourcePool resourcePool;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private com.reserv_engine.core.domain.ResourceUnitStatus status;

    // Optimistic lock — the mechanism unit-based pools will use for hold creation.
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ResourceUnit(ResourcePool resourcePool) {
        this.id = UUID.randomUUID().toString();
        this.resourcePool = resourcePool;
        this.status = ResourceUnitStatus.AVAILABLE;
    }

    @PrePersist
    void onCreate() {

    }


}