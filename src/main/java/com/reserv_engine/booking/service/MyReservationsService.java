package com.reserv_engine.booking.service;

import com.reserv_engine.booking.dto.response.MyReservationResponse;
import com.reserv_engine.booking.repository.MyReservationRow;
import com.reserv_engine.booking.repository.SeatShowtimeAssignmentRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MyReservationsService {

    private final SeatShowtimeAssignmentRepository seatShowtimeAssignmentRepository;

    public MyReservationsService(SeatShowtimeAssignmentRepository seatShowtimeAssignmentRepository) {
        this.seatShowtimeAssignmentRepository = seatShowtimeAssignmentRepository;
    }

    public List<MyReservationResponse> getMyReservations(String holderId) {
        List<MyReservationRow> rows = seatShowtimeAssignmentRepository.findMyReservationRows(holderId);

        // LinkedHashMap on purpose: preserves the query's confirmedAt DESC
        // ordering across reservations, while grouping each reservation's
        // seat rows (which arrive contiguous, but grouping doesn't rely on
        // that) together.
        Map<String, List<MyReservationRow>> byReservation = new LinkedHashMap<>();
        for (MyReservationRow row : rows) {
            byReservation.computeIfAbsent(row.getReservationId(), key -> new ArrayList<>()).add(row);
        }

        return byReservation.values().stream()
                .map(this::toResponse)
                .toList();
    }

    private MyReservationResponse toResponse(List<MyReservationRow> rowsForOneReservation) {
        MyReservationRow first = rowsForOneReservation.get(0);

        List<MyReservationResponse.SeatLine> seats = rowsForOneReservation.stream()
                .map(row -> new MyReservationResponse.SeatLine(row.getSeatLabel(), row.getTierName(), row.getLockedPrice()))
                .toList();

        BigDecimal totalPaid = rowsForOneReservation.stream()
                .map(MyReservationRow::getLockedPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new MyReservationResponse(first.getReservationId(), first.getReservationStatus(), first.getConfirmedAt(),
                first.getEventTitle(), first.getShowtimeId(), first.getStartTime(), first.getEndTime(),
                seats, totalPaid);
    }
}