package com.reserv_engine.booking.repository;

import com.reserv_engine.booking.entity.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShowtimeRepository extends JpaRepository<Showtime, String> {
    List<Showtime> findByEventId(String eventId);
    List<Showtime> findByHallId(String hallId);
    Optional<Showtime> findByAvailabilityWindowId(String availabilityWindowId);
}