package com.reserv_engine.dto;

import com.reserv_engine.core.domain.Role;
import jakarta.validation.constraints.NotNull;

public record GrantRoleRequest(
        @NotNull(message = "role is required")
        Role role
) {}