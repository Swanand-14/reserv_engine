package com.reserv_engine.booking.entity;

import com.reserv_engine.entity.AvailabilityWindow;
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
 * [Booking] One concrete occurrence of an Event in a specific Hall at a
 * specific time — e.g. "Inception, Screen 3, 7:00 PM Aug 30". This is the
 * one Booking entity that reaches across into the Engine: creating a
 * Showtime is expected to create exactly one dedicated AvailabilityWindow
 * (1:1, enforced with a unique constraint on availability_window_id — no
 * two Showtimes may share a window). TicketTiers then attach ResourcePools
 * to that same window, one per tier.
 *
 * startTime/endTime here are the Booking-facing display values; they are
 * expected to match availabilityWindow's own start/end exactly, kept in
 * sync by whichever service creates both together — not re-validated
 * against each other here, to avoid coupling this entity's invariants to
 * an Engine entity's internal state.
 */
@Entity
@Table(name = "showtimes")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Showtime {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false, updatable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hall_id", nullable = false, updatable = false)
    private Hall hall;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "availability_window_id", nullable = false, updatable = false, unique = true)
    private AvailabilityWindow availabilityWindow;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // No orphanRemoval: a TicketTier attaches a live Engine ResourcePool.
    // Same reasoning as Event -> Showtime above.
    @OneToMany(mappedBy = "showtime", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    private List<TicketTier> ticketTiers = new ArrayList<>();

    public Showtime(Event event, Hall hall, AvailabilityWindow availabilityWindow,
                    LocalDateTime startTime, LocalDateTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        this.id = UUID.randomUUID().toString();
        this.event = event;
        this.hall = hall;
        this.availabilityWindow = availabilityWindow;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void addTicketTier(TicketTier tier) {
        ticketTiers.add(tier);
        tier.setShowtime(this);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}