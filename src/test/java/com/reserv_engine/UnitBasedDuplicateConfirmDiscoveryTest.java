package com.reserv_engine;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UNIT_BASED sibling of DuplicateConfirmDiscoveryTest — same discovery
 * methodology, deliberately never run before now. Every prior duplicate-
 * confirm/confirm-vs-expiry/expiry-vs-expiry test used a COUNTER_BASED
 * pool, where the only version-guarded write in ReservationWriteService's
 * transaction is on Hold itself.
 *
 * A UNIT_BASED confirm additionally writes ResourceUnit.status=RESERVED
 * on the SAME unit row for all 20 racing threads (same Hold -> same
 * HoldLine -> same unit). ResourceUnit carries its own @Version
 * (already proven load-bearing by UnitBasedHoldConcurrencyTest during
 * creation). Losing that version check throws OptimisticLockingFailure-
 * Exception -- a FOURTH exception type, distinct from the three
 * (DataIntegrityViolationException, CannotAcquireLockException,
 * ResourceConflictException) that confirm()'s catch clause was widened
 * to handle. GlobalExceptionHandler maps OptimisticLockingFailure-
 * Exception straight to 409 with no recovery re-query, since confirm()
 * doesn't catch it at all.
 *
 * Hypothesis under test: this unit-based path reproduces the same
 * "some responses become 409 instead of 201" pattern already fixed
 * twice for counter-based, via a mechanism nothing has exercised yet.
 * Reporting the real numbers, not asserting a guessed outcome, same
 * discipline as the original test.
 */
class UnitBasedDuplicateConfirmDiscoveryTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final int CONCURRENT_REQUESTS = 20;
    private String testToken;
    private String testHolderId;

    private record SeededHold(String unitId, String holdId, String holdLineId) {}

    private SeededHold seedActiveUnitBasedHoldReadyToConfirm() {
        String windowId = UUID.randomUUID().toString();
        String poolId = UUID.randomUUID().toString();
        String unitId = UUID.randomUUID().toString();
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
                VALUES (?, ?, 'test-owner', 'UNIT_BASED', 1, 1, 0, NOW())
                """, poolId, windowId);

        // Unit starts HELD -- this is the state a real checkout leaves it
        // in after Hold creation, and the state confirm's write is meant
        // to transition FROM (HELD -> RESERVED), same as the real flow.
        jdbcTemplate.update("""
                INSERT INTO resource_unit (id, resource_pool_id, status, version, created_at)
                VALUES (?, ?, 'HELD', 0, NOW())
                """, unitId, poolId);

        // Java-computed timestamps, well in the future -- same clock
        // lesson as every other seeded Hold in this suite.
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

        jdbcTemplate.update("""
                INSERT INTO hold (id, holder_id, status, idempotency_key, created_at, expires_at, version)
                VALUES (?,?, 'ACTIVE', ?, ?, ?, 0)
                """, holdId,testHolderId, "unit-duplicate-confirm-" + holdId, createdAt, expiresAt);

        jdbcTemplate.update("""
                INSERT INTO hold_line (id, hold_id, resource_pool_id, resource_unit_id, quantity)
                VALUES (?, ?, ?, ?, 1)
                """, holdLineId, holdId, poolId, unitId);

        jdbcTemplate.update("""
                INSERT INTO payment_attempt (id, hold_id, idempotency_key, status, amount, created_at, version)
                VALUES (?, ?, ?, 'SUCCESS', 10.00, NOW(), 0)
                """, paymentId, holdId, "payment-" + holdId);

        return new SeededHold(unitId, holdId, holdLineId);
    }

    @Test
    void twentyIdenticalConcurrentConfirms_unitBased_atMostOneReservationEverCreated() throws InterruptedException {
        String email = "unitdupconfirm-" + UUID.randomUUID() + "@example.com";
        String password = "password123";
        signupAndLogin(email, password);
        testToken = login(email, password);
        testHolderId = currentUserId(testToken);
        SeededHold hold = seedActiveUnitBasedHoldReadyToConfirm();

        String body = """
                {
                  "holdId": "%s",
                  "linePrices": [{"holdLineId": "%s", "price": 10.00}]
                }
                """.formatted(hold.holdId(), hold.holdLineId());

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);

        AtomicInteger count201 = new AtomicInteger();
        AtomicInteger count409 = new AtomicInteger();
        AtomicInteger count5xx = new AtomicInteger();
        AtomicInteger countOther = new AtomicInteger();
        ConcurrentLinkedQueue<String> sampleBodiesByBucket = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();

                    HttpHeaders headers = new HttpHeaders();

                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setBearerAuth(testToken);
                    ResponseEntity<String> response = restTemplate.postForEntity(
                            baseUrl() + "/api/v1/reservations/confirm",
                            new HttpEntity<>(body, headers), String.class);

                    HttpStatus status = (HttpStatus) response.getStatusCode();
                    if (status == HttpStatus.CREATED) {
                        count201.incrementAndGet();
                    } else if (status == HttpStatus.CONFLICT) {
                        count409.incrementAndGet();
                        sampleBodiesByBucket.add("409 :: " + response.getBody());
                    } else if (status.is5xxServerError()) {
                        count5xx.incrementAndGet();
                        sampleBodiesByBucket.add("5xx :: " + status + " :: " + response.getBody());
                    } else {
                        countOther.incrementAndGet();
                        sampleBodiesByBucket.add("other :: " + status + " :: " + response.getBody());
                    }
                } catch (Exception e) {
                    count5xx.incrementAndGet();
                    sampleBodiesByBucket.add("EXCEPTION :: " + e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        String holdStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM hold WHERE id = ?", String.class, hold.holdId());
        var unitRow = jdbcTemplate.queryForMap(
                "SELECT status, version FROM resource_unit WHERE id = ?", hold.unitId());
        List<Map<String, Object>> reservationRows = jdbcTemplate.queryForList(
                "SELECT id, status FROM reservation WHERE hold_id = ?", hold.holdId());
        long unitVersion = ((Number) unitRow.get("version")).longValue();

        System.out.println("=== Unit-based duplicate-confirm discovery report ===");
        System.out.printf("1. 201 CREATED responses : %d%n", count201.get());
        System.out.printf("2. 409 CONFLICT responses: %d%n", count409.get());
        System.out.printf("3. 5xx / unexpected      : %d (5xx=%d, other=%d)%n",
                count5xx.get() + countOther.get(), count5xx.get(), countOther.get());
        System.out.printf("4. reservation rows for this Hold: %d %s%n",
                reservationRows.size(), reservationRows);
        System.out.printf("5. Final Hold status: %s%n", holdStatus);
        System.out.printf("6. Final resource_unit row: %s%n", unitRow);
        System.out.printf("7. All requests completed within timeout: %s%n", finished);
        if (!sampleBodiesByBucket.isEmpty()) {
            System.out.println("Sample 409/error response bodies:");
            sampleBodiesByBucket.forEach(System.out::println);
        }

        boolean reservationExists = !reservationRows.isEmpty();
        boolean holdStatusConsistentWithReservation =
                (reservationExists && "CONSUMED".equals(holdStatus))
                        || (!reservationExists && "ACTIVE".equals(holdStatus));
        System.out.printf("Hold status consistent with reservation existence: %s%n", holdStatusConsistentWithReservation);

        assertThat(finished).as("all requests completed within timeout").isTrue();

        // Same invariant the original test asserted regardless of status-code
        // outcome -- everything else is reported above, not asserted, since
        // the whole point is to see the real numbers first.
        assertThat(reservationRows.size())
                .as("number of `reservation` rows for this Hold — must never exceed 1")
                .isLessThanOrEqualTo(1);

        assertThat(unitVersion)

                .as("ResourceUnit version — exactly one successful HELD -> RESERVED transition")

                .isEqualTo(1L);
        assertThat(unitRow.get("status"))

                .as("ResourceUnit must be RESERVED after successful confirmation")

                .isEqualTo("RESERVED");
    }
}