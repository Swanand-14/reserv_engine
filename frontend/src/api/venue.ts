import { apiFetch } from "./client";

// Matches com.reserv_engine.booking.dto.response.VenueResponse exactly.
export interface VenueResponse {
  id: string;
  name: string;
  createdAt: string;
  hallCount: number;
  totalSeatCount: number;
}

// Matches com.reserv_engine.booking.dto.response.HallResponse exactly.
export interface HallResponse {
  id: string;
  venueId: string;
  name: string;
  createdAt: string;
}

// Venues are seeded/static for this demo — organizers pick from existing
// ones, there's no create-venue screen.
export function listMyVenues(): Promise<VenueResponse[]> {
  return apiFetch<VenueResponse[]>("/api/v1/venues");
}

export function listHallsForVenue(venueId: string): Promise<HallResponse[]> {
  return apiFetch<HallResponse[]>(`/api/v1/venues/${venueId}/halls`);
}