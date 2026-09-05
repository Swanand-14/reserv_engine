package com.reserv_engine.booking;

import com.reserv_engine.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BookingOrchestrationTest extends AbstractIntegrationTest {

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private String extractField(String json, String field) {
        int start = json.indexOf("\"" + field + "\":\"") + field.length() + 4;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private String post(String path, String body, String token) {
        ResponseEntity<String> resp = restTemplate.postForEntity(
                baseUrl() + path, new HttpEntity<>(body, authHeaders(token)), String.class);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode(), "POST " + path + " failed: " + resp.getBody());
        return resp.getBody();
    }

    @Test
    void customerCanBookAssignedSeats_secondCustomerBlockedFromAlreadyHeldSeat() {
        String orgToken = signupAndLogin("booking-org@test.com", "password123");
        grantOrganizerRole(orgToken);
        orgToken = login("booking-org@test.com", "password123");

        String venueId = extractField(post("/api/v1/venues", "{\"name\":\"Grand Cinema\"}", orgToken), "id");
        String hallId = extractField(post("/api/v1/venues/" + venueId + "/halls", "{\"name\":\"Screen 1\"}", orgToken), "id");
        String seat1 = extractField(post("/api/v1/halls/" + hallId + "/seats", "{\"label\":\"1A\"}", orgToken), "id");
        String seat2 = extractField(post("/api/v1/halls/" + hallId + "/seats", "{\"label\":\"1B\"}", orgToken), "id");
        String seat3 = extractField(post("/api/v1/halls/" + hallId + "/seats", "{\"label\":\"1C\"}", orgToken), "id");

        String eventId = extractField(post("/api/v1/events", "{\"title\":\"Inception\"}", orgToken), "id");
        String showtimeBody = """
            {"hallId":"%s","startTime":"2026-09-01T19:00:00","endTime":"2026-09-01T21:00:00"}
            """.formatted(hallId);
        String showtimeId = extractField(post("/api/v1/events/" + eventId + "/showtimes", showtimeBody, orgToken), "id");

        String tierBody = "{\"name\":\"Standard\",\"price\":250.00,\"totalCapacity\":3}";
        String tierId = extractField(post("/api/v1/showtimes/" + showtimeId + "/ticket-tiers", tierBody, orgToken), "id");

        String assignBody = "{\"seatIds\":[\"%s\",\"%s\",\"%s\"]}".formatted(seat1, seat2, seat3);
        post("/api/v1/ticket-tiers/" + tierId + "/seat-assignments", assignBody, orgToken);

        // --- Customer A books seat1 + seat2 ---
        String customerAToken = signupAndLogin("customerA@test.com", "password123");
        String bookBody = """
            {"seatIds":["%s","%s"],"idempotencyKey":"%s"}
            """.formatted(seat1, seat2, UUID.randomUUID());
        ResponseEntity<String> bookResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/showtimes/" + showtimeId + "/bookings",
                new HttpEntity<>(bookBody, authHeaders(customerAToken)), String.class);
        assertEquals(HttpStatus.CREATED, bookResp.getStatusCode(), bookResp.getBody());
        assertTrue(bookResp.getBody().contains("\"status\":\"ACTIVE\""));

        // --- Customer B tries to book seat1 (now HELD) — must be rejected ---
        String customerBToken = signupAndLogin("customerB@test.com", "password123");
        String conflictBody = """
            {"seatIds":["%s"],"idempotencyKey":"%s"}
            """.formatted(seat1, UUID.randomUUID());
        ResponseEntity<String> conflictResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/showtimes/" + showtimeId + "/bookings",
                new HttpEntity<>(conflictBody, authHeaders(customerBToken)), String.class);
        assertEquals(HttpStatus.CONFLICT, conflictResp.getStatusCode(), conflictResp.getBody());

        // --- Customer B books seat3 (still AVAILABLE) — should succeed ---
        String seat3Body = """
            {"seatIds":["%s"],"idempotencyKey":"%s"}
            """.formatted(seat3, UUID.randomUUID());
        ResponseEntity<String> seat3Resp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/showtimes/" + showtimeId + "/bookings",
                new HttpEntity<>(seat3Body, authHeaders(customerBToken)), String.class);
        assertEquals(HttpStatus.CREATED, seat3Resp.getStatusCode(), seat3Resp.getBody());
    }
}