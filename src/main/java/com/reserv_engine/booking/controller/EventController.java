package com.reserv_engine.booking.controller;

import com.reserv_engine.booking.dto.request.CreateEventRequest;
import com.reserv_engine.booking.dto.response.EventResponse;
import com.reserv_engine.booking.entity.Event;
import com.reserv_engine.booking.service.EventService;
import com.reserv_engine.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request) {
        String currentUserId = SecurityUtils.currentUserId();
        Event event = eventService.create(currentUserId, request.title());
        return ResponseEntity.status(HttpStatus.CREATED).body(EventResponse.from(event));
    }
}