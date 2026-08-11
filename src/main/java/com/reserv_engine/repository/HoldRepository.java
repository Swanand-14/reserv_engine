package com.reserv_engine.repository;

import com.reserv_engine.core.domain.HoldStatus;
import com.reserv_engine.entity.Hold;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
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

    @Query("""
            SELECT h FROM Hold h
            LEFT JOIN FETCH h.holdLines hl
            LEFT JOIN FETCH hl.resourcePool
            LEFT JOIN FETCH hl.resourceUnit
            WHERE h.id = :id
            """)
    Optional<Hold> findByIdWithLines(@Param("id") String id);

    @Query("SELECT h FROM Hold h WHERE h.status = :status AND h.expiresAt < :now")
    List<Hold> findExpiredHolds(
            @Param("status") HoldStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);

}