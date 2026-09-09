import { apiFetch } from "./client";

export interface VenueResponse {
  id: string;
  managerId: string;
  name: string;
  createdAt: string;
}

export function createVenue(name: string): Promise<VenueResponse> {
  return apiFetch<VenueResponse>("/api/v1/venues", {
    method: "POST",
    body: JSON.stringify({ name }),
  });
}
export function listMyVenues(): Promise<VenueResponse[]> {
  return apiFetch<VenueResponse[]>("/api/v1/venues");
}