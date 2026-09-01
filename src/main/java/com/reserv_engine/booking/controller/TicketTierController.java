package com.reserv_engine.booking.controller;

import com.reserv_engine.booking.dto.request.CreateTicketTierRequest;
import com.reserv_engine.booking.dto.response.TicketTierResponse;
import com.reserv_engine.booking.entity.TicketTier;
import com.reserv_engine.booking.service.TicketTierService;
import com.reserv_engine.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/showtimes/{showtimeId}/ticket-tiers")
public class TicketTierController {

    private final TicketTierService ticketTierService;

    public TicketTierController(TicketTierService ticketTierService) {
        this.ticketTierService = ticketTierService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<TicketTierResponse> create(@PathVariable String showtimeId,
                                                     @Valid @RequestBody CreateTicketTierRequest request) {
        String currentUserId = SecurityUtils.currentUserId();
        TicketTier tier = ticketTierService.create(
                showtimeId, currentUserId, request.name(), request.price(), request.totalCapacity());
        return ResponseEntity.status(HttpStatus.CREATED).body(TicketTierResponse.from(tier));
    }
}