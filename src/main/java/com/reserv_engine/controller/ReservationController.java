package com.reserv_engine.controller;

import com.reserv_engine.dto.ConfirmReservationRequest;
import com.reserv_engine.dto.ReservationResponse;
import com.reserv_engine.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse confirm(@RequestBody ConfirmReservationRequest request) {
        return reservationService.confirm(request);
    }
}