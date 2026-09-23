import { apiFetch } from "./client";

// Matches com.reserv_engine.booking.dto.response.ShowtimeResponse exactly —
// returned by POST create.
export interface ShowtimeResponse {
  id: string;
  eventId: string;
  hallId: string;
  availabilityWindowId: string;
  startTime: string;
  endTime: string;
  createdAt: string;
}

// Matches com.reserv_engine.booking.dto.response.ShowtimeBrowseResponse
// exactly — returned by GET .../mine. Different shape from ShowtimeResponse:
// enriched with hall/venue names and starting price (null until ticket
// tiers exist for the showtime).
export interface ShowtimeBrowseResponse {
  id: string;
  startTime: string;
  endTime: string;
  hallName: string;
  venueName: string;
  startingPrice: number | null;
}

export interface CreateShowtimeRequest {
  hallId: string;
  startTime: string; // "YYYY-MM-DDTHH:mm" — matches <input type="datetime-local"> directly
  endTime: string;
}

export function createShowtime(
  eventId: string,
  request: CreateShowtimeRequest
): Promise<ShowtimeResponse> {
  return apiFetch<ShowtimeResponse>(`/api/v1/events/${eventId}/showtimes`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function listMyShowtimes(eventId: string): Promise<ShowtimeBrowseResponse[]> {
  return apiFetch<ShowtimeBrowseResponse[]>(`/api/v1/events/${eventId}/showtimes/mine`);
}