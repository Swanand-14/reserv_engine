package com.reserv_engine.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record CreateAvailabilityWindowRequest(
        @NotNull(message = "startTime is required")
        LocalDateTime startTime,

        @NotNull(message = "endTime is required")
        LocalDateTime endTime
) {}