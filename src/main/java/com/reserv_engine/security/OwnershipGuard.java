package com.reserv_engine.security;

import com.reserv_engine.entity.Hold;
import com.reserv_engine.entity.Reservation;
import com.reserv_engine.exception.ResourceNotFoundException;
import com.reserv_engine.repository.HoldRepository;
import com.reserv_engine.repository.ReservationRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class OwnershipGuard {

    private final HoldRepository holdRepository;
    private final ReservationRepository reservationRepository;

    public OwnershipGuard(HoldRepository holdRepository, ReservationRepository reservationRepository) {
        this.holdRepository = holdRepository;
        this.reservationRepository = reservationRepository;
    }

    public void assertOwnsHold(String holdId, String currentUserId) {
        Hold hold = holdRepository.findById(holdId)
                .orElseThrow(() -> new ResourceNotFoundException("Hold not found: " + holdId));
        if (!hold.getHolderId().equals(currentUserId)) {
            throw new AccessDeniedException("You do not own this Hold");
        }
    }

    public void assertOwnsReservation(String reservationId, String currentUserId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));
        if (!reservation.getHolderId().equals(currentUserId)) {
            throw new AccessDeniedException("You do not own this Reservation");
        }
    }
}