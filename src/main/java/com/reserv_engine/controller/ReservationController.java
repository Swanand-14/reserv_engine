package com.reserv_engine.controller;

import com.reserv_engine.dto.ConfirmReservationRequest;
import com.reserv_engine.dto.ReservationResponse;
import com.reserv_engine.service.ReservationCancelService;
import com.reserv_engine.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationCancelService reservationCancelService;

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse confirm(@RequestBody ConfirmReservationRequest request) {
        return reservationService.confirm(request);
    }

    @PostMapping("/{id}/cancel")
    public ReservationResponse cancel(@PathVariable("id") String id) {
        return reservationCancelService.cancel(id);
    }
}