package com.reserv_engine.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentAttemptResponse(
        String id,
        String holdId,
        String status,
        BigDecimal amount,
        LocalDateTime createdAt
) {
}