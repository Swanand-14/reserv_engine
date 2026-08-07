package com.reserv_engine.dto;

import java.time.LocalDateTime;
import java.util.List;

public record HoldResponse(
        String id,
        String status,
        LocalDateTime expiresAt,
        List<HoldLineResponse> lines
) {
}