package com.reserv_engine.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateBookingRequest(@NotEmpty List<String> seatIds, @NotBlank String idempotencyKey) {}