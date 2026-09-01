package com.reserv_engine.booking.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateSeatRequest(@NotBlank String label) {}