package com.reserv_engine.booking.entity;

import com.reserv_engine.entity.ResourceUnit;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * [Booking] The link entity that makes Seat<->ResourceUnit translation
 * possible without the Engine ever knowing what a "seat" is. One row per
 * (Seat, Showtime) pair, created in bulk at showtime-scheduling time,
 * zipping that Showtime's physical Seats to the freshly-generated
 * ResourceUnit rows in its TicketTiers' pools.
 *
 * Written once, by a single Organizer request, at scheduling time — never
 * written to by customers, so it carries no concurrency exposure itself.
 * All contested state lives on the referenced ResourceUnit (status,
 * @Version), which is read here but never modified here.
 *
 * Two constraints do the real work (enforced at the DB, mirrored by
 * unique repository finders below):
 *  - (seat_id, showtime_id) unique: a seat can't be assigned twice within
 *    one showtime.
 *  - resource_unit_id unique: one ResourceUnit can't be claimed by two
 *    seats -- this is what keeps the seat map from corrupting silently.
 */
@Entity
@Table(name = "seat_showtime_assignments")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeatShowtimeAssignment {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false, updatable = false)
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "showtime_id", nullable = false, updatable = false)
    private Showtime showtime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_unit_id", nullable = false, updatable = false, unique = true)
    private ResourceUnit resourceUnit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public SeatShowtimeAssignment(Seat seat, Showtime showtime, ResourceUnit resourceUnit) {
        this.id = UUID.randomUUID().toString();
        this.seat = seat;
        this.showtime = showtime;
        this.resourceUnit = resourceUnit;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}