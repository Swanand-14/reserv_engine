package com.reserv_engine.controller;

import com.reserv_engine.dto.ConfirmReservationRequest;
import com.reserv_engine.dto.ReservationResponse;
import com.reserv_engine.security.OwnershipGuard;
import com.reserv_engine.security.SecurityUtils;
import com.reserv_engine.service.ReservationCancelService;
import com.reserv_engine.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationCancelService reservationCancelService;
    private final OwnershipGuard ownershipGuard;

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    public ReservationResponse confirm(@RequestBody ConfirmReservationRequest request) {
        ownershipGuard.assertOwnsHold(request.holdId(), SecurityUtils.currentUserId());
        return reservationService.confirm(request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ReservationResponse cancel(@PathVariable("id") String id) {
        ownershipGuard.assertOwnsReservation(id, SecurityUtils.currentUserId());
        return reservationCancelService.cancel(id);
    }
}