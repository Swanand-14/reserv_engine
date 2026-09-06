package com.reserv_engine.booking.dto.response;

/** [Booking] Read-only browse projection — deliberately no relation traversal. */
public record EventBrowseResponse(String id, String title) {}