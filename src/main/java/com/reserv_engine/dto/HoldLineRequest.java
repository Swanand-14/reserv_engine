package com.reserv_engine.dto;

/**
 * One requested line in a checkout attempt.
 * For a UNIT_BASED pool: set resourceUnitId, leave quantity null.
 * For a COUNTER_BASED pool: set quantity, leave resourceUnitId null.
 */
public record HoldLineRequest(
        String resourcePoolId,
        String resourceUnitId,
        Integer quantity
) {
}