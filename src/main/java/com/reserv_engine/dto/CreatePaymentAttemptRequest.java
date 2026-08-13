package com.reserv_engine.dto;

import java.math.BigDecimal;

/**
 * [Engine] Request to attempt payment against a Hold.
 *
 * simulateSuccess exists ONLY because there is no real payment gateway
 * wired in yet — it's how the caller (test client, or later a real
 * gateway-integration layer) tells this mock step what outcome to record.
 * A real integration would replace this with an actual gateway call and
 * derive the outcome from its response instead of trusting the caller.
 */
public record CreatePaymentAttemptRequest(
        String holdId,
        String idempotencyKey,
        BigDecimal amount,
        boolean simulateSuccess
) {
}