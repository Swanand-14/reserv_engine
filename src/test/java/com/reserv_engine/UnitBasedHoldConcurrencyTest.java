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
 * Direct port of the manual curl test proven earlier: "exactly 1 winner /
 * 19 conflicts / 0 server errors" when 20 concurrent requests target the
 * SAME single AVAILABLE ResourceUnit (optimistic-locking path).
 *
 * Unlike the counter-based test, there is no SELECT ... FOR UPDATE forcing
 * threads to queue up one at a time. All 20 threads can genuinely read the
 * unit as AVAILABLE/version=0 simultaneously. What decides the winner is
 * the UPDATE ... WHERE id = ? AND version = ? at commit time: the first
 * commit to land mutates the row and bumps version to 1; every other
 * thread's UPDATE now matches zero rows because its WHERE clause still
 * expects version=0, Hibernate raises OptimisticLockException, and that's
 * what should surface as this test's 409s. This test is proving that
 * failure-DETECTION-on-write is correct under real contention, not that
 * access was serialized — a materially different mechanism from the
 * pessimistic-lock test even though the JUnit scaffolding is identical.
 */
class UnitBasedHoldConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String poolId;
    private String unitId;
    private String testToken;

    @BeforeEach
    void setUp() {
        String email = "unitrace-" + UUID.randomUUID() + "@example.com";
        String password = "password123";
        signupAndLogin(email, password);
        testToken = login(email, password);
        String windowId = UUID.randomUUID().toString();
        poolId = UUID.randomUUID().toString();
        unitId = UUID.randomUUID().toString();

        jdbcTemplate.update("""
                INSERT INTO availability_window (id, owner_id, start_time, end_time, created_at)
                VALUES (?, 'test-owner', NOW(), NOW() + INTERVAL 1 DAY, NOW())
                """, windowId);

        // total_capacity/remaining_capacity are irrelevant for UNIT_BASED
        // pools in this schema, but the columns are NOT NULL — 1/1 is a
        // harmless placeholder, not something the unit-based code path
        // reads or mutates.
        jdbcTemplate.update("""
                INSERT INTO resource_pool
                    (id, availability_window_id, owner_id, pool_mode, total_capacity, remaining_capacity, version, created_at)
                VALUES (?, ?, 'test-owner', 'UNIT_BASED', 1, 1, 0, NOW())
                """, poolId, windowId);

        // Exactly ONE available unit — this is the entire point. 20
        // threads are about to race for this single row.
        jdbcTemplate.update("""
                INSERT INTO resource_unit (id, resource_pool_id, status, version, created_at)
                VALUES (?, ?, 'AVAILABLE', 0, NOW())
                """, unitId, poolId);
    }

    @Test
    void twentyConcurrentRequests_exactlyOneSucceeds_restConflict_zeroServerErrors() throws InterruptedException {
        int totalRequests = 20;

        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
        CountDownLatch readyLatch = new CountDownLatch(totalRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        AtomicInteger created = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        AtomicInteger serverErrors = new AtomicInteger();
        AtomicInteger otherStatuses = new AtomicInteger();
        // Diagnostic only — captures one example response that wasn't a
        // clean 201/409, so a failure tells us WHAT actually came back
        // (400? 500 with a stack trace in the body? something else?)
        // instead of just a bare status-code tally.
        java.util.concurrent.atomic.AtomicReference<String> sampleUnexpectedResponse = new java.util.concurrent.atomic.AtomicReference<>();

        for (int i = 0; i < totalRequests; i++) {
            int idx = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();

                    String body = """
        {
          "holderId": "user-%d",
          "idempotencyKey": "unit-race-%s-%d",
          "lines": [{"resourcePoolId": "%s", "resourceUnitId": "%s", "quantity": 1}]
        }
        """.formatted(idx, poolId, idx, poolId, unitId);

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setBearerAuth(testToken);

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
                        sampleUnexpectedResponse.compareAndSet(null,
                                response.getStatusCode() + " :: " + response.getBody());
                    } else {
                        otherStatuses.incrementAndGet();
                        sampleUnexpectedResponse.compareAndSet(null,
                                response.getStatusCode() + " :: " + response.getBody());
                    }
                } catch (Exception e) {
                    serverErrors.incrementAndGet();
                    sampleUnexpectedResponse.compareAndSet(null,
                            "EXCEPTION :: " + e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // Print unconditionally, pass or fail — this is the breakdown the
        // first run's failure hid from us, since assertThat stops at the
        // first mismatch instead of showing all four counts together.
        System.out.printf(
                "created=%d conflicts=%d serverErrors=%d otherStatuses=%d%n",
                created.get(), conflicts.get(), serverErrors.get(), otherStatuses.get());
        if (sampleUnexpectedResponse.get() != null) {
            System.out.println("Sample unexpected response: " + sampleUnexpectedResponse.get());
        }

        var row = jdbcTemplate.queryForMap(
                "SELECT status, version FROM resource_unit WHERE id = ?", unitId);
        System.out.println("Final resource_unit row: " + row);

        assertThat(finished).as("all requests completed within timeout").isTrue();

        org.assertj.core.api.SoftAssertions softly = new org.assertj.core.api.SoftAssertions();
        softly.assertThat(created.get()).as("successful holds").isEqualTo(1);
        softly.assertThat(conflicts.get()).as("rejected holds").isEqualTo(totalRequests - 1);
        softly.assertThat(serverErrors.get()).as("server errors").isZero();
        softly.assertThat(otherStatuses.get()).as("unexpected status codes").isZero();
        softly.assertThat(row.get("status")).as("final unit status").isEqualTo("HELD");
        softly.assertThat(((Number) row.get("version")).longValue()).as("version after one successful write").isEqualTo(1L);
        softly.assertAll();
    }
}