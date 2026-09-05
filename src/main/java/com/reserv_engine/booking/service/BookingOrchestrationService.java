package com.reserv_engine.booking.service;

import com.reserv_engine.booking.entity.SeatShowtimeAssignment;
import com.reserv_engine.booking.repository.SeatShowtimeAssignmentRepository;
import com.reserv_engine.dto.CreateHoldRequest;
import com.reserv_engine.dto.HoldLineRequest;
import com.reserv_engine.dto.HoldResponse;
import com.reserv_engine.exception.ResourceNotFoundException;
import com.reserv_engine.service.HoldService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;

/**
 * [Booking] The one place Booking actually reaches into the Engine's write
 * path. Translates a customer's chosen Seats for a Showtime into the
 * ResourceUnit ids the Engine already tracks, then delegates straight into
 * the existing, unmodified HoldService.createHold — no new concurrency
 * logic here. Every seat is UNIT_BASED (see TicketTierService), so each
 * line carries a resourceUnitId; quantity is left null, matching how
 * buildUnitLine ignores it for UNIT_BASED pools.
 */
@Service
public class BookingOrchestrationService {

    private final SeatShowtimeAssignmentRepository assignmentRepository;
    private final HoldService holdService;

    public BookingOrchestrationService(SeatShowtimeAssignmentRepository assignmentRepository,
                                       HoldService holdService) {
        this.assignmentRepository = assignmentRepository;
        this.holdService = holdService;
    }

    public HoldResponse bookSeats(String showtimeId, String currentUserId,
                                  List<String> seatIds, String idempotencyKey) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("seatIds must not be empty");
        }
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new IllegalArgumentException("seatIds contains duplicates");
        }

        List<HoldLineRequest> lines = seatIds.stream()
                .map(seatId -> resolveLine(seatId, showtimeId))
                .toList();

        CreateHoldRequest request = new CreateHoldRequest(currentUserId, idempotencyKey, lines);
        return holdService.createHold(request);
    }

    private HoldLineRequest resolveLine(String seatId, String showtimeId) {
        SeatShowtimeAssignment assignment = assignmentRepository
                .findBySeatIdAndShowtimeIdWithResourceUnitAndPool(seatId, showtimeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Seat " + seatId + " is not assigned to Showtime " + showtimeId));

        String resourcePoolId = assignment.getResourceUnit().getResourcePool().getId();
        String resourceUnitId = assignment.getResourceUnit().getId();

        return new HoldLineRequest(resourcePoolId, resourceUnitId, null);
    }
}