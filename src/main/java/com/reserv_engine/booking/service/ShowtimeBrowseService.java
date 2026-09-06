package com.reserv_engine.booking.service;

import com.reserv_engine.booking.dto.response.ShowtimeBrowseResponse;
import com.reserv_engine.booking.entity.Event;
import com.reserv_engine.booking.types.EventLifecycleStatus;
import com.reserv_engine.booking.repository.EventRepository;
import com.reserv_engine.booking.repository.ShowtimeRepository;
import com.reserv_engine.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShowtimeBrowseService {

    private final ShowtimeRepository showtimeRepository;
    private final EventRepository eventRepository;

    public ShowtimeBrowseService(ShowtimeRepository showtimeRepository, EventRepository eventRepository) {
        this.showtimeRepository = showtimeRepository;
        this.eventRepository = eventRepository;
    }

    public List<ShowtimeBrowseResponse> listShowtimesForEvent(String eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found: " + eventId));

        // Hide DRAFT/CANCELLED events from browsing entirely — not an
        // ownership issue, so 404 (not 403) is the correct response.
        if (event.getLifecycleStatus() != EventLifecycleStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Event not found: " + eventId);
        }

        return showtimeRepository.findByEventId(eventId).stream()
                .map(s -> new ShowtimeBrowseResponse(s.getId(), s.getHall().getId(), s.getStartTime(), s.getEndTime()))
                .toList();
    }
}