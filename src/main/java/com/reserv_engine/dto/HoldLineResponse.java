package com.reserv_engine.dto;

public record HoldLineResponse(
        String resourcePoolId,
        String resourceUnitId,
        int quantity
) {
}