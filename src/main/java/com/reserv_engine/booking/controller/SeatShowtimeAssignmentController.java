package com.reserv_engine.booking.controller;

import com.reserv_engine.booking.dto.request.CreateSeatAssignmentsRequest;
import com.reserv_engine.booking.dto.response.SeatShowtimeAssignmentResponse;
import com.reserv_engine.booking.entity.SeatShowtimeAssignment;
import com.reserv_engine.booking.service.SeatShowtimeAssignmentService;
import com.reserv_engine.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ticket-tiers/{tierId}/seat-assignments")
public class SeatShowtimeAssignmentController {

    private final SeatShowtimeAssignmentService seatShowtimeAssignmentService;

    public SeatShowtimeAssignmentController(SeatShowtimeAssignmentService seatShowtimeAssignmentService) {
        this.seatShowtimeAssignmentService = seatShowtimeAssignmentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<List<SeatShowtimeAssignmentResponse>> assign(
            @PathVariable String tierId, @Valid @RequestBody CreateSeatAssignmentsRequest request) {
        String currentUserId = SecurityUtils.currentUserId();
        List<SeatShowtimeAssignment> assignments =
                seatShowtimeAssignmentService.assignSeats(tierId, currentUserId, request.seatIds());
        List<SeatShowtimeAssignmentResponse> body = assignments.stream()
                .map(SeatShowtimeAssignmentResponse::from)
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}