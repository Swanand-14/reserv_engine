package com.reserv_engine.booking;

import com.reserv_engine.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import static org.junit.jupiter.api.Assertions.*;

class BookingStructureCreationTest extends AbstractIntegrationTest {

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

    @Test
    void organizerCanCreateFullBookingChain_customerAndOtherOrganizerAreBlocked() {
        // --- Organizer A: signup, self-grant ORGANIZER, re-login for fresh token ---
        String orgAToken = signupAndLogin("orgA@test.com", "password123");
        grantOrganizerRole(orgAToken);
        orgAToken = login("orgA@test.com", "password123");

        // --- Venue ---
        ResponseEntity<String> venueResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/venues",
                new HttpEntity<>("{\"name\":\"Grand Cinema\"}", authHeaders(orgAToken)),
                String.class);
        assertEquals(HttpStatus.CREATED, venueResp.getStatusCode());
        String venueId = extractField(venueResp.getBody(), "id");

        // --- Hall ---
        ResponseEntity<String> hallResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/venues/" + venueId + "/halls",
                new HttpEntity<>("{\"name\":\"Screen 3\"}", authHeaders(orgAToken)),
                String.class);
        assertEquals(HttpStatus.CREATED, hallResp.getStatusCode());
        String hallId = extractField(hallResp.getBody(), "id");

        // --- Seat ---
        ResponseEntity<String> seatResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/halls/" + hallId + "/seats",
                new HttpEntity<>("{\"label\":\"12A\"}", authHeaders(orgAToken)),
                String.class);
        assertEquals(HttpStatus.CREATED, seatResp.getStatusCode());

        // --- Event ---
        ResponseEntity<String> eventResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/events",
                new HttpEntity<>("{\"title\":\"Inception\"}", authHeaders(orgAToken)),
                String.class);
        assertEquals(HttpStatus.CREATED, eventResp.getStatusCode());
        String eventId = extractField(eventResp.getBody(), "id");

        // --- Showtime ---
        String showtimeBody = """
            {"hallId":"%s","startTime":"2026-09-01T19:00:00","endTime":"2026-09-01T21:00:00"}
            """.formatted(hallId);
        ResponseEntity<String> showtimeResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/events/" + eventId + "/showtimes",
                new HttpEntity<>(showtimeBody, authHeaders(orgAToken)),
                String.class);
        assertEquals(HttpStatus.CREATED, showtimeResp.getStatusCode());
        String showtimeId = extractField(showtimeResp.getBody(), "id");
        assertNotNull(extractField(showtimeResp.getBody(), "availabilityWindowId"));

        // --- TicketTier (this call reaches into the Engine's ResourcePoolService) ---
        ResponseEntity<String> tierResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/showtimes/" + showtimeId + "/ticket-tiers",
                new HttpEntity<>("{\"name\":\"Standard\",\"price\":250.00,\"totalCapacity\":100}", authHeaders(orgAToken)),
                String.class);
        assertEquals(HttpStatus.CREATED, tierResp.getStatusCode());
        assertNotNull(extractField(tierResp.getBody(), "resourcePoolId"));
        System.out.println("STATUS: " + tierResp.getStatusCode());
        System.out.println("BODY: " + tierResp.getBody());

        // --- Negative: CUSTOMER (no ORGANIZER role) cannot create a Venue ---
        String customerToken = signupAndLogin("customer@test.com", "password123");
        ResponseEntity<String> customerVenueResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/venues",
                new HttpEntity<>("{\"name\":\"Rogue Cinema\"}", authHeaders(customerToken)),
                String.class);
        assertEquals(HttpStatus.FORBIDDEN, customerVenueResp.getStatusCode());

        // --- Negative: a DIFFERENT organizer cannot create a Hall under Org A's Venue ---
        String orgBToken = signupAndLogin("orgB@test.com", "password123");
        grantOrganizerRole(orgBToken);
        orgBToken = login("orgB@test.com", "password123");

        ResponseEntity<String> orgBHallResp = restTemplate.postForEntity(
                baseUrl() + "/api/v1/venues/" + venueId + "/halls",
                new HttpEntity<>("{\"name\":\"Intruder Screen\"}", authHeaders(orgBToken)),
                String.class);
        assertEquals(HttpStatus.FORBIDDEN, orgBHallResp.getStatusCode());
    }
}