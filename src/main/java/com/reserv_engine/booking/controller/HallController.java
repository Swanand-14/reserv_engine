package com.reserv_engine.booking.controller;

import com.reserv_engine.booking.dto.request.CreateHallRequest;
import com.reserv_engine.booking.dto.response.HallResponse;
import com.reserv_engine.booking.entity.Hall;
import com.reserv_engine.booking.service.HallService;
import com.reserv_engine.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/venues/{venueId}/halls")
public class HallController {

    private final HallService hallService;

    public HallController(HallService hallService) {
        this.hallService = hallService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<HallResponse> create(@PathVariable String venueId,
                                               @Valid @RequestBody CreateHallRequest request) {
        String currentUserId = SecurityUtils.currentUserId();
        Hall hall = hallService.create(venueId, currentUserId, request.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(HallResponse.from(hall));
    }
}