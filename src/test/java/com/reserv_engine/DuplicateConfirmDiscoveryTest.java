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
 * DISCOVERY test, not a correctness-assertion test in the usual sense of
 * this suite — the point is to find out what ReservationService.confirm
 * actually does under 20 identical concurrent duplicate-confirm requests
 * against the same Hold, not to assert a status code guessed in advance.
 *
 * Code inspection first (per instructions, not assuming): confirm's
 * pre-check (findByHoldIdWithLines) is a plain unlocked read. Under real
 * concurrency most of the 20 requests will miss that fast path and all
 * proceed toward reservationRepository.save(reservation) + hold.setStatus
 * (CONSUMED) inside ONE single @Transactional — no REQUIRES_NEW split like
 * HoldWriteService has. The losing INSERT hits uq_reservation_hold_id and
 * throws DataIntegrityViolationException. GlobalExceptionHandler has NO
 * handler for that exception type (only ResourceNotFoundException,
 * ResourceConflictException, IllegalArgumentException,
 * OptimisticLockingFailureException are mapped) — so the prediction from
 * reading the code is that losers surface as raw 500s, not clean 409s.
 * This test exists to confirm or refute that against real behavior.
 *
 * The one invariant this test DOES assert regardless of status-code
 * outcome: at most one `reservation` row may ever exist for a given
 * hold_id, no matter how many concurrent confirms arrive. Everything else
 * (status code distribution, exact error bodies) is reported, not
 * asserted, since the whole point is to see the real numbers.
 */
class DuplicateConfirmDiscoveryTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final int CONCURRENT_REQUESTS = 20;
    private static final int TOTAL_CAPACITY = 10;
    private String testToken;
    private String testHolderId;

    private record SeededHold(String poolId, String holdId, String holdLineId) {}

    private SeededHold seedActiveHoldReadyToConfirm() {
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
                """, poolId, windowId, TOTAL_CAPACITY, TOTAL_CAPACITY - 1);

        // Java-computed timestamps, well in the future — the timezone
        // lesson from ConfirmVsCancelRaceTest applies to every test that
        // seeds a Hold the app will later run isExpired() against.
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

        jdbcTemplate.update("""
                INSERT INTO hold (id, holder_id, status, idempotency_key, created_at, expires_at, version)
                VALUES (?, ?, 'ACTIVE', ?, ?, ?, 0)
                """, holdId, testHolderId,"duplicate-confirm-" + holdId, createdAt, expiresAt);

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

    @Test
    void twentyIdenticalConcurrentConfirms_atMostOneReservationEverCreated() throws InterruptedException {
        String email = "dupconfirm-" + UUID.randomUUID() + "@example.com";
        String password = "password123";
        signupAndLogin(email, password);
        testToken = login(email, password);
        testHolderId = currentUserId(testToken);
        SeededHold hold = seedActiveHoldReadyToConfirm();

        // Same valid confirmation body for all 20 requests, verbatim — a
        // genuine duplicate-submit scenario (double-click, retry storm),
        // not 20 different requests.
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
        List<Map<String, Object>> reservationRows = jdbcTemplate.queryForList(
                "SELECT id, status FROM reservation WHERE hold_id = ?", hold.holdId());

        System.out.println("=== Duplicate-confirm discovery report ===");
        System.out.printf("1. 201 CREATED responses : %d%n", count201.get());
        System.out.printf("2. 409 CONFLICT responses: %d%n", count409.get());
        System.out.printf("3. 5xx / unexpected      : %d (5xx=%d, other=%d)%n",
                count5xx.get() + countOther.get(), count5xx.get(), countOther.get());
        System.out.printf("4. reservation rows for this Hold: %d %s%n",
                reservationRows.size(), reservationRows);
        System.out.printf("5. Final Hold status: %s%n", holdStatus);
        System.out.printf("6. All requests completed within timeout: %s%n", finished);
        if (!sampleBodiesByBucket.isEmpty()) {
            System.out.println("Sample unexpected/error response bodies:");
            sampleBodiesByBucket.forEach(System.out::println);
        }

        boolean reservationExists = !reservationRows.isEmpty();
        boolean holdStatusConsistentWithReservation =
                (reservationExists && "CONSUMED".equals(holdStatus))
                        || (!reservationExists && "ACTIVE".equals(holdStatus));
        System.out.printf("Hold status consistent with reservation existence: %s%n", holdStatusConsistentWithReservation);

        assertThat(finished).as("all requests completed within timeout").isTrue();

        assertThat(reservationRows.size())
                .as("number of `reservation` rows for this Hold — must never exceed 1")
                .isLessThanOrEqualTo(1);
    }
}