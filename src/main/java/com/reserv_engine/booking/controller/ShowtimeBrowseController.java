package com.reserv_engine.booking.controller;

import com.reserv_engine.booking.dto.response.ShowtimeBrowseResponse;
import com.reserv_engine.booking.service.ShowtimeBrowseService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events/{eventId}/showtimes")
public class ShowtimeBrowseController {

    private final ShowtimeBrowseService showtimeBrowseService;

    public ShowtimeBrowseController(ShowtimeBrowseService showtimeBrowseService) {
        this.showtimeBrowseService = showtimeBrowseService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ShowtimeBrowseResponse> listShowtimes(@PathVariable String eventId) {
        return showtimeBrowseService.listShowtimesForEvent(eventId);
    }
}