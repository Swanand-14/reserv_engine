import { apiFetch } from "./client";
import type { EventBrowseResponse } from "../types/event";

export function listPublishedEvents(): Promise<EventBrowseResponse[]> {
  return apiFetch<EventBrowseResponse[]>("/api/v1/events");
}

export function getEventDetail(eventId: string): Promise<EventBrowseResponse> {
  return apiFetch<EventBrowseResponse>(`/api/v1/events/${eventId}`);
}
