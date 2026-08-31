package com.reserv_engine.booking.repository;

import com.reserv_engine.booking.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, String> {
    List<Event> findByOrganizerId(String organizerId);
}