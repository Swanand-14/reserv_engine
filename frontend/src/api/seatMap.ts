import { apiFetch } from "./client";

// Matches com.reserv_engine.booking.dto.response.SeatMapEntryResponse
// exactly. tierName/price/status are all null until the seat has been
// assigned to a ticket tier.
export interface SeatMapEntryResponse {
  seatId: string;
  label: string;
  tierName: string | null;
  price: number | null;
  status: string | null;
}

export function getSeatMap(showtimeId: string): Promise<SeatMapEntryResponse[]> {
  return apiFetch<SeatMapEntryResponse[]>(`/api/v1/showtimes/${showtimeId}/seat-map`);
}