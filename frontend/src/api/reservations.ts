import { apiFetch } from "./client";
import type { BookingHoldLine } from "./booking";

// Matches com.reserv_engine.dto.ReservationResponse exactly — the Engine's
// own raw shape (resourcePoolId/resourceUnitId, no seat labels). Only
// useful for status/confirmedAt right after confirming; display uses the
// BookingHoldResponse already held client-side instead.
export interface ReservationLine {
  resourcePoolId: string;
  resourceUnitId: string;
  quantity: number;
  lockedPrice: number;
}
export interface ReservationResponse {
  id: string;
  holdId: string;
  status: string;
  confirmedAt: string;
  lines: ReservationLine[];
}

export function confirmReservation(
  holdId: string,
  lines: BookingHoldLine[]
): Promise<ReservationResponse> {
  return apiFetch<ReservationResponse>("/api/v1/reservations/confirm", {
    method: "POST",
    body: JSON.stringify({
      holdId,
      linePrices: lines.map((l) => ({ holdLineId: l.holdLineId, price: l.price })),
    }),
  });
}

// Matches com.reserv_engine.booking.dto.response.MyReservationResponse
// exactly — the Booking-layer, customer-facing, enriched view (one card
// per reservation, seats grouped underneath). Deliberately NOT paginated
// on the backend. Used for the My Reservations page, and as a fallback on
// the confirmation page if its navigation state is lost (e.g. a refresh) —
// there's no GET-reservation-by-id endpoint, so this is the only way back.
export interface MyReservationSeat {
  seatLabel: string;
  tierName: string;
  price: number;
}
export interface MyReservationResponse {
  reservationId: string;
  status: string;
  confirmedAt: string;
  eventTitle: string;
  showtimeId: string;
  startTime: string;
  endTime: string;
  seats: MyReservationSeat[];
  totalPaid: number;
}

export function listMyReservations(): Promise<MyReservationResponse[]> {
  return apiFetch<MyReservationResponse[]>("/api/v1/my-reservations");
}