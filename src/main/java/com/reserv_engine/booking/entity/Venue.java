package com.reserv_engine.booking.entity;

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
 * [Booking] A physical location an Organizer manages. Purely structural —
 * no capacity, no concurrency exposure. Manager is a real FK to User
 * (unlike Engine's ID-only ownerId/holderId) since Booking is app-specific
 * and isn't meant to be domain-portable like the Engine.
 *
 * Owns the FK to User (@ManyToOne) and is the "one" side of Hall
 * (@OneToMany) — both real Booking-internal JPA relationships.
 */
@Entity
@Table(name = "venues")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Venue {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)//one user can handle multiple venues
    @JoinColumn(name = "manager_id", nullable = false, updatable = false)
    private User manager;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // orphanRemoval = true: a Hall has no meaning outside its Venue —
    // same reasoning as ResourcePool -> ResourceUnit.
    @OneToMany(mappedBy = "venue", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Hall> halls = new ArrayList<>();

    public Venue(User manager, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        this.id = UUID.randomUUID().toString();
        this.manager = manager;
        this.name = name;
    }

    public void addHall(Hall hall) {
        halls.add(hall);
        hall.setVenue(this);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}