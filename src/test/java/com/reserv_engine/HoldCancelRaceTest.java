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
 * Cancel-vs-cancel: two concurrent POST /api/v1/holds/{id}/cancel calls
 * racing on the SAME ACTIVE Hold. Direct sibling of ExpiryVsExpiryRaceTest,
 * but for the user-triggered path instead of the reaper — and worth
 * checking independently rather than assumed safe by analogy, since
 * DuplicateConfirmDiscoveryTest already proved once in this codebase that
 * an unlocked status pre-check (HoldCancelService.cancel has the exact
 * same "hold.getStatus() != ACTIVE -> ResourceConflictException" shape
 * ReservationWriteService had) can hide a second failure mode that a
 * structurally-similar-looking sibling test doesn't exercise.
 *
 * Two genuinely different mechanisms live inside this one service method,
 * so both get their own scenario rather than assuming one covers both:
 *  - COUNTER_BASED release goes through poolRepository.findByIdForUpdate
 *    (SELECT ... FOR UPDATE) — the loser BLOCKS on that lock until the
 *    winner's transaction commits, then proceeds, double-increments
 *    remaining_capacity in memory, and only THEN hits Hold's own
 *    @Version check at commit and rolls back (release included). Safety
 *    net is the same @Version mechanism, but reached via a different path
 *    than the unit-based case below.
 *  - UNIT_BASED release is a bare in-memory field flip
 *    (unit.setStatus(AVAILABLE)) with NO lock acquired anywhere before
 *    Hold's own @Version check at commit — both threads can race all the
 *    way to commit with zero serialization, structurally identical to
 *    ExpiryVsExpiryRaceTest's actual mechanism.
 *
 * Expected outcome either way, per HoldCancelService's own javadoc
 * reasoning: exactly one 200 OK, one 409 CONFLICT (ResourceConflictException
 * -> CONFLICT per GlobalExceptionHandler), Hold ends CANCELLED, resource
 * released exactly once — never twice, never zero times.
 */
class HoldCancelRaceTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final int ITERATIONS = 20;
    private static final int TOTAL_CAPACITY = 10;
    private static final int STARTING_REMAINING = TOTAL_CAPACITY - 1; // 1 already "held"
    private String testToken;
    private String testHolderId;
    @BeforeEach
    void setUpAuth() {
        String email = "holdcancelrace-" + UUID.randomUUID() + "@example.com";
        String password = "password123";
        signupAndLogin(email, password);
        testToken = login(email, password);
        testHolderId = currentUserId(testToken);
    }

    private record SeededCounterHold(String poolId, String holdId) {}
    private record SeededUnitHold(String unitId, String holdId) {}

    private String seedWindow() {
        String windowId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO availability_window (id, owner_id, start_time, end_time, created_at)
                VALUES (?, 'test-owner', NOW(), NOW() + INTERVAL 1 DAY, NOW())
                """, windowId);
        return windowId;
    }

    private SeededCounterHold seedActiveCounterBasedHold() {
        String windowId = seedWindow();
        String poolId = UUID.randomUUID().toString();
        String holdId = UUID.randomUUID().toString();
        String holdLineId = UUID.randomUUID().toString();

        jdbcTemplate.update("""
                INSERT INTO resource_pool
                    (id, availability_window_id, owner_id, pool_mode, total_capacity, remaining_capacity, version, created_at)
                VALUES (?, ?, 'test-owner', 'COUNTER_BASED', ?, ?, 0, NOW())
                """, poolId, windowId, TOTAL_CAPACITY, STARTING_REMAINING);

        // Java-computed, well in the future — same clock lesson as every
        // other seeded Hold in this suite (Testcontainers UTC vs JVM IST).
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

        jdbcTemplate.update("""
                INSERT INTO hold (id, holder_id, status, idempotency_key, created_at, expires_at, version)
                VALUES (?, ?, 'ACTIVE', ?, ?, ?, 0)
                """, holdId, testHolderId,"cancel-race-counter-" + holdId, createdAt, expiresAt);

        jdbcTemplate.update("""
                INSERT INTO hold_line (id, hold_id, resource_pool_id, resource_unit_id, quantity)
                VALUES (?, ?, ?, NULL, 1)
                """, holdLineId, holdId, poolId);

        return new SeededCounterHold(poolId, holdId);
    }

    private SeededUnitHold seedActiveUnitBasedHold() {
        String windowId = seedWindow();
        String poolId = UUID.randomUUID().toString();
        String unitId = UUID.randomUUID().toString();
        String holdId = UUID.randomUUID().toString();
        String holdLineId = UUID.randomUUID().toString();

        jdbcTemplate.update("""
                INSERT INTO resource_pool
                    (id, availability_window_id, owner_id, pool_mode, total_capacity, remaining_capacity, version, created_at)
                VALUES (?, ?, 'test-owner', 'UNIT_BASED', 1, 1, 0, NOW())
                """, poolId, windowId);

        // Unit starts HELD (not AVAILABLE) — it's already claimed by this
        // Hold, exactly the state cancel is meant to release it FROM.
        jdbcTemplate.update("""
                INSERT INTO resource_unit (id, resource_pool_id, status, version, created_at)
                VALUES (?, ?, 'HELD', 0, NOW())
                """, unitId, poolId);

        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

        jdbcTemplate.update("""
                INSERT INTO hold (id, holder_id, status, idempotency_key, created_at, expires_at, version)
                VALUES (?, ?, 'ACTIVE', ?, ?, ?, 0)
                """, holdId,testHolderId, "cancel-race-unit-" + holdId, createdAt, expiresAt);

        jdbcTemplate.update("""
                INSERT INTO hold_line (id, hold_id, resource_pool_id, resource_unit_id, quantity)
                VALUES (?, ?, ?, ?, 1)
                """, holdLineId, holdId, poolId, unitId);

        return new SeededUnitHold(unitId, holdId);
    }

    private ResponseEntity<String> callCancel(String holdId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(testToken);
        return restTemplate.postForEntity(
                baseUrl() + "/api/v1/holds/" + holdId + "/cancel",
                new HttpEntity<>(headers), String.class);
    }

    /** Runs two concurrent cancels on holdId, returns their statuses in submission order. */
    private HttpStatus[] raceTwoCancels(String holdId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<ResponseEntity<String>> f1 = executor.submit(() -> {
            readyLatch.countDown();
            startLatch.await();
            return callCancel(holdId);
        });
        Future<ResponseEntity<String>> f2 = executor.submit(() -> {
            readyLatch.countDown();
            startLatch.await();
            return callCancel(holdId);
        });

        readyLatch.await();
        startLatch.countDown();

        HttpStatus s1 = (HttpStatus) f1.get(10, TimeUnit.SECONDS).getStatusCode();
        HttpStatus s2 = (HttpStatus) f2.get(10, TimeUnit.SECONDS).getStatusCode();
        executor.shutdown();
        return new HttpStatus[]{s1, s2};
    }

    @Test
    void twoConcurrentCancels_onSameCounterBasedHold_exactlyOneWinsAndReleasesExactlyOnce() throws Exception {
        List<String> violations = new ArrayList<>();
        int exactlyOneWinnerCount = 0;

        for (int i = 0; i < ITERATIONS; i++) {
            SeededCounterHold hold = seedActiveCounterBasedHold();
            HttpStatus[] statuses = raceTwoCancels(hold.holdId());

            long okCount = List.of(statuses).stream().filter(s -> s == HttpStatus.OK).count();
            long conflictCount = List.of(statuses).stream().filter(s -> s == HttpStatus.CONFLICT).count();

            String holdStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM hold WHERE id = ?", String.class, hold.holdId());
            Integer remainingCapacity = jdbcTemplate.queryForObject(
                    "SELECT remaining_capacity FROM resource_pool WHERE id = ?", Integer.class, hold.poolId());

            if (okCount == 1 && conflictCount == 1) {
                exactlyOneWinnerCount++;
            } else {
                violations.add("iter %d: expected exactly one OK and one CONFLICT, got %s (holdStatus=%s, remaining=%d)"
                        .formatted(i, List.of(statuses), holdStatus, remainingCapacity));
            }

            if (!"CANCELLED".equals(holdStatus)) {
                violations.add("iter %d: expected hold status CANCELLED, was %s".formatted(i, holdStatus));
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
    void twoConcurrentCancels_onSameUnitBasedHold_exactlyOneWinsAndReleasesExactlyOnce() throws Exception {
        List<String> violations = new ArrayList<>();
        int exactlyOneWinnerCount = 0;

        for (int i = 0; i < ITERATIONS; i++) {
            SeededUnitHold hold = seedActiveUnitBasedHold();
            HttpStatus[] statuses = raceTwoCancels(hold.holdId());

            long okCount = List.of(statuses).stream().filter(s -> s == HttpStatus.OK).count();
            long conflictCount = List.of(statuses).stream().filter(s -> s == HttpStatus.CONFLICT).count();

            String holdStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM hold WHERE id = ?", String.class, hold.holdId());
            var unitRow = jdbcTemplate.queryForMap(
                    "SELECT status, version FROM resource_unit WHERE id = ?", hold.unitId());

            if (okCount == 1 && conflictCount == 1) {
                exactlyOneWinnerCount++;
            } else {
                violations.add("iter %d: expected exactly one OK and one CONFLICT, got %s (holdStatus=%s, unit=%s)"
                        .formatted(i, List.of(statuses), holdStatus, unitRow));
            }

            if (!"CANCELLED".equals(holdStatus)) {
                violations.add("iter %d: expected hold status CANCELLED, was %s".formatted(i, holdStatus));
            }
            if (!"AVAILABLE".equals(unitRow.get("status"))) {
                violations.add("iter %d: expected unit status AVAILABLE, was %s".formatted(i, unitRow.get("status")));
            }
            // Exactly one successful write to the unit expected — version
            // should have bumped by exactly 1 from its seeded 0.
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