package com.reserv_engine.booking.service;

import com.reserv_engine.booking.entity.Hall;
import com.reserv_engine.booking.entity.Venue;
import com.reserv_engine.booking.repository.HallRepository;
import com.reserv_engine.booking.repository.VenueRepository;
import com.reserv_engine.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HallService {

    private final HallRepository hallRepository;
    private final VenueRepository venueRepository;

    public HallService(HallRepository hallRepository, VenueRepository venueRepository) {
        this.hallRepository = hallRepository;
        this.venueRepository = venueRepository;
    }

    @Transactional
    public Hall create(String venueId, String currentUserId, String name) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new ResourceNotFoundException("Venue not found: " + venueId));

        if (!venue.getManager().getId().equals(currentUserId)) {
            throw new AccessDeniedException("You do not manage this Venue");
        }

        return hallRepository.save(new Hall(venue, name));
    }
}