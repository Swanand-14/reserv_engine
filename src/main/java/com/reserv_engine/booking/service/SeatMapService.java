package com.reserv_engine.booking.service;

import com.reserv_engine.booking.dto.response.SeatMapEntryResponse;
import com.reserv_engine.booking.types.EventLifecycleStatus;
import com.reserv_engine.booking.entity.Showtime;
import com.reserv_engine.booking.repository.SeatRepository;
import com.reserv_engine.booking.repository.ShowtimeRepository;
import com.reserv_engine.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SeatMapService {

    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;

    public SeatMapService(ShowtimeRepository showtimeRepository, SeatRepository seatRepository) {
        this.showtimeRepository = showtimeRepository;
        this.seatRepository = seatRepository;
    }

    public List<SeatMapEntryResponse> getSeatMap(String showtimeId) {
        Showtime showtime = showtimeRepository.findByIdWithEvent(showtimeId)
                .orElseThrow(() -> new ResourceNotFoundException("Showtime not found: " + showtimeId));

        if (showtime.getEvent().getLifecycleStatus() != EventLifecycleStatus.PUBLISHED) {
            throw new ResourceNotFoundException("Showtime not found: " + showtimeId);
        }

        // hall.getId() on a lazy proxy resolves without a DB hit — Hibernate
        // can always answer the identifier without initializing the proxy.
        String hallId = showtime.getHall().getId();

        return seatRepository.findSeatMapByHallIdAndShowtimeId(hallId, showtimeId).stream()
                .map(SeatMapEntryResponse::from)
                .toList();
    }
}