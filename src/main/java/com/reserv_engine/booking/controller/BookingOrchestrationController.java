package com.reserv_engine.booking.controller;

import com.reserv_engine.booking.dto.request.CreateBookingRequest;
import com.reserv_engine.booking.service.BookingOrchestrationService;
import com.reserv_engine.dto.HoldResponse;
import com.reserv_engine.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/showtimes/{showtimeId}/bookings")
public class BookingOrchestrationController {

    private final BookingOrchestrationService bookingOrchestrationService;

    public BookingOrchestrationController(BookingOrchestrationService bookingOrchestrationService) {
        this.bookingOrchestrationService = bookingOrchestrationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    public HoldResponse book(@PathVariable String showtimeId, @Valid @RequestBody CreateBookingRequest request) {
        String currentUserId = SecurityUtils.currentUserId();
        return bookingOrchestrationService.bookSeats(
                showtimeId, currentUserId, request.seatIds(), request.idempotencyKey());
    }
}