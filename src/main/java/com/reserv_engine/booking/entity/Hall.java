package com.reserv_engine.booking.entity;

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
 * [Booking] A room/screen within a Venue. Structural, no capacity of its
 * own — capacity lives entirely in the Engine's ResourcePool, created per
 * Showtime, never here.
 */
@Entity
@Table(name = "halls")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hall {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false, updatable = false)
    private Venue venue;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "hall", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Seat> seats = new ArrayList<>();

    public Hall(Venue venue, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        this.id = UUID.randomUUID().toString();
        this.venue = venue;
        this.name = name;
    }

    public void addSeat(Seat seat) {
        seats.add(seat);
        seat.setHall(this);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}