package com.reserv_engine.booking.service;

import com.reserv_engine.booking.entity.Event;
import com.reserv_engine.booking.repository.EventRepository;
import com.reserv_engine.entity.User;
import com.reserv_engine.exception.ResourceNotFoundException;
import com.reserv_engine.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    public EventService(EventRepository eventRepository, UserRepository userRepository) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Event create(String currentUserId, String title) {
        User organizer = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUserId));
        return eventRepository.save(new Event(organizer, title));
    }
}