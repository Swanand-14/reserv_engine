package com.reserv_engine.entity;

import com.reserv_engine.core.domain.HoldStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * [Engine] The short-lived, time-boxed claim created during checkout —
 * the centerpiece of the frozen reservation workflow.
 *
 * Invariants (enforced here + service layer):
 *  - expiresAt must be after createdAt (checked at construction).
 *  - Only ACTIVE holds can become Reservations (checked in service layer,
 *    once Reservation exists).
 *  - ACTIVE is the only non-terminal status; CONSUMED/EXPIRED/CANCELLED
 *    can never transition anywhere else (checked in service layer).
 *  - Must contain at least one HoldLine (checked in service layer, since
 *    that's where lines get added one at a time).
 */
@Entity
@Table(name = "hold")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hold {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "holder_id", length = 36, nullable = false, updatable = false)
    private String holderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private HoldStatus status;

    @Column(name = "idempotency_key", length = 100, nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    // orphanRemoval = true: a HoldLine has no meaning outside its Hold, same
    // reasoning as ResourcePool -> ResourceUnit. No cascade REMOVE at the
    // Hold level itself — deleting a Hold row entirely isn't a workflow
    // concept (holds transition to EXPIRED/CANCELLED/CONSUMED, they aren't
    // deleted).
    @OneToMany(mappedBy = "hold", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<HoldLine> holdLines = new ArrayList<>();

    public Hold(String holderId, Duration ttl, String idempotencyKey) {
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        this.id = UUID.randomUUID().toString();
        this.holderId = holderId;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = this.createdAt.plus(ttl);
        this.idempotencyKey = idempotencyKey;
        this.status = HoldStatus.ACTIVE;
    }

    public void addHoldLine(HoldLine line) {
        holdLines.add(line);
        line.setHold(this);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}