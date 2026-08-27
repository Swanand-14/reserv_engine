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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PaymentAttempt entity's own javadoc states an invariant: "At most one
 * PaymentAttempt per Hold may reach status = SUCCESS (service layer —
 * checked before creating/resolving a new attempt)." This test checks
 * whether that actually holds under real concurrency, or is aspirational.
 *
 * PaymentAttemptService.attemptPayment never touches Hold.status, so the
 * ACTIVE guard is a non-factor here — nothing during this test changes
 * it. The ONLY thing that could block a second SUCCESS is the
 * findByHoldIdAndStatus(..., SUCCESS) pre-check, which is a plain read
 * with no lock, backed by no DB constraint (unlike Hold's
 * uq_hold_idempotency_key — payment_attempt's unique constraint is on
 * idempotency_key alone, which does nothing here since every request in
 * this test uses a DIFFERENT key). Under REPEATABLE READ, N genuinely
 * concurrent transactions each get their own snapshot at first read and
 * won't see each other's uncommitted writes — so the real prediction is
 * that MORE THAN ONE could land as SUCCESS. This test measures the actual
 * count rather than assuming either outcome.
 */
class ConcurrentPaymentSuccessRaceTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final int ITERATIONS = 5;
    private static final int CONCURRENT_ATTEMPTS_PER_ITERATION = 20;
    private String testToken;
    private String testHolderId;

    private String seedActiveHold() {
        String holdId = UUID.randomUUID().toString();
        LocalDateTime createdAt = LocalDateTime.now();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1); // Java-computed, well in the future

        jdbcTemplate.update("""
                INSERT INTO hold (id, holder_id, status, idempotency_key, created_at, expires_at, version)
                VALUES (?, ?, 'ACTIVE', ?, ?, ?, 0)
                """, holdId, testHolderId,"payment-race-hold-" + holdId, createdAt, expiresAt);

        return holdId;
    }

    private ResponseEntity<String> callAttemptPayment(String holdId, String idempotencyKey) {
        String body = """
                {
                  "holdId": "%s",
                  "idempotencyKey": "%s",
                  "amount": 10.00,
                  "simulateSuccess": true
                }
                """.formatted(holdId, idempotencyKey);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(testToken);
        return restTemplate.postForEntity(
                baseUrl() + "/api/v1/payment-attempts",
                new HttpEntity<>(body, headers), String.class);
    }

    @Test
    void concurrentSuccessAttempts_differentIdempotencyKeys_atMostOneShouldReachSuccess() throws InterruptedException {
        String email = "paymentrace-" + UUID.randomUUID() + "@example.com";
        String password = "password123";
        signupAndLogin(email, password);
        testToken = login(email, password);
        testHolderId = currentUserId(testToken);
        List<String> violations = new ArrayList<>();

        for (int i = 0; i < ITERATIONS; i++) {
            String holdId = seedActiveHold();

            ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_ATTEMPTS_PER_ITERATION);
            CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_ATTEMPTS_PER_ITERATION);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_ATTEMPTS_PER_ITERATION);

            AtomicInteger created201 = new AtomicInteger();
            AtomicInteger conflict409 = new AtomicInteger();
            AtomicInteger otherStatus = new AtomicInteger();

            for (int c = 0; c < CONCURRENT_ATTEMPTS_PER_ITERATION; c++) {
                String idempotencyKey = "payment-" + holdId + "-" + c;
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        HttpStatus status = (HttpStatus) callAttemptPayment(holdId, idempotencyKey).getStatusCode();
                        if (status == HttpStatus.CREATED) {
                            created201.incrementAndGet();
                        } else if (status == HttpStatus.CONFLICT) {
                            conflict409.incrementAndGet();
                        } else {
                            otherStatus.incrementAndGet();
                        }
                    } catch (Exception e) {
                        otherStatus.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown();
            boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            Integer successRowCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payment_attempt WHERE hold_id = ? AND status = 'SUCCESS'",
                    Integer.class, holdId);
            Integer totalRowCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM payment_attempt WHERE hold_id = ?", Integer.class, holdId);

            System.out.printf(
                    "iter %d: created201=%d conflict409=%d otherStatus=%d -> successRowCount=%d totalRowCount=%d%n",
                    i, created201.get(), conflict409.get(), otherStatus.get(), successRowCount, totalRowCount);

            if (!finished) {
                violations.add("iter %d: not all requests completed within timeout".formatted(i));
            }
            if (otherStatus.get() > 0) {
                violations.add("iter %d: %d requests got an unexpected (non-201/409) status".formatted(i, otherStatus.get()));
            }
            // The actual invariant under test, per PaymentAttempt's own javadoc:
            if (successRowCount > 1) {
                violations.add("iter %d: INVARIANT VIOLATED — %d PaymentAttempt rows reached SUCCESS for one Hold (expected at most 1)"
                        .formatted(i, successRowCount));
            }
            if (successRowCount == 0) {
                violations.add("iter %d: no PaymentAttempt reached SUCCESS at all — unexpected given simulateSuccess=true on every request"
                        .formatted(i));
            }
        }

        System.out.println("=== Summary ===");
        violations.forEach(System.out::println);
        if (violations.isEmpty()) {
            System.out.println("No violations — at-most-one-SUCCESS invariant held across all iterations.");
        }

        assertThat(violations).as("iterations where the at-most-one-SUCCESS invariant did not hold, or something else went wrong")
                .isEmpty();
    }
}