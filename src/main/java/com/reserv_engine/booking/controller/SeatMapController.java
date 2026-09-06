package com.reserv_engine.booking.controller;

import com.reserv_engine.booking.dto.response.SeatMapEntryResponse;
import com.reserv_engine.booking.service.SeatMapService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/showtimes/{showtimeId}/seat-map")
public class SeatMapController {

    private final SeatMapService seatMapService;

    public SeatMapController(SeatMapService seatMapService) {
        this.seatMapService = seatMapService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<SeatMapEntryResponse> getSeatMap(@PathVariable String showtimeId) {
        return seatMapService.getSeatMap(showtimeId);
    }
}