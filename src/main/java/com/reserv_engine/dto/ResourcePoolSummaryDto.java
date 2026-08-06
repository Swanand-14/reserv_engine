package com.reserv_engine.dto;

public record ResourcePoolSummaryDto(
        String id,
        int remainingCapacity
) {
}