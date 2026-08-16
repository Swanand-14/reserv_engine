package com.reserv_engine;

import tools.jackson.databind.json.JsonMapper;
import com.reserv_engine.dto.HoldResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Port of the manual idempotency-race curl testing: N concurrent requests,
 * SAME idempotencyKey, must resolve to exactly one logical Hold.
 *
 * Unlike the counter-based and unit-based tests, this is NOT a 201/409
 * split. Tracing HoldController -> HoldService -> HoldWriteService:
 * HoldController is unconditionally @ResponseStatus(CREATED) — there is no
 * branch that returns 409. Every one of the N racing requests is expected
 * to come back 201, via one of two paths:
 *   - the pre-check in HoldService.createHold finds the winner's Hold
 *     already committed (fast path), or
 *   - attemptCreate() throws DataIntegrityViolationException on the
 *     uq_hold_idempotency_key unique constraint, is caught, and recovers
 *     by re-querying (slow path) — per the REPEATABLE READ fix described
 *     in HoldService's javadoc.
 * Either path returns 201 with the SAME underlying Hold id. So the actual
 * invariant under test is: (a) every response is 201, (b) every response
 * body carries the identical Hold id, and (c) exactly one row exists in
 * `hold` for this idempotency key despite N concurrent writers.
 *
 * A second invariant rides along for free: because attemptCreate() runs
 * under REQUIRES_NEW and the class javadoc is explicit that a losing
 * transaction's DataIntegrityViolationException rolls back EVERYTHING it
 * did — including the in-memory pool.remainingCapacity() decrement, not
 * yet committed — the pool's capacity should be decremented exactly ONCE,
 * not N times. That's what the final remaining_capacity assertion proves:
 * no double-counting from the 19 transactions that raced and lost.
 */
class IdempotencyRaceHoldConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper objectMapper;

    private String poolId;
    private static final int STARTING_CAPACITY = 100;

    @BeforeEach
    void setUp() {
        String windowId = UUID.randomUUID().toString();
        poolId = UUID.randomUUID().toString();

        jdbcTemplate.update("""
                INSERT INTO availability_window (id, owner_id, start_time, end_time, created_at)
                VALUES (?, 'test-owner', NOW(), NOW() + INTERVAL 1 DAY, NOW())
                """, windowId);

        // Capacity deliberately far above the request count (20) — this
        // test is isolating the idempotency-key race, not the capacity
        // race already proven by CounterBasedHoldConcurrencyTest. If
        // capacity were tight, a 409 here could mean either "duplicate
        // key" or "pool full," and we'd lose the ability to tell them
        // apart from the status code alone.
        jdbcTemplate.update("""
                INSERT INTO resource_pool
                    (id, availability_window_id, owner_id, pool_mode, total_capacity, remaining_capacity, version, created_at)
                VALUES (?, ?, 'test-owner', 'COUNTER_BASED', ?, ?, 0, NOW())
                """, poolId, windowId, STARTING_CAPACITY, STARTING_CAPACITY);
    }

    @Test
    void twentyConcurrentRequests_sameIdempotencyKey_resolveToOneHold() throws InterruptedException {
        int totalRequests = 20;
        String sharedIdempotencyKey = "idempotency-race-" + UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
        CountDownLatch readyLatch = new CountDownLatch(totalRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        AtomicInteger created = new AtomicInteger();
        AtomicInteger unexpectedStatuses = new AtomicInteger();
        AtomicReference<String> sampleUnexpectedResponse = new AtomicReference<>();
        Set<String> distinctHoldIds = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < totalRequests; i++) {
            int idx = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();

                    // Same idempotencyKey on every request — that's the
                    // entire point of the race. Different holderId/quantity
                    // don't matter here since only the FIRST committed
                    // Hold survives; every recovery path returns that
                    // same row regardless of what a losing request asked for.
                    String body = """
                            {
                              "holderId": "user-%d",
                              "idempotencyKey": "%s",
                              "lines": [{"resourcePoolId": "%s", "quantity": 1}]
                            }
                            """.formatted(idx, sharedIdempotencyKey, poolId);

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);

                    ResponseEntity<String> response = restTemplate.postForEntity(
                            baseUrl() + "/api/v1/holds",
                            new HttpEntity<>(body, headers),
                            String.class);

                    if (response.getStatusCode() == HttpStatus.CREATED) {
                        created.incrementAndGet();
                        HoldResponse parsed = objectMapper.readValue(response.getBody(), HoldResponse.class);
                        distinctHoldIds.add(parsed.id());
                    } else {
                        unexpectedStatuses.incrementAndGet();
                        sampleUnexpectedResponse.compareAndSet(null,
                                response.getStatusCode() + " :: " + response.getBody());
                    }
                } catch (Exception e) {
                    unexpectedStatuses.incrementAndGet();
                    sampleUnexpectedResponse.compareAndSet(null, "EXCEPTION :: " + e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.printf(
                "created=%d unexpectedStatuses=%d distinctHoldIds=%d%n",
                created.get(), unexpectedStatuses.get(), distinctHoldIds.size());
        if (sampleUnexpectedResponse.get() != null) {
            System.out.println("Sample unexpected response: " + sampleUnexpectedResponse.get());
        }

        Integer holdRowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM hold WHERE idempotency_key = ?", Integer.class, sharedIdempotencyKey);
        Integer remainingCapacity = jdbcTemplate.queryForObject(
                "SELECT remaining_capacity FROM resource_pool WHERE id = ?", Integer.class, poolId);
        System.out.printf("hold rows for key=%d remaining_capacity=%d%n", holdRowCount, remainingCapacity);

        org.assertj.core.api.SoftAssertions softly = new org.assertj.core.api.SoftAssertions();
        softly.assertThat(finished).as("all requests completed within timeout").isTrue();
        softly.assertThat(created.get()).as("201 responses").isEqualTo(totalRequests);
        softly.assertThat(unexpectedStatuses.get()).as("non-201 responses").isZero();
        softly.assertThat(distinctHoldIds).as("every response should carry the SAME Hold id").hasSize(1);
        softly.assertThat(holdRowCount).as("rows in `hold` for this idempotency key").isEqualTo(1);
        softly.assertThat(remainingCapacity)
                .as("capacity decremented exactly once, not once per racing request")
                .isEqualTo(STARTING_CAPACITY - 1);
        softly.assertAll();
    }
}