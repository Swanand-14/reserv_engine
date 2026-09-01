package com.reserv_engine.booking.service;

import com.reserv_engine.booking.entity.Hall;
import com.reserv_engine.booking.entity.Seat;
import com.reserv_engine.booking.repository.HallRepository;
import com.reserv_engine.booking.repository.SeatRepository;
import com.reserv_engine.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatService {

    private final SeatRepository seatRepository;
    private final HallRepository hallRepository;

    public SeatService(SeatRepository seatRepository, HallRepository hallRepository) {
        this.seatRepository = seatRepository;
        this.hallRepository = hallRepository;
    }

    @Transactional
    public Seat create(String hallId, String currentUserId, String label) {
        Hall hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new ResourceNotFoundException("Hall not found: " + hallId));

        if (!hall.getVenue().getManager().getId().equals(currentUserId)) {
            throw new AccessDeniedException("You do not manage the Venue for this Hall");
        }

        return seatRepository.save(new Seat(hall, label));
    }
}