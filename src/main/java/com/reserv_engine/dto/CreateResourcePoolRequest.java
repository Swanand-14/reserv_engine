package com.reserv_engine.dto;

import com.reserv_engine.core.domain.PoolMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateResourcePoolRequest(
        @NotNull(message = "poolMode is required")
        PoolMode poolMode,

        @Min(value = 1, message = "totalCapacity must be at least 1")
        int totalCapacity
) {}