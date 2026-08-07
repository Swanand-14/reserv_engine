package com.reserv_engine.dto;

import java.util.List;

public record CreateHoldRequest(
        String holderId,
        String idempotencyKey,
        List<HoldLineRequest> lines
) {
}