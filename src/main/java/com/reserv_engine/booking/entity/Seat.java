package com.reserv_engine.booking.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * [Booking] A physical seat within a Hall. Deliberately has NO tier/category
 * column — which tier a seat belongs to is derived per-Showtime via
 * SeatShowtimeAssignment -> ResourceUnit -> ResourcePool -> TicketTier,
 * so the same physical seat can be Standard for one showtime and Premium
 * for another. Structural only; never touched by concurrent bookings —
 * only the Engine's ResourceUnit rows are.
 */
@Entity
@Table(name = "seats", uniqueConstraints = @UniqueConstraint(name = "uq_seat_hall_label", columnNames = {"hall_id", "label"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hall_id", nullable = false, updatable = false)
    private Hall hall;

    @Column(name = "label", length = 20, nullable = false)
    private String label;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Seat(Hall hall, String label) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label cannot be blank");
        }
        this.id = UUID.randomUUID().toString();
        this.hall = hall;
        this.label = label;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}