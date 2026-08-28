package com.reserv_engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cancel-vs-cancel for ReservationCancelService — the fast-follow flagged
 * after HoldCancelRaceTest. Same unlocked-status-check shape as
 * HoldCancelService.cancel (reservation.getStatus() != CONFIRMED ->
 * ResourceConflictException before any write), same two release
 * mechanisms (pessimistic findByIdForUpdate for counter-based, bare
 * in-memory field flip for unit-based), same @Version safety net on the
 * Reservation entity itself at commit.
 *
 * One real difference from HoldCancelRaceTest worth calling out: cancel
 * here does NOT touch the originating Hold at all (per
 * ReservationCancelService's own javadoc — Hold's CONSUMED status is
 * permanent once a Reservation exists). So the seeded Hold is expected to
 * stay CONSUMED throughout regardless of outcome — only the Reservation
 * and its named resources should move.
 *
 * Reservations are seeded directly as already-CONFIRMED (skipping the
 * confirm HTTP call entirely) since only the cancel race is under test
 * here — mirrors how HoldCancelRaceTest seeded ACTIVE Holds directly
 * rather than going through create.
 */
class ReservationCancelRaceTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final int ITERATIONS = 20;
    private static final int TOTAL_CAPACITY = 10;
    private static final int STARTING_REMAINING = TOTAL_CAPACITY - 1; // 1 already "reserved"
    private String testToken;
    private String testHolderId;
    @BeforeEach
    void setUpAuth() {
        String email = "reservationcancelrace-" + UUID.randomUUID() + "@example.com";
        String password = "password123";
        signupAndLogin(email, password);
        testToken = login(email, password);
        testHolderId = currentUserId(testToken);
    }

    private record SeededCounterReservation(String poolId, String reservationId) {}
    private record SeededUnitReservation(String unitId, String reservationId) {}

    private String seedWindow() {
        String windowId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO availability_window (id, owner_id, start_time, end_time, created_at)
                VALUES (?, 'test-owner', NOW(), NOW() + INTERVAL 1 DAY, NOW())
                """, windowId);
        return windowId;
    }

    /** Seeds an already-CONSUMED Hold — required since reservation.hold_id is a real FK,
     *  and CONSUMED is the only status a Hold can be in once a Reservation exists. */
    private String seedConsumedHold() {
        String holdId = UUID.randomUUID().toString();
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);
        jdbcTemplate.update("""
                INSERT INTO hold (id, holder_id, status, idempotency_key, created_at, expires_at, version)
                VALUES (?, ?, 'CONSUMED', ?, ?, ?, 0)
                """, holdId, testHolderId,"reservation-cancel-race-" + holdId, createdAt, expiresAt);
        return holdId;
    }

    private SeededCounterReservation seedConfirmedCounterBasedReservation() {
        String windowId = seedWindow();
        String poolId = UUID.randomUUID().toString();
        String holdId = seedConsumedHold();
        String reservationId = UUID.randomUUID().toString();
        String lineId = UUID.randomUUID().toString();

        jdbcTemplate.update("""
                INSERT INTO resource_pool
                    (id, availability_window_id, owner_id, pool_mode, total_capacity, remaining_capacity, version, created_at)
                VALUES (?, ?, 'test-owner', 'COUNTER_BASED', ?, ?, 0, NOW())
                """, poolId, windowId, TOTAL_CAPACITY, STARTING_REMAINING);

        jdbcTemplate.update("""
                INSERT INTO reservation (id, hold_id, holder_id, status, confirmed_at, version)
                VALUES (?, ?, ?, 'CONFIRMED', ?, 0)
                """, reservationId, holdId,testHolderId, LocalDateTime.now());

        jdbcTemplate.update("""
                INSERT INTO reservation_line (id, reservation_id, resource_pool_id, resource_unit_id, quantity, locked_price)
                VALUES (?, ?, ?, NULL, 1, 10.00)
                """, lineId, reservationId, poolId);

        return new SeededCounterReservation(poolId, reservationId);
    }

    private SeededUnitReservation seedConfirmedUnitBasedReservation() {
        String windowId = seedWindow();
        String poolId = UUID.randomUUID().toString();
        String unitId = UUID.randomUUID().toString();
        String holdId = seedConsumedHold();
        String reservationId = UUID.randomUUID().toString();
        String lineId = UUID.randomUUID().toString();

        jdbcTemplate.update("""
                INSERT INTO resource_pool
                    (id, availability_window_id, owner_id, pool_mode, total_capacity, remaining_capacity, version, created_at)
                VALUES (?, ?, 'test-owner', 'UNIT_BASED', 1, 1, 0, NOW())
                """, poolId, windowId);

        // Unit starts RESERVED (not HELD) — this is confirm's terminal
        // state for a unit, the state cancel is meant to release it FROM.
        jdbcTemplate.update("""
                INSERT INTO resource_unit (id, resource_pool_id, status, version, created_at)
                VALUES (?, ?, 'RESERVED', 0, NOW())
                """, unitId, poolId);

        jdbcTemplate.update("""
                INSERT INTO reservation (id, hold_id, holder_id, status, confirmed_at, version)
                VALUES (?, ?, ?, 'CONFIRMED', ?, 0)
                """, reservationId, holdId, testHolderId, LocalDateTime.now());

        jdbcTemplate.update("""
                INSERT INTO reservation_line (id, reservation_id, resource_pool_id, resource_unit_id, quantity, locked_price)
                VALUES (?, ?, ?, ?, 1, 10.00)
                """, lineId, reservationId, poolId, unitId);

        return new SeededUnitReservation(unitId, reservationId);
    }

    private ResponseEntity<String> callCancel(String reservationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(testToken);
        return restTemplate.postForEntity(
                baseUrl() + "/api/v1/reservations/" + reservationId + "/cancel",
                new HttpEntity<>(headers), String.class);
    }

    private HttpStatus[] raceTwoCancels(String reservationId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<ResponseEntity<String>> f1 = executor.submit(() -> {
            readyLatch.countDown();
            startLatch.await();
            return callCancel(reservationId);
        });
        Future<ResponseEntity<String>> f2 = executor.submit(() -> {
            readyLatch.countDown();
            startLatch.await();
            return callCancel(reservationId);
        });

        readyLatch.await();
        startLatch.countDown();

        HttpStatus s1 = (HttpStatus) f1.get(10, TimeUnit.SECONDS).getStatusCode();
        HttpStatus s2 = (HttpStatus) f2.get(10, TimeUnit.SECONDS).getStatusCode();
        executor.shutdown();
        return new HttpStatus[]{s1, s2};
    }

    @Test
    void twoConcurrentCancels_onSameCounterBasedReservation_exactlyOneWinsAndReleasesExactlyOnce() throws Exception {
        List<String> violations = new ArrayList<>();
        int exactlyOneWinnerCount = 0;

        for (int i = 0; i < ITERATIONS; i++) {
            SeededCounterReservation r = seedConfirmedCounterBasedReservation();
            HttpStatus[] statuses = raceTwoCancels(r.reservationId());

            long okCount = List.of(statuses).stream().filter(s -> s == HttpStatus.OK).count();
            long conflictCount = List.of(statuses).stream().filter(s -> s == HttpStatus.CONFLICT).count();

            String reservationStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM reservation WHERE id = ?", String.class, r.reservationId());
            Integer remainingCapacity = jdbcTemplate.queryForObject(
                    "SELECT remaining_capacity FROM resource_pool WHERE id = ?", Integer.class, r.poolId());

            if (okCount == 1 && conflictCount == 1) {
                exactlyOneWinnerCount++;
            } else {
                violations.add("iter %d: expected exactly one OK and one CONFLICT, got %s (reservationStatus=%s, remaining=%d)"
                        .formatted(i, List.of(statuses), reservationStatus, remainingCapacity));
            }

            if (!"CANCELLED".equals(reservationStatus)) {
                violations.add("iter %d: expected reservation status CANCELLED, was %s".formatted(i, reservationStatus));
            }
            if (!remainingCapacity.equals(TOTAL_CAPACITY)) {
                String direction = remainingCapacity > TOTAL_CAPACITY ? "DOUBLE-RELEASE" : "under-released";
                violations.add("iter %d: expected remaining_capacity=%d (released exactly once), was %d [%s]"
                        .formatted(i, TOTAL_CAPACITY, remainingCapacity, direction));
            }
        }

        System.out.printf("[counter-based] iterations=%d exactlyOneWinner=%d violations=%d%n",
                ITERATIONS, exactlyOneWinnerCount, violations.size());
        violations.forEach(System.out::println);

        assertThat(violations).as("iterations where the exactly-once-cancel invariant did not hold (counter-based)").isEmpty();
        assertThat(exactlyOneWinnerCount).as("iterations with exactly one OK / one CONFLICT").isEqualTo(ITERATIONS);
    }

    @Test
    void twoConcurrentCancels_onSameUnitBasedReservation_exactlyOneWinsAndReleasesExactlyOnce() throws Exception {
        List<String> violations = new ArrayList<>();
        int exactlyOneWinnerCount = 0;

        for (int i = 0; i < ITERATIONS; i++) {
            SeededUnitReservation r = seedConfirmedUnitBasedReservation();
            HttpStatus[] statuses = raceTwoCancels(r.reservationId());

            long okCount = List.of(statuses).stream().filter(s -> s == HttpStatus.OK).count();
            long conflictCount = List.of(statuses).stream().filter(s -> s == HttpStatus.CONFLICT).count();

            String reservationStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM reservation WHERE id = ?", String.class, r.reservationId());
            var unitRow = jdbcTemplate.queryForMap(
                    "SELECT status, version FROM resource_unit WHERE id = ?", r.unitId());

            if (okCount == 1 && conflictCount == 1) {
                exactlyOneWinnerCount++;
            } else {
                violations.add("iter %d: expected exactly one OK and one CONFLICT, got %s (reservationStatus=%s, unit=%s)"
                        .formatted(i, List.of(statuses), reservationStatus, unitRow));
            }

            if (!"CANCELLED".equals(reservationStatus)) {
                violations.add("iter %d: expected reservation status CANCELLED, was %s".formatted(i, reservationStatus));
            }
            if (!"AVAILABLE".equals(unitRow.get("status"))) {
                violations.add("iter %d: expected unit status AVAILABLE, was %s".formatted(i, unitRow.get("status")));
            }
            long unitVersion = ((Number) unitRow.get("version")).longValue();
            if (unitVersion != 1L) {
                violations.add("iter %d: expected unit version=1 after exactly one release, was %d"
                        .formatted(i, unitVersion));
            }
        }

        System.out.printf("[unit-based] iterations=%d exactlyOneWinner=%d violations=%d%n",
                ITERATIONS, exactlyOneWinnerCount, violations.size());
        violations.forEach(System.out::println);

        assertThat(violations).as("iterations where the exactly-once-cancel invariant did not hold (unit-based)").isEmpty();
        assertThat(exactlyOneWinnerCount).as("iterations with exactly one OK / one CONFLICT").isEqualTo(ITERATIONS);
    }
}