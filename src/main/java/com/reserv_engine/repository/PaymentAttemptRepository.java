package com.reserv_engine.repository;

import com.reserv_engine.core.domain.PaymentAttemptStatus;
import com.reserv_engine.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, String> {

    // Idempotency check: a retried request with the same key resolves to
    // the existing PaymentAttempt instead of creating a duplicate. Same
    // pattern as HoldRepository.findByIdempotencyKey.
    Optional<PaymentAttempt> findByIdempotencyKey(String idempotencyKey);

    // Used to enforce "at most one SUCCESS per Hold" before creating or
    // resolving a new attempt against that Hold.
    Optional<PaymentAttempt> findByHoldIdAndStatus(String holdId, PaymentAttemptStatus status);

}