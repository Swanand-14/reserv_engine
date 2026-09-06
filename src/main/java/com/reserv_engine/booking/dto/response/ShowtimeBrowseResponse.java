package com.reserv_engine.booking.dto.response;

import java.time.LocalDateTime;

public record ShowtimeBrowseResponse(String id, String hallId, LocalDateTime startTime, LocalDateTime endTime) {}