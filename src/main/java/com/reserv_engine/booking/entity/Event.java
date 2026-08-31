package com.reserv_engine.booking.entity;

import com.reserv_engine.booking.types.EventLifecycleStatus;
import com.reserv_engine.entity.User;
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
 * [Booking] Metadata for a bookable thing an Organizer runs — e.g. a movie
 * title, a concert. Carries no capacity or timing of its own; each concrete
 * occurrence is a Showtime, which is what actually touches the Engine.
 *
 * organizerId is a real FK to User (same intentional divergence as
 * Venue.manager) since Booking is app-specific, unlike the Engine's
 * ID-only ownerId/holderId.
 */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id", nullable = false, updatable = false)
    private User organizer;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", length = 20, nullable = false)
    private EventLifecycleStatus lifecycleStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // No orphanRemoval: a Showtime drives real Engine state (AvailabilityWindow,
    // ResourcePool, potentially live Holds). Deleting an Event must never
    // silently cascade-delete a Showtime — that requires a service-layer check
    // first, same reasoning as AvailabilityWindow -> ResourcePool in the Engine.
    @OneToMany(mappedBy = "event", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    private List<Showtime> showtimes = new ArrayList<>();

    public Event(User organizer, String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title cannot be blank");
        }
        this.id = UUID.randomUUID().toString();
        this.organizer = organizer;
        this.title = title;
        this.lifecycleStatus = EventLifecycleStatus.DRAFT;
    }

    public void addShowtime(Showtime showtime) {
        showtimes.add(showtime);
        showtime.setEvent(this);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}