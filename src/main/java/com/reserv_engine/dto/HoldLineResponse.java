package com.reserv_engine.dto;

/**
 * [Engine] id is the HoldLine's own identity — required by any caller that
 * later needs to build ConfirmReservationRequest.linePrices, which is
 * keyed by holdLineId. This was previously missing entirely, which meant
 * there was no way to confirm a Hold created through this response
 * without a second, separate lookup of the raw entity.
 */
public record HoldLineResponse(
        String id,
        String resourcePoolId,
        String resourceUnitId,
        int quantity
) {
}