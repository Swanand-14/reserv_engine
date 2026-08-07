package com.reserv_engine.repository;

import com.reserv_engine.entity.Hold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HoldRepository extends JpaRepository<Hold, String> {

    // Idempotency check: a retried request with the same key resolves to the
    // existing Hold instead of creating a duplicate.
    Optional<Hold> findByIdempotencyKey(String idempotencyKey);
}