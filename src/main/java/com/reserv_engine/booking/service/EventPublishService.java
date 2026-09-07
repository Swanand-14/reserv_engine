package com.reserv_engine.booking.service;

import com.reserv_engine.booking.entity.Event;
import com.reserv_engine.booking.repository.EventRepository;
import com.reserv_engine.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventPublishService {

    private final EventRepository eventRepository;

    public EventPublishService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public Event publish(String eventId, String currentUserId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        if (!event.getOrganizer().getId().equals(currentUserId)) {
            throw new AccessDeniedException("You do not organize this Event");
        }

        event.publish(); // throws ResourceConflictException if not currently DRAFT
        return eventRepository.save(event);
    }
}