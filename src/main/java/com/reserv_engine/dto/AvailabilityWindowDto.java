package com.reserv_engine.dto;

import java.time.LocalDateTime;

/**
 * [Engine] Read-only projection for browsing — deliberately has no
 * relationship traversal, just the fields the browse endpoint needs.
 */
public record AvailabilityWindowDto(
        String id,
        LocalDateTime startTime,
        LocalDateTime endTime
) {
}