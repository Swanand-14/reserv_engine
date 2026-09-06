package com.reserv_engine.booking.controller;

import com.reserv_engine.booking.dto.response.EventBrowseResponse;
import com.reserv_engine.booking.service.EventBrowseService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
public class EventBrowseController {

    private final EventBrowseService eventBrowseService;

    public EventBrowseController(EventBrowseService eventBrowseService) {
        this.eventBrowseService = eventBrowseService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<EventBrowseResponse> listPublishedEvents() {
        return eventBrowseService.listPublishedEvents();
    }
}