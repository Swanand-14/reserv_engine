package com.reserv_engine.dto;

import com.reserv_engine.core.domain.PoolMode;

public record ResourcePoolResponse(
        String id,
        String availabilityWindowId,
        String ownerId,
        PoolMode poolMode,
        int totalCapacity,
        int remainingCapacity
) {
    public static ResourcePoolResponse from(com.reserv_engine.entity.ResourcePool pool) {
        return new ResourcePoolResponse(
                pool.getId(), pool.getAvailabilityWindow().getId(), pool.getOwnerId(),
                pool.getPoolMode(), pool.getTotalCapacity(), pool.getRemainingCapacity()
        );
    }
}