package com.reserv_engine.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateShowtimeRequest(@NotBlank String hallId,
                                    @NotNull LocalDateTime startTime,
                                    @NotNull LocalDateTime endTime) {}