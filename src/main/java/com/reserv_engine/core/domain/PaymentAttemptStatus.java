package com.reserv_engine.core.domain;

/**
 * Lifecycle state of a single try to pay for a Hold.
 *
 * PENDING -> attempt created, outcome not yet resolved
 * SUCCESS -> terminal; at most one PaymentAttempt per Hold may reach this
 * FAILED  -> terminal; the Hold stays ACTIVE, a new PaymentAttempt may be
 *            created against it (retry) as long as the Hold hasn't expired
 */
public enum PaymentAttemptStatus {
    PENDING,
    SUCCESS,
    FAILED
}