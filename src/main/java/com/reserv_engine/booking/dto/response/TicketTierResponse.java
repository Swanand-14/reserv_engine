package com.reserv_engine.booking.dto.response;

import com.reserv_engine.booking.entity.TicketTier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TicketTierResponse(String id, String showtimeId, String resourcePoolId,
                                 String name, BigDecimal price, LocalDateTime createdAt) {
    public static TicketTierResponse from(TicketTier tier) {
        return new TicketTierResponse(tier.getId(), tier.getShowtime().getId(), tier.getResourcePool().getId(),
                tier.getName(), tier.getPrice(), tier.getCreatedAt());
    }
}