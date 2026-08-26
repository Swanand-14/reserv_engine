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

class IdempotencyRaceHoldConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper objectMapper;

    private String poolId;
    private String testToken;
    private String testHolderId;
    private static final int STARTING_CAPACITY = 100;

    @BeforeEach
    void setUp() {
        String email = "holdrace-" + UUID.randomUUID() + "@example.com";
        String password = "password123";
        signupAndLogin(email, password); // creates the user; token discarded, no roles yet
        String initialToken = login(email, password);
        grantOrganizerRole(initialToken);
        testToken = login(email, password); // fresh token, now carries ORGANIZER
        testHolderId = currentUserId(testToken);

        String windowId = UUID.randomUUID().toString();
        poolId = UUID.randomUUID().toString();

        jdbcTemplate.update("""
                INSERT INTO availability_window (id, owner_id, start_time, end_time, created_at)
                VALUES (?, ?, NOW(), NOW() + INTERVAL 1 DAY, NOW())
                """, windowId, testHolderId);

        jdbcTemplate.update("""
                INSERT INTO resource_pool
                    (id, availability_window_id, owner_id, pool_mode, total_capacity, remaining_capacity, version, created_at)
                VALUES (?, ?, ?, 'COUNTER_BASED', ?, ?, 0, NOW())
                """, poolId, windowId, testHolderId, STARTING_CAPACITY, STARTING_CAPACITY);
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
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();

                    // holderId in the body is now irrelevant — HoldController
                    // overwrites it with the authenticated user's real id
                    // before calling HoldService. Left as a fixed literal
                    // since every racing request must resolve to the SAME
                    // holder anyway (testHolderId), matching what the
                    // ownership guard now requires.
                    String body = """
                            {
                              "holderId": "%s",
                              "idempotencyKey": "%s",
                              "lines": [{"resourcePoolId": "%s", "quantity": 1}]
                            }
                            """.formatted(testHolderId, sharedIdempotencyKey, poolId);

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.setBearerAuth(testToken);

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