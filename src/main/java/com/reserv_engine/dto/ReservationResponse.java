package com.reserv_engine.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ReservationResponse(
        String id,
        String holdId,
        String status,
        LocalDateTime confirmedAt,
        List<ReservationLineResponse> lines
) {
    public record ReservationLineResponse(
            String resourcePoolId,
            String resourceUnitId,
            int quantity,
            BigDecimal lockedPrice
    ) {
    }
}