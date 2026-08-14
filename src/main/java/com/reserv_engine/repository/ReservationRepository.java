package com.reserv_engine.repository;

import com.reserv_engine.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

    // Idempotency check for confirm: a retried request against the same
    // Hold resolves to the already-created Reservation instead of trying
    // to create a duplicate. Mirrors HoldRepository.findByIdempotencyKey.
    Optional<Reservation> findByHoldId(String holdId);

    // Same JOIN FETCH shape as HoldRepository.findByIdempotencyKeyWithLines
    // — used for the response returned on the idempotent-retry path, so the
    // lines can be read without a lazy-loading exception outside the
    // transaction.
    @Query("""
            SELECT r FROM Reservation r
            LEFT JOIN FETCH r.reservationLines rl
            LEFT JOIN FETCH rl.resourcePool
            LEFT JOIN FETCH rl.resourceUnit
            WHERE r.holdId = :holdId
            """)
    Optional<Reservation> findByHoldIdWithLines(@Param("holdId") String holdId);
    @Query("""
            SELECT r FROM Reservation r
            LEFT JOIN FETCH r.reservationLines rl
            LEFT JOIN FETCH rl.resourcePool
            LEFT JOIN FETCH rl.resourceUnit
            WHERE r.id = :id
            """)
    Optional<Reservation> findByIdWithLines(@Param("id") String id);

}