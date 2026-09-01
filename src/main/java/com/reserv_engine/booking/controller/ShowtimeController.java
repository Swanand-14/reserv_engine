package com.reserv_engine.booking.controller;

import com.reserv_engine.booking.dto.request.CreateShowtimeRequest;
import com.reserv_engine.booking.dto.response.ShowtimeResponse;
import com.reserv_engine.booking.entity.Showtime;
import com.reserv_engine.booking.service.ShowtimeService;
import com.reserv_engine.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events/{eventId}/showtimes")
public class ShowtimeController {

    private final ShowtimeService showtimeService;

    public ShowtimeController(ShowtimeService showtimeService) {
        this.showtimeService = showtimeService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<ShowtimeResponse> create(@PathVariable String eventId,
                                                   @Valid @RequestBody CreateShowtimeRequest request) {
        String currentUserId = SecurityUtils.currentUserId();
        Showtime showtime = showtimeService.create(
                eventId, currentUserId, request.hallId(), request.startTime(), request.endTime());
        return ResponseEntity.status(HttpStatus.CREATED).body(ShowtimeResponse.from(showtime));
    }
}