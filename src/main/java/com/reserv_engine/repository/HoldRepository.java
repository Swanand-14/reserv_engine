package com.reserv_engine.repository;

import com.reserv_engine.entity.Hold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface HoldRepository extends JpaRepository<Hold, String> {

    // Idempotency check: a retried request with the same key resolves to the
    // existing Hold instead of creating a duplicate.
    Optional<Hold> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            SELECT h FROM Hold h
            LEFT JOIN FETCH h.holdLines hl
            LEFT JOIN FETCH hl.resourcePool
            LEFT JOIN FETCH hl.resourceUnit
            WHERE h.idempotencyKey = :key
            """)
    Optional<Hold> findByIdempotencyKeyWithLines(@Param("key") String idempotencyKey);
}