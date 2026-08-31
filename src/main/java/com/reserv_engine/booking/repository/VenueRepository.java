package com.reserv_engine.booking.repository;

import com.reserv_engine.booking.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VenueRepository extends JpaRepository<Venue, String> {
    List<Venue> findByManagerId(String managerId);
}