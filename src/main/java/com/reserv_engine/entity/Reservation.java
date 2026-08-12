package com.reserv_engine.entity;

import com.reserv_engine.core.domain.ReservationStatus;
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
 * [Engine] The confirmed outcome of a Hold after successful payment.
 *
 * Invariants (enforced here + service layer):
 *  - Must originate from exactly one Hold — holdId is set once at
 *    construction and never changes (updatable = false), and carries a
 *    UNIQUE constraint at the DB level (see migration) since a Hold can
 *    produce at most one Reservation.
 *  - holderId is copied from the originating Hold at confirm time — never
 *    re-derived later, so it can't drift if the Hold row is touched again.
 *  - Cannot exist without at least one ReservationLine (checked in service
 *    layer, same reasoning as Hold/HoldLine — lines get added one at a time).
 *  - The Hold->CONSUMED transition happens in the SAME transaction that
 *    creates this row (enforced in service layer, not here — this entity
 *    has no reference back to mutate its Hold).
 *  - CONFIRMED is the only non-terminal status; CANCELLED/COMPLETED can
 *    never transition anywhere else (checked in service layer).
 */
@Entity
@Table(name = "reservation")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    // Opaque link to the originating Hold — not a JPA @OneToOne relationship
    // on purpose. Confirm loads the Hold separately (it needs to lock/mutate
    // it anyway); Reservation only needs to remember which Hold it came from.
    @Column(name = "hold_id", length = 36, nullable = false, updatable = false, unique = true)
    private String holdId;

    @Column(name = "holder_id", length = 36, nullable = false, updatable = false)
    private String holderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ReservationStatus status;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private LocalDateTime confirmedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    // orphanRemoval = true: a ReservationLine has no meaning outside its
    // Reservation, same reasoning as Hold -> HoldLine.
    @OneToMany(mappedBy = "reservation", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ReservationLine> reservationLines = new ArrayList<>();

    public Reservation(String holdId, String holderId) {
        this.id = UUID.randomUUID().toString();
        this.holdId = holdId;
        this.holderId = holderId;
        this.confirmedAt = LocalDateTime.now();
        this.status = ReservationStatus.CONFIRMED;
    }

    public void addReservationLine(ReservationLine line) {
        reservationLines.add(line);
        line.setReservation(this);
    }
}