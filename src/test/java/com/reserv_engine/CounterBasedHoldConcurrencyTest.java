package com.reserv_engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct port of the manual bash/curl test proven earlier in this project:
 * "5 concurrent successes / 15 conflicts / 0 server errors for a pool with
 * capacity 5" (counter-based, pessimistic-locking path).
 *
 * The invariant under test is the COUNT of outcomes, not WHICH specific
 * requests win — exactly like reading `sort | uniq -c` output by eye. Which
 * of the 20 requests land in the winning 5 is legitimately nondeterministic
 * (OS thread scheduling); that a real MySQL, running the real
 * findByIdForUpdate pessimistic lock, always produces exactly 5/15/0 is
 * what's actually being proven here.
 */
class CounterBasedHoldConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String poolId;

    @BeforeEach
    void setUp() {
        String windowId = UUID.randomUUID().toString();
        poolId = UUID.randomUUID().toString();

        // Raw SQL rather than a repository, deliberately — this test only
        // needs a throwaway, known-clean pool to race against, not a full
        // entity graph. Fresh pool per test run means no manual cleanup and
        // no cross-test pollution, unlike the shared pool we were reusing
        // in manual curl testing.
        jdbcTemplate.update("""
                INSERT INTO availability_window (id, owner_id, start_time, end_time, created_at)
                VALUES (?, 'test-owner', NOW(), NOW() + INTERVAL 1 DAY, NOW())
                """, windowId);

        jdbcTemplate.update("""
                INSERT INTO resource_pool
                    (id, availability_window_id, owner_id, pool_mode, total_capacity, remaining_capacity, version, created_at)
                VALUES (?, ?, 'test-owner', 'COUNTER_BASED', 5, 5, 0, NOW())
                """, poolId, windowId);
    }

    @Test
    void twentyConcurrentRequests_exactlyFiveSucceed_restConflict_zeroServerErrors() throws InterruptedException {
        int totalRequests = 20;
        int capacity = 5;

        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
        // Two-latch pattern: readyLatch confirms every thread has reached
        // the starting line before ANY of them fire; startLatch then
        // releases all of them in the same instant. Without this, thread
        // pool scheduling staggers request timing and you lose the tight
        // simultaneous burst that actually exercises the race.
        CountDownLatch readyLatch = new CountDownLatch(totalRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        AtomicInteger created = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        AtomicInteger serverErrors = new AtomicInteger();
        AtomicInteger otherStatuses = new AtomicInteger();

        for (int i = 0; i < totalRequests; i++) {
            int idx = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();

                    String body = """
                            {
                              "holderId": "user-%d",
                              "idempotencyKey": "counter-race-%s-%d",
                              "lines": [{"resourcePoolId": "%s", "quantity": 1}]
                            }
                            """.formatted(idx, poolId, idx, poolId);

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);

                    ResponseEntity<String> response = restTemplate.postForEntity(
                            baseUrl() + "/api/v1/holds",
                            new HttpEntity<>(body, headers),
                            String.class);

                    if (response.getStatusCode() == HttpStatus.CREATED) {
                        created.incrementAndGet();
                    } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                        conflicts.incrementAndGet();
                    } else if (response.getStatusCode().is5xxServerError()) {
                        serverErrors.incrementAndGet();
                    } else {
                        otherStatuses.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Any transport-level failure counts as a server error
                    // for this test's purposes — same bar as your curl
                    // loops treating anything that wasn't 201/409 as a
                    // problem worth investigating.
                    serverErrors.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).as("all requests completed within timeout").isTrue();
        assertThat(created.get()).as("successful holds").isEqualTo(capacity);
        assertThat(conflicts.get()).as("rejected holds").isEqualTo(totalRequests - capacity);
        assertThat(serverErrors.get()).as("server errors").isZero();
        assertThat(otherStatuses.get()).as("unexpected status codes").isZero();

        // Same DB-state check you were doing by eye via SELECT — final
        // remaining_capacity must be exactly 0, not just "the response
        // codes looked right." This is what actually rules out silent
        // overselling that a status-code-only assertion could miss.
        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT remaining_capacity FROM resource_pool WHERE id = ?", Integer.class, poolId);
        assertThat(remaining).as("final remaining_capacity").isZero();
    }

}