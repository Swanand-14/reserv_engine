package com.reserv_engine.booking.service;

import com.reserv_engine.booking.dto.response.BookingHoldResponse;
import com.reserv_engine.booking.entity.SeatShowtimeAssignment;
import com.reserv_engine.booking.entity.TicketTier;
import com.reserv_engine.booking.repository.SeatShowtimeAssignmentRepository;
import com.reserv_engine.booking.repository.TicketTierRepository;
import com.reserv_engine.dto.CreateHoldRequest;
import com.reserv_engine.dto.HoldLineRequest;
import com.reserv_engine.dto.HoldLineResponse;
import com.reserv_engine.dto.HoldResponse;
import com.reserv_engine.exception.ResourceNotFoundException;
import com.reserv_engine.service.HoldService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
public class BookingOrchestrationService {

    private final SeatShowtimeAssignmentRepository assignmentRepository;
    private final TicketTierRepository ticketTierRepository;
    private final HoldService holdService;

    public BookingOrchestrationService(SeatShowtimeAssignmentRepository assignmentRepository,
                                       TicketTierRepository ticketTierRepository,
                                       HoldService holdService) {
        this.assignmentRepository = assignmentRepository;
        this.ticketTierRepository = ticketTierRepository;
        this.holdService = holdService;
    }

    public BookingHoldResponse bookSeats(String showtimeId, String currentUserId,
                                         List<String> seatIds, String idempotencyKey) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("seatIds must not be empty");
        }
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new IllegalArgumentException("seatIds contains duplicates");
        }

        List<ResolvedLine> resolved = seatIds.stream()
                .map(seatId -> resolveLine(seatId, showtimeId))
                .toList();

        List<HoldLineRequest> lines = resolved.stream().map(ResolvedLine::holdLineRequest).toList();

        CreateHoldRequest request = new CreateHoldRequest(currentUserId, idempotencyKey, lines);
        HoldResponse holdResponse = holdService.createHold(request);

        return toBookingHoldResponse(holdResponse, resolved);
    }

    private BookingHoldResponse toBookingHoldResponse(HoldResponse holdResponse, List<ResolvedLine> resolved) {
        Map<String, ResolvedLine> byResourceUnitId = new HashMap<>();
        for (ResolvedLine r : resolved) {
            byResourceUnitId.put(r.resourceUnitId(), r);
        }

        BigDecimal totalPrice = BigDecimal.ZERO;
        List<BookingHoldResponse.Line> bookingLines = new ArrayList<>();
        for (HoldLineResponse line : holdResponse.lines()) {
            ResolvedLine r = byResourceUnitId.get(line.resourceUnitId());
            bookingLines.add(new BookingHoldResponse.Line(line.id(), r.seatLabel(), r.tierName(), r.price()));
            totalPrice = totalPrice.add(r.price());
        }

        return new BookingHoldResponse(holdResponse.id(), holdResponse.status(), holdResponse.expiresAt(),
                bookingLines, totalPrice);
    }

    private ResolvedLine resolveLine(String seatId, String showtimeId) {
        SeatShowtimeAssignment assignment = assignmentRepository
                .findBySeatIdAndShowtimeIdWithResourceUnitAndPool(seatId, showtimeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Seat " + seatId + " is not assigned to Showtime " + showtimeId));

        String resourcePoolId = assignment.getResourceUnit().getResourcePool().getId();
        String resourceUnitId = assignment.getResourceUnit().getId();

        TicketTier tier = ticketTierRepository.findByResourcePoolId(resourcePoolId)
                .orElseThrow(() -> new ResourceNotFoundException("TicketTier not found for pool: " + resourcePoolId));

        HoldLineRequest holdLineRequest = new HoldLineRequest(resourcePoolId, resourceUnitId, null);

        return new ResolvedLine(holdLineRequest, resourceUnitId, assignment.getSeat().getLabel(),
                tier.getName(), tier.getPrice());
    }

    private record ResolvedLine(HoldLineRequest holdLineRequest, String resourceUnitId,
                                String seatLabel, String tierName, BigDecimal price) {}
}