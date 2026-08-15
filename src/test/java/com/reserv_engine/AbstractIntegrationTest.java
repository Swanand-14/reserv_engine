package com.reserv_engine;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for every concurrency/integration test in this project.
 *
 * Spins up a REAL MySQL 9.4.0 container — the exact same version your dev
 * Docker setup runs (docker exec ... mysql --version confirmed 9.4.0) — not
 * H2 or any in-memory substitute. This matters concretely for this project:
 * every real bug found so far (the insert-before-update flush-order
 * deadlock, the findByIdForUpdate identity-map staleness, the REPEATABLE
 * READ idempotency-recovery snapshot bug, the chk_pool_capacity_bounds
 * constraint violation) was a genuine InnoDB/MySQL behavior that an
 * in-memory database would not reproduce. A test suite that passes against
 * the wrong engine would be worse than no test suite — it would report
 * false confidence.
 *
 * @ServiceConnection auto-configures Spring's DataSource (url/username/
 * password) to point at this container — no manual @DynamicPropertySource
 * wiring needed. Flyway then runs your real migrations against it on
 * startup, same as it does against your real dev DB, so the schema itself
 * is under test too, not just the Java code.
 *
 * webEnvironment = RANDOM_PORT starts a real embedded server (not
 * MockMvc's fake dispatcher) — this matters for concurrency tests
 * specifically, since we need genuine concurrent HTTP requests hitting a
 * real socket, not calls funneled through a single shared test transaction.
 */
@Testcontainers
@ExtendWith(org.springframework.test.context.junit.jupiter.SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.docker.compose.enabled=false")
@AutoConfigureTestRestTemplate
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:9.4.0");

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    protected String baseUrl() {
        return "http://localhost:" + port;
    }
}