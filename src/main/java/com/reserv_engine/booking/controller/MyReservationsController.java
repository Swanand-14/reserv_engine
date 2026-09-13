package com.reserv_engine.booking.controller;

import com.reserv_engine.booking.dto.response.MyReservationResponse;
import com.reserv_engine.booking.service.MyReservationsService;
import com.reserv_engine.security.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Deliberately NOT /reservations/my — the Engine's ReservationController
// already owns that path (its own, un-enriched, resourcePoolId/
// resourceUnitId-shaped view). This is the Booking-side, customer-facing
// equivalent, kept at its own path rather than replacing or wrapping it.
@RestController
@RequestMapping("/api/v1/my-reservations")
public class MyReservationsController {

    private final MyReservationsService myReservationsService;

    public MyReservationsController(MyReservationsService myReservationsService) {
        this.myReservationsService = myReservationsService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<MyReservationResponse> getMyReservations() {
        String currentUserId = SecurityUtils.currentUserId();
        return myReservationsService.getMyReservations(currentUserId);
    }
}