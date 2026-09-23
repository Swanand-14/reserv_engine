import { apiFetch } from "./client";

// Matches com.reserv_engine.booking.dto.response.TicketTierResponse exactly.
// Note: totalCapacity is NOT part of this response (backend doesn't return
// it), even though it's required on create — callers that need it should
// track the value they submitted alongside the returned tier.
export interface TicketTierResponse {
  id: string;
  showtimeId: string;
  resourcePoolId: string;
  name: string;
  price: number;
  createdAt: string;
}

export interface CreateTicketTierRequest {
  name: string;
  price: number;
  totalCapacity: number;
}

export function createTicketTier(
  showtimeId: string,
  request: CreateTicketTierRequest
): Promise<TicketTierResponse> {
  return apiFetch<TicketTierResponse>(`/api/v1/showtimes/${showtimeId}/ticket-tiers`, {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function listTicketTiers(showtimeId: string): Promise<TicketTierResponse[]> {
  return apiFetch<TicketTierResponse[]>(`/api/v1/showtimes/${showtimeId}/ticket-tiers`);
}

// All-or-nothing: seatIds length must exactly match the tier's totalCapacity
// from creation, or the backend 400s.
export function assignSeatsToTier(tierId: string, seatIds: string[]): Promise<void> {
  return apiFetch<void>(`/api/v1/ticket-tiers/${tierId}/seat-assignments`, {
    method: "POST",
    body: JSON.stringify({ seatIds }),
  });
}