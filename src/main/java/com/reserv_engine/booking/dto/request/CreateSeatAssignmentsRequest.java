package com.reserv_engine.booking.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateSeatAssignmentsRequest(@NotEmpty List<String> seatIds) {}