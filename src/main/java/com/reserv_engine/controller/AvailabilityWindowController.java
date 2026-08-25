package com.reserv_engine.controller;

import com.reserv_engine.dto.AvailabilityWindowResponse;
import com.reserv_engine.dto.CreateAvailabilityWindowRequest;
import com.reserv_engine.entity.AvailabilityWindow;
import com.reserv_engine.security.SecurityUtils;
import com.reserv_engine.service.AvailabilityWindowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/availability-windows")
public class AvailabilityWindowController {

    private final AvailabilityWindowService availabilityWindowService;

    public AvailabilityWindowController(AvailabilityWindowService availabilityWindowService) {
        this.availabilityWindowService = availabilityWindowService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<AvailabilityWindowResponse> create(@Valid @RequestBody CreateAvailabilityWindowRequest request) {
        String ownerId = SecurityUtils.currentUserId();
        AvailabilityWindow window = availabilityWindowService.create(ownerId, request.startTime(), request.endTime());
        return ResponseEntity.status(HttpStatus.CREATED).body(AvailabilityWindowResponse.from(window));
    }
}