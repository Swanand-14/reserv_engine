import { apiFetch } from "./client";

export type EventLifecycleStatus = "DRAFT" | "PUBLISHED";

// Matches com.reserv_engine.booking.dto.response.OrganizerEventResponse exactly.
export interface OrganizerEventResponse {
  id: string;
  title: string;
  lifecycleStatus: EventLifecycleStatus;
  showtimeCount: number;
  createdAt: string;
}

export function listMyEvents(): Promise<OrganizerEventResponse[]> {
  return apiFetch<OrganizerEventResponse[]>("/api/v1/events/mine");
}
export interface EventResponse {
  id: string;
  organizerId: string;
  title: string;
  lifecycleStatus: EventLifecycleStatus;
  createdAt: string;
}

export function publishEvent(eventId: string): Promise<EventResponse> {
  return apiFetch<EventResponse>(`/api/v1/events/${eventId}/publish`, {
    method: "PATCH",
  });
}