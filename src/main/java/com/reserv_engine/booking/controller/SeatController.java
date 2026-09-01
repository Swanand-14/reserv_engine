package com.reserv_engine.booking.controller;

import com.reserv_engine.booking.dto.request.CreateSeatRequest;
import com.reserv_engine.booking.dto.response.SeatResponse;
import com.reserv_engine.booking.entity.Seat;
import com.reserv_engine.booking.service.SeatService;
import com.reserv_engine.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/halls/{hallId}/seats")
public class SeatController {

    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<SeatResponse> create(@PathVariable String hallId,
                                               @Valid @RequestBody CreateSeatRequest request) {
        String currentUserId = SecurityUtils.currentUserId();
        Seat seat = seatService.create(hallId, currentUserId, request.label());
        return ResponseEntity.status(HttpStatus.CREATED).body(SeatResponse.from(seat));
    }
}