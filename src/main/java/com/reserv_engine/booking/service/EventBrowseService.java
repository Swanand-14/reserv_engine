package com.reserv_engine.booking.service;

import com.reserv_engine.booking.dto.response.EventBrowseResponse;
import com.reserv_engine.booking.types.EventLifecycleStatus;
import com.reserv_engine.booking.repository.EventRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventBrowseService {

    private final EventRepository eventRepository;

    public EventBrowseService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public List<EventBrowseResponse> listPublishedEvents() {
        return eventRepository.findByLifecycleStatus(EventLifecycleStatus.PUBLISHED).stream()
                .map(e -> new EventBrowseResponse(e.getId(), e.getTitle()))
                .toList();
    }
}