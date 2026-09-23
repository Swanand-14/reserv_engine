// Mirrors com.reserv_engine.booking.dto.response.EventBrowseResponse exactly.
// Deliberately no poster/description/genre — the backend doesn't carry them.
export interface EventBrowseResponse {
  id: string;
  title: string;
  showtimeCount: number;
  startingPrice: number; // BigDecimal serializes as a JSON number
}
