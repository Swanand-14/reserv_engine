import { apiFetch } from "./client";

// Matches com.reserv_engine.booking.dto.response.BookingHoldResponse
// exactly. This orchestration endpoint hides the low-level Engine Hold
// entirely — one call creates the hold AND returns everything the
// payment/confirm steps need (holdLineId per seat, seat label, tier,
// price, total) without a second round trip.
export interface BookingHoldLine {
  holdLineId: string;
  seatLabel: string;
  tierName: string;
  price: number;
}

export interface BookingHoldResponse {
  holdId: string;
  status: string;
  expiresAt: string;
  lines: BookingHoldLine[];
  totalPrice: number;
}

export function createBookingHold(showtimeId: string, seatIds: string[]): Promise<BookingHoldResponse> {
  return apiFetch<BookingHoldResponse>(`/api/v1/showtimes/${showtimeId}/bookings`, {
    method: "POST",
    body: JSON.stringify({ seatIds, idempotencyKey: crypto.randomUUID() }),
  });
}

// There's no GET-hold-by-id endpoint on the backend — a hold can only be
// created or cancelled, never re-fetched. Callers that need hold details
// after this point have to carry the BookingHoldResponse themselves.
export function cancelHold(holdId: string): Promise<void> {
  return apiFetch<void>(`/api/v1/holds/${holdId}/cancel`, { method: "POST" });
}