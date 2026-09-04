package com.reserv_engine.booking.service;

import com.reserv_engine.booking.entity.*;
import com.reserv_engine.booking.repository.*;
import com.reserv_engine.core.domain.ResourceUnitStatus;
import com.reserv_engine.entity.ResourceUnit;
import com.reserv_engine.exception.ResourceConflictException;
import com.reserv_engine.exception.ResourceNotFoundException;
import com.reserv_engine.repository.ResourceUnitRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SeatShowtimeAssignmentService {

    private final SeatShowtimeAssignmentRepository assignmentRepository;
    private final TicketTierRepository ticketTierRepository;
    private final SeatRepository seatRepository;
    private final ResourceUnitRepository resourceUnitRepository;

    public SeatShowtimeAssignmentService(SeatShowtimeAssignmentRepository assignmentRepository,
                                         TicketTierRepository ticketTierRepository,
                                         SeatRepository seatRepository,
                                         ResourceUnitRepository resourceUnitRepository) {
        this.assignmentRepository = assignmentRepository;
        this.ticketTierRepository = ticketTierRepository;
        this.seatRepository = seatRepository;
        this.resourceUnitRepository = resourceUnitRepository;
    }

    @Transactional
    public List<SeatShowtimeAssignment> assignSeats(String tierId, String currentUserId, List<String> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("seatIds must not be empty");
        }
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new IllegalArgumentException("seatIds contains duplicates");
        }

        TicketTier tier = ticketTierRepository.findById(tierId)
                .orElseThrow(() -> new ResourceNotFoundException("TicketTier not found: " + tierId));

        Showtime showtime = tier.getShowtime();
        if (!showtime.getEvent().getOrganizer().getId().equals(currentUserId)) {
            throw new AccessDeniedException("You do not organize the Event for this Showtime");
        }

        String hallId = showtime.getHall().getId();

        // Load and validate every requested Seat belongs to this Showtime's Hall,
        // and isn't already assigned for this specific Showtime.
        List<Seat> seats = new ArrayList<>(seatIds.size());
        for (String seatId : seatIds) {
            Seat seat = seatRepository.findById(seatId)
                    .orElseThrow(() -> new ResourceNotFoundException("Seat not found: " + seatId));
            if (!seat.getHall().getId().equals(hallId)) {
                throw new IllegalArgumentException(
                        "Seat " + seatId + " does not belong to this Showtime's Hall");
            }
            if (assignmentRepository.findBySeatIdAndShowtimeId(seatId, showtime.getId()).isPresent()) {
                throw new ResourceConflictException(
                        "Seat " + seatId + " is already assigned for this Showtime");
            }
            seats.add(seat);
        }

        // Available ResourceUnits for this tier's pool = all units in the pool
        // minus any already claimed by a SeatShowtimeAssignment for this Showtime.
        List<ResourceUnit> allUnits = resourceUnitRepository.findByResourcePool_Id(tier.getResourcePool().getId());
        Set<String> alreadyAssignedUnitIds = assignmentRepository.findByShowtimeId(showtime.getId()).stream()
                .map(a -> a.getResourceUnit().getId())
                .collect(Collectors.toSet());
        List<ResourceUnit> availableUnits = allUnits.stream()
                .filter(u -> !alreadyAssignedUnitIds.contains(u.getId()))
                .filter(u -> u.getStatus() == ResourceUnitStatus.AVAILABLE)
                .toList();

        // All-or-nothing: this batch must exactly consume what's available.
        // Matches the Engine's own Hold/HoldLine all-or-nothing convention.
        if (seatIds.size() != availableUnits.size()) {
            throw new IllegalArgumentException(
                    "seatIds count (%d) must exactly match available ResourceUnit count (%d) for this tier"
                            .formatted(seatIds.size(), availableUnits.size()));
        }

        List<SeatShowtimeAssignment> assignments = new ArrayList<>(seats.size());
        for (int i = 0; i < seats.size(); i++) {
            assignments.add(new SeatShowtimeAssignment(seats.get(i), showtime, availableUnits.get(i)));
        }

        return assignmentRepository.saveAll(assignments);
    }
}