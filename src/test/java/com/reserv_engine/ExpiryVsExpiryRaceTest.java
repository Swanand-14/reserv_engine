package com.reserv_engine;

import com.reserv_engine.service.HoldExpiryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Expiry-vs-expiry: two concurrent HoldExpiryService.releaseHold(holdId)
 * calls racing on the SAME already-expired Hold.
 *
 * Not contrived — HoldExpiryReaper.releaseExpiredHolds() explicitly
 * catches and logs a per-Hold failure with "will retry next run" instead
 * of aborting the batch. That's an admission the same Hold CAN legitimately
 * be handed to releaseHold more than once across retries or overlapping
 * runs. This proves that when two such calls genuinely overlap, the
 * resource is released EXACTLY once — never twice (double-release) and
 * never zero times (a Hold stuck ACTIVE forever, past its own expiresAt).
 *
 * Mechanism, per Hold's own @Version: neither call takes an upfront lock,
 * so both can read ACTIVE+expired before either commits. Whichever
 * commits first bumps version 0->1 and succeeds silently. The second
 * commit's UPDATE ... WHERE version=0 now matches zero rows, Hibernate
 * throws an optimistic-lock exception, and Spring rolls back that entire
 * transaction — INCLUDING the resource release it already performed in
 * memory. Same @Version safety net already proven for confirm-vs-cancel,
 * now proven for the reaper racing itself.
 *
 * Defense in depth worth noting, not directly exercised: even if @Version
 * somehow failed here, chk_pool_capacity_bounds (remaining_capacity <=
 * total_capacity) is a hard MySQL CHECK constraint — a genuine
 * double-release would fail at the SQL level regardless. This test's job
 * is to confirm it never even gets that far.
 */
class ExpiryVsExpiryRaceTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HoldExpiryService holdExpiryService;

    private static final int ITERATIONS = 20;
    private static final int TOTAL_CAPACITY = 10;
    private static final int STARTING_REMAINING = TOTAL_CAPACITY - 1; // 1 already "held"

    private record SeededExpiredHold(String poolId, String holdId) {}

    private SeededExpiredHold seedAlreadyExpiredHold() {
        String windowId = UUID.randomUUID().toString();
        String poolId = UUID.randomUUID().toString();
        String holdId = UUID.randomUUID().toString();
        String holdLineId = UUID.randomUUID().toString();

        jdbcTemplate.update("""
                INSERT INTO availability_window (id, owner_id, start_time, end_time, created_at)
                VALUES (?, 'test-owner', NOW(), NOW() + INTERVAL 1 DAY, NOW())
                """, windowId);

        jdbcTemplate.update("""
                INSERT INTO resource_pool
                    (id, availability_window_id, owner_id, pool_mode, total_capacity, remaining_capacity, version, created_at)
                VALUES (?, ?, 'test-owner', 'COUNTER_BASED', ?, ?, 0, NOW())
                """, poolId, windowId, TOTAL_CAPACITY, STARTING_REMAINING);

        // Java-computed, already in the past — same lesson as
        // ConfirmVsCancelRaceTest/ConfirmVsExpiryTest: MySQL's own NOW()
        // is on a different clock (Testcontainers UTC) from the JVM
        // (IST here) that isExpired() actually compares against.
        LocalDateTime createdAt = LocalDateTime.now().minusMinutes(10);
        LocalDateTime expiresAt = LocalDateTime.now().minusMinutes(1);

        jdbcTemplate.update("""
                INSERT INTO hold (id, holder_id, status, idempotency_key, created_at, expires_at, version)
                VALUES (?, 'test-holder', 'ACTIVE', ?, ?, ?, 0)
                """, holdId, "expiry-vs-expiry-" + holdId, createdAt, expiresAt);

        jdbcTemplate.update("""
                INSERT INTO hold_line (id, hold_id, resource_pool_id, resource_unit_id, quantity)
                VALUES (?, ?, ?, NULL, 1)
                """, holdLineId, holdId, poolId);

        return new SeededExpiredHold(poolId, holdId);
    }

    @Test
    void twoConcurrentReleases_onSameExpiredHold_exactlyOneWinsAndReleasesExactlyOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<String> violations = new ArrayList<>();
        int exactlyOneWinnerCount = 0;

        for (int i = 0; i < ITERATIONS; i++) {
            SeededExpiredHold hold = seedAlreadyExpiredHold();

            CountDownLatch readyLatch = new CountDownLatch(2);
            CountDownLatch startLatch = new CountDownLatch(1);

            Callable<Exception> releaseTask = () -> {
                readyLatch.countDown();
                startLatch.await();
                try {
                    holdExpiryService.releaseHold(hold.holdId());
                    return null; // clean success, no exception
                } catch (Exception ex) {
                    return ex;
                }
            };

            Future<Exception> f1 = executor.submit(releaseTask);
            Future<Exception> f2 = executor.submit(releaseTask);

            readyLatch.await();
            startLatch.countDown();

            Exception r1 = f1.get(10, TimeUnit.SECONDS);
            Exception r2 = f2.get(10, TimeUnit.SECONDS);
            boolean r1Clean = r1 == null;
            boolean r2Clean = r2 == null;

            String holdStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM hold WHERE id = ?", String.class, hold.holdId());
            Integer remainingCapacity = jdbcTemplate.queryForObject(
                    "SELECT remaining_capacity FROM resource_pool WHERE id = ?", Integer.class, hold.poolId());

            if (r1Clean && r2Clean) {
                violations.add("iter %d: BOTH releases completed cleanly — expected exactly one to fail on optimistic lock (remaining=%d)"
                        .formatted(i, remainingCapacity));
            } else if (!r1Clean && !r2Clean) {
                violations.add("iter %d: BOTH releases threw — expected exactly one clean winner (r1=%s, r2=%s)"
                        .formatted(i, r1, r2));
            } else {
                exactlyOneWinnerCount++;
                Exception loserException = r1Clean ? r2 : r1;
                String loserType = loserException.getClass().getSimpleName();
                if (!loserType.toLowerCase().contains("optimisticlock")) {
                    violations.add("iter %d: loser threw an unexpected exception type: %s (%s)"
                            .formatted(i, loserType, loserException.getMessage()));
                }
            }

            if (!"EXPIRED".equals(holdStatus)) {
                violations.add("iter %d: expected hold status EXPIRED, was %s".formatted(i, holdStatus));
            }
            if (!remainingCapacity.equals(TOTAL_CAPACITY)) {
                String direction = remainingCapacity > TOTAL_CAPACITY ? "DOUBLE-RELEASE" : "under-released";
                violations.add("iter %d: expected remaining_capacity=%d (released exactly once), was %d [%s]"
                        .formatted(i, TOTAL_CAPACITY, remainingCapacity, direction));
            }
        }

        executor.shutdown();

        System.out.printf("iterations=%d exactlyOneWinner=%d violations=%d%n",
                ITERATIONS, exactlyOneWinnerCount, violations.size());
        violations.forEach(System.out::println);

        assertThat(violations).as("iterations where the exactly-once-release invariant did not hold").isEmpty();
        assertThat(exactlyOneWinnerCount).as("iterations with exactly one clean winner").isEqualTo(ITERATIONS);
    }
}