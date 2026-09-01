package com.reserv_engine.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateTicketTierRequest(@NotBlank String name,
                                      @Positive BigDecimal price,
                                      @Positive int totalCapacity) {}