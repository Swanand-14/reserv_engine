package com.reserv_engine.booking.service;

import com.reserv_engine.booking.entity.Venue;
import com.reserv_engine.booking.repository.VenueRepository;
import com.reserv_engine.entity.User;
import com.reserv_engine.exception.ResourceNotFoundException;
import com.reserv_engine.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VenueService {

    private final VenueRepository venueRepository;
    private final UserRepository userRepository;

    public VenueService(VenueRepository venueRepository, UserRepository userRepository) {
        this.venueRepository = venueRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Venue create(String currentUserId, String name) {
        User manager = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUserId));
        return venueRepository.save(new Venue(manager, name));
    }
}