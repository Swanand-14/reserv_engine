package com.reserv_engine.booking.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateHallRequest(@NotBlank String name) {}