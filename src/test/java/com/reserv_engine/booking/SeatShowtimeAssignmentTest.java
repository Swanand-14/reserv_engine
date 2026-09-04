package com.reserv_engine.booking;

import com.reserv_engine.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

class SeatShowtimeAssignmentTest extends AbstractIntegrationTest {

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
    void assignSeats_exactCountSucceeds_mismatchAndWrongHallAreRejected() {
        String orgToken = signupAndLogin("assign-org@test.com", "password123");
        grantOrganizerRole(orgToken);
        orgToken = login("assign-org@test.com", "password123");

        String venueId = extractField(post("/api/v1/venues", "{\"name\":\"Grand Cinema\"}", orgToken), "id");
        String hallId = extractField(post("/api/v1/venues/" + venueId + "/halls", "{\"name\":\"Screen 1\"}", orgToken), "id");

        // A second Hall, in the SAME Venue, to prove cross-hall seats are rejected
        String otherHallId = extractField(post("/api/v1/venues/" + venueId + "/halls", "{\"name\":\"Screen 2\"}", orgToken), "id");
        String foreignSeatId = extractField(post("/api/v1/halls/" + otherHallId + "/seats", "{\"label\":\"1A\"}", orgToken), "id");

        // 3 seats in the real hall
        String seat1 = extractField(post("/api/v1/halls/" + hallId + "/seats", "{\"label\":\"1A\"}", orgToken), "id");
        String seat2 = extractField(post("/api/v1/halls/" + hallId + "/seats", "{\"label\":\"1B\"}", orgToken), "id");
        String seat3 = extractField(post("/api/v1/halls/" + hallId + "/seats", "{\"label\":\"1C\"}", orgToken), "id");

        String eventId = extractField(post("/api/v1/events", "{\"title\":\"Inception\"}", orgToken), "id");

        String showtimeBody = """
            {"hallId":"%s","startTime":"2026-09-01T19:00:00","endTime":"2026-09-01T21:00:00"}
            """.formatted(hallId);
        String showtimeId = extractField(post("/api/v1/events/" + eventId + "/showtimes", showtimeBody, orgToken), "id");

        // Tier with capacity 3 -> exactly 3 ResourceUnits generated
        String tierBody = "{\"name\":\"Standard\",\"price\":250.00,\"totalCapacity\":3}";
        String tierId = extractField(post("/api/v1/showtimes/" + showtimeId + "/ticket-tiers", tierBody, orgToken), "id");

        // --- Negative: mismatched count (2 seats requested, 3 units available) ---
        String mismatchBody = "{\"seatIds\":[\"%s\",\"%s\"]}".formatted(seat1, seat2);
        ResponseEntity<String> meCheck = restTemplate.exchange(
                baseUrl() + "/users/me", HttpMethod.GET,
                new HttpEntity<>(authHeaders(orgToken)), String.class);
        System.out.println("PRE-ASSIGNMENT ROLE CHECK: " + meCheck.getBody());
        ResponseEntity<String> mismatchResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/ticket-tiers/" + tierId + "/seat-assignments",
                new HttpEntity<>(mismatchBody, authHeaders(orgToken)), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, mismatchResp.getStatusCode());

        // --- Negative: seat from a different Hall included ---
        String wrongHallBody = "{\"seatIds\":[\"%s\",\"%s\",\"%s\"]}".formatted(seat1, seat2, foreignSeatId);
        ResponseEntity<String> wrongHallResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/ticket-tiers/" + tierId + "/seat-assignments",
                new HttpEntity<>(wrongHallBody, authHeaders(orgToken)), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, wrongHallResp.getStatusCode());

        System.out.println("MISMATCH BODY: " + mismatchResp.getBody());
        System.out.println("WRONG-HALL BODY: " + wrongHallResp.getBody());

        // --- Positive: exact match, all 3 real seats ---
        String assignBody = "{\"seatIds\":[\"%s\",\"%s\",\"%s\"]}".formatted(seat1, seat2, seat3);
        ResponseEntity<String> assignResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/ticket-tiers/" + tierId + "/seat-assignments",
                new HttpEntity<>(assignBody, authHeaders(orgToken)), String.class);
        assertEquals(HttpStatus.CREATED, assignResp.getStatusCode());
        assertTrue(assignResp.getBody().contains(seat1));
        assertTrue(assignResp.getBody().contains(seat2));
        assertTrue(assignResp.getBody().contains(seat3));

        // --- Negative: re-running the same assignment now fails (0 units left) ---
        ResponseEntity<String> rerunResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/ticket-tiers/" + tierId + "/seat-assignments",
                new HttpEntity<>(assignBody, authHeaders(orgToken)), String.class);
        // seat1/seat2/seat3 are now already-assigned for this Showtime -> 409, not the 400 mismatch path
        assertEquals(HttpStatus.CONFLICT, rerunResp.getStatusCode());
    }
}