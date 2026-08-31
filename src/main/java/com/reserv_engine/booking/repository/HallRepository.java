package com.reserv_engine.booking.repository;

import com.reserv_engine.booking.entity.Hall;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HallRepository extends JpaRepository<Hall, String> {
    List<Hall> findByVenueId(String venueId);
}