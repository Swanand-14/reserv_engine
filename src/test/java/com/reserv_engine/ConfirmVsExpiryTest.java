package com.reserv_engine;

import com.reserv_engine.service.HoldExpiryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirm-vs-expiry: NOT a fair race like confirm-vs-cancel. Once a Hold
 * is genuinely past expiresAt, ReservationService.confirm's OWN
 * hold.isExpired() re-check is self-contained — it does not depend on
 * HoldExpiryReaper/HoldExpiryService.releaseHold having run yet. So there
 * is exactly one correct outcome, not two: every confirm attempt against
 * an already-expired Hold MUST get 409, regardless of whether the reaper
 * happens to fire before, during, or after those attempts.
 *
 * What this test actually proves, then, isn't "who wins" — it's that this
 * defensive re-check holds up under genuine concurrent HTTP load hitting
 * the same row the reaper is simultaneously mutating, not just when
 * called one at a time in isolation. HoldExpiryService.releaseHold is
 * called directly (not via the real 30s @Scheduled tick) — same
 * production code path, just triggered explicitly so the test doesn't
 * depend on wall-clock scheduler timing.
 */
class ConfirmVsExpiryTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HoldExpiryService holdExpiryService;

    private static final int ITERATIONS = 10;
    private static final int CONCURRENT_CONFIRMS_PER_ITERATION = 10;
    private static final int TOTAL_CAPACITY = 10;
    private static final int STARTING_REMAINING = TOTAL_CAPACITY - 1; // 1 already "held"

    private record SeededHold(String poolId, String holdId, String holdLineId) {}

    private SeededHold seedAlreadyExpiredHold() {
        String windowId = UUID.randomUUID().toString();
        String poolId = UUID.randomUUID().toString();
        String holdId = UUID.randomUUID().toString();
        String holdLineId = UUID.randomUUID().toString();
        String paymentId = UUID.randomUUID().toString();

        jdbcTemplate.update("""
                INSERT INTO availability_window (id, owner_id, start_time, end_time, created_at)
                VALUES (?, 'test-owner', NOW(), NOW() + INTERVAL 1 DAY, NOW())
                """, windowId);

        jdbcTemplate.update("""
                INSERT INTO resource_pool
                    (id, availability_window_id, owner_id, pool_mode, total_capacity, remaining_capacity, version, created_at)
                VALUES (?, ?, 'test-owner', 'COUNTER_BASED', ?, ?, 0, NOW())
                """, poolId, windowId, TOTAL_CAPACITY, STARTING_REMAINING);

        // createdAt/expiresAt both Java-computed and already in the past —
        // learned the hard way (ConfirmVsCancelRaceTest) that MySQL's own
        // NOW() is on a different clock (Testcontainers UTC vs this JVM's
        // IST) from whatever LocalDateTime.now() the app will later
        // compare against. Both ends of this comparison must come from
        // the same clock.
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(10);
        LocalDateTime expiresAt = LocalDateTime.now().minusMinutes(1); // 1 minute in the past

        jdbcTemplate.update("""
                INSERT INTO hold (id, holder_id, status, idempotency_key, created_at, expires_at, version)
                VALUES (?, 'test-holder', 'ACTIVE', ?, ?, ?, 0)
                """, holdId, "confirm-expiry-race-" + holdId, createdAt, expiresAt);

        jdbcTemplate.update("""
                INSERT INTO hold_line (id, hold_id, resource_pool_id, resource_unit_id, quantity)
                VALUES (?, ?, ?, NULL, 1)
                """, holdLineId, holdId, poolId);

        jdbcTemplate.update("""
                INSERT INTO payment_attempt (id, hold_id, idempotency_key, status, amount, created_at, version)
                VALUES (?, ?, ?, 'SUCCESS', 10.00, NOW(), 0)
                """, paymentId, holdId, "payment-" + holdId);

        return new SeededHold(poolId, holdId, holdLineId);
    }

    private ResponseEntity<String> callConfirm(SeededHold hold) {
        String body = """
                {
                  "holdId": "%s",
                  "linePrices": [{"holdLineId": "%s", "price": 10.00}]
                }
                """.formatted(hold.holdId(), hold.holdLineId());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(
                baseUrl() + "/api/v1/reservations/confirm",
                new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void confirmAgainstAlreadyExpiredHold_alwaysRejected_evenUnderConcurrentReaperExecution() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_CONFIRMS_PER_ITERATION + 1);
        List<String> violations = new ArrayList<>();

        for (int i = 0; i < ITERATIONS; i++) {
            SeededHold hold = seedAlreadyExpiredHold();

            int participants = CONCURRENT_CONFIRMS_PER_ITERATION + 1; // +1 for the reaper call
            CountDownLatch readyLatch = new CountDownLatch(participants);
            CountDownLatch startLatch = new CountDownLatch(1);

            List<Future<HttpStatus>> confirmFutures = new ArrayList<>();
            for (int c = 0; c < CONCURRENT_CONFIRMS_PER_ITERATION; c++) {
                Callable<HttpStatus> task = () -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return (HttpStatus) callConfirm(hold).getStatusCode();
                };
                confirmFutures.add(executor.submit(task));
            }

            Future<Exception> reaperFuture = executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                try {
                    holdExpiryService.releaseHold(hold.holdId());
                    return null;
                } catch (Exception ex) {
                    return ex;
                }
            });

            readyLatch.await();
            startLatch.countDown();

            List<HttpStatus> confirmStatuses = new ArrayList<>();
            for (Future<HttpStatus> f : confirmFutures) {
                confirmStatuses.add(f.get(10, TimeUnit.SECONDS));
            }
            Exception reaperException = reaperFuture.get(10, TimeUnit.SECONDS);

            long non409Count = confirmStatuses.stream().filter(s -> s != HttpStatus.CONFLICT).count();
            if (non409Count > 0) {
                violations.add("iter %d: %d of %d confirms did NOT get 409 (statuses=%s)"
                        .formatted(i, non409Count, CONCURRENT_CONFIRMS_PER_ITERATION, confirmStatuses));
            }
            if (reaperException != null) {
                violations.add("iter %d: releaseHold threw: %s".formatted(i, reaperException));
            }

            String holdStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM hold WHERE id = ?", String.class, hold.holdId());
            Integer reservationCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM reservation WHERE hold_id = ?", Integer.class, hold.holdId());
            Integer remainingCapacity = jdbcTemplate.queryForObject(
                    "SELECT remaining_capacity FROM resource_pool WHERE id = ?", Integer.class, hold.poolId());

            if (!"EXPIRED".equals(holdStatus)) {
                violations.add("iter %d: expected hold status EXPIRED, was %s".formatted(i, holdStatus));
            }
            if (reservationCount != 0) {
                violations.add("iter %d: expected 0 Reservation rows, found %d".formatted(i, reservationCount));
            }
            if (!remainingCapacity.equals(TOTAL_CAPACITY)) {
                violations.add("iter %d: expected remaining_capacity=%d (released back), was %d"
                        .formatted(i, TOTAL_CAPACITY, remainingCapacity));
            }
        }

        executor.shutdown();

        System.out.printf("iterations=%d violations=%d%n", ITERATIONS, violations.size());
        violations.forEach(System.out::println);

        assertThat(violations).as("iterations where the expired-Hold invariant did not hold").isEmpty();
    }
}