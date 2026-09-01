package com.reserv_engine.booking.controller;

import com.reserv_engine.booking.dto.request.CreateVenueRequest;
import com.reserv_engine.booking.dto.response.VenueResponse;
import com.reserv_engine.booking.entity.Venue;
import com.reserv_engine.booking.service.VenueService;
import com.reserv_engine.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/venues")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<VenueResponse> create(@Valid @RequestBody CreateVenueRequest request) {
        String currentUserId = SecurityUtils.currentUserId();
        Venue venue = venueService.create(currentUserId, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(VenueResponse.from(venue));
    }
}