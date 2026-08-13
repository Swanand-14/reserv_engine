package com.reserv_engine.entity;

import com.reserv_engine.core.domain.PaymentAttemptStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * [Engine] A record of one try to pay for a Hold — needed because the
 * frozen workflow explicitly allows retry on failure while the Hold stays
 * active.
 *
 * Opaque holdId reference, same reasoning as Reservation.holdId: this
 * doesn't need a JPA relationship back to Hold, and keeping it a plain
 * column avoids coupling a payment-simulation concern into Hold's own
 * entity graph.
 *
 * Invariants (enforced here + service layer):
 *  - idempotencyKey is unique (DB constraint) — a retried request carrying
 *    the same key must resolve to the existing PaymentAttempt, never
 *    create a second row. Same pattern as Hold.idempotencyKey.
 *  - At most one PaymentAttempt per Hold may reach status = SUCCESS
 *    (service layer — checked before creating/resolving a new attempt).
 *  - A PaymentAttempt cannot be created or resolved to SUCCESS against a
 *    Hold that is not ACTIVE (service layer).
 *  - Once a PaymentAttempt reaches SUCCESS, no further attempt against the
 *    same Hold is processed (service layer).
 *  - status transitions PENDING -> SUCCESS or PENDING -> FAILED only;
 *    SUCCESS and FAILED are terminal (service layer).
 */
@Entity
@Table(name = "payment_attempt")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAttempt {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "hold_id", length = 36, nullable = false, updatable = false)
    private String holdId;

    @Column(name = "idempotency_key", length = 100, nullable = false, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private PaymentAttemptStatus status;

    @Column(name = "amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public PaymentAttempt(String holdId, String idempotencyKey, BigDecimal amount) {
        this.id = UUID.randomUUID().toString();
        this.holdId = holdId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
        this.status = PaymentAttemptStatus.PENDING;
    }

    public void markSuccess() {
        if (this.status != PaymentAttemptStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot mark PaymentAttempt SUCCESS from status " + this.status);
        }
        this.status = PaymentAttemptStatus.SUCCESS;
    }

    public void markFailed() {
        if (this.status != PaymentAttemptStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot mark PaymentAttempt FAILED from status " + this.status);
        }
        this.status = PaymentAttemptStatus.FAILED;
    }
}