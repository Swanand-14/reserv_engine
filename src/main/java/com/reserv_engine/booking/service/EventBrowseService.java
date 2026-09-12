package com.reserv_engine.booking.service;

import com.reserv_engine.booking.dto.response.EventBrowseResponse;
import com.reserv_engine.booking.types.EventLifecycleStatus;
import com.reserv_engine.booking.repository.EventRepository;
import com.reserv_engine.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventBrowseService {

    private final EventRepository eventRepository;

    public EventBrowseService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public List<EventBrowseResponse> listPublishedEvents() {
        return eventRepository.findBrowseRowsByLifecycleStatus(EventLifecycleStatus.PUBLISHED).stream()
                .map(EventBrowseResponse::from)
                .toList();
    }

    public EventBrowseResponse getPublishedEventDetail(String eventId) {
        return eventRepository.findBrowseRowByIdAndLifecycleStatus(eventId, EventLifecycleStatus.PUBLISHED)
                .map(EventBrowseResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Published event not found: " + eventId));
    }
}