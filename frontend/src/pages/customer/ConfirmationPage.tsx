import { useState, useEffect } from "react";
import { useParams, useLocation, Link } from "react-router-dom";
import type { BookingHoldResponse } from "../../api/booking";
import type { ReservationResponse } from "../../api/reservations";
import { listMyReservations } from "../../api/reservations";
import { formatInr } from "../../utils/currency";
import { LoadingState } from "../../components/ui/LoadingState";
import { ErrorState } from "../../components/ui/ErrorState";

interface ConfirmationState {
  reservation?: ReservationResponse;
  hold?: BookingHoldResponse;
  eventTitle?: string;
  venueName?: string;
  hallName?: string;
}

interface DisplayData {
  status: string;
  confirmedAt: string;
  eventTitle?: string;
  venueName?: string;
  hallName?: string;
  seats: { label: string; tierName: string; price: number }[];
  total: number;
}

export function ConfirmationPage() {
  const { reservationId } = useParams<{ reservationId: string }>();
  const location = useLocation();
  const state = (location.state ?? {}) as ConfirmationState;

  const [fallbackData, setFallbackData] = useState<DisplayData | null>(null);
  const [loading, setLoading] = useState(!state.reservation);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    // Fast path: navigated here straight from checkout, everything's already
    // in hand. Fallback only runs on a direct visit or a refresh, since
    // there's no GET-reservation-by-id endpoint — /my-reservations is the
    // only way to recover the data at that point.
    if (state.reservation || !reservationId) return;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const all = await listMyReservations();
        const match = all.find((r) => r.reservationId === reservationId);
        if (!match) {
          setError(new Error("Reservation not found"));
          return;
        }
        setFallbackData({
          status: match.status,
          confirmedAt: match.confirmedAt,
          eventTitle: match.eventTitle,
          seats: match.seats.map((s) => ({ label: s.seatLabel, tierName: s.tierName, price: s.price })),
          total: match.totalPaid,
        });
      } catch (err) {
        setError(err);
      } finally {
        setLoading(false);
      }
    })();
  }, [reservationId, state.reservation]);

  if (loading) return <LoadingState label="Loading your reservation..." />;
  if (error) {
    return (
      <div className="page">
        <ErrorState error={error} />
      </div>
    );
  }

  const data: DisplayData | null = state.reservation
    ? {
        status: state.reservation.status,
        confirmedAt: state.reservation.confirmedAt,
        eventTitle: state.eventTitle,
        venueName: state.venueName,
        hallName: state.hallName,
        seats: (state.hold?.lines ?? []).map((l) => ({
          label: l.seatLabel,
          tierName: l.tierName,
          price: l.price,
        })),
        total: state.hold?.totalPrice ?? 0,
      }
    : fallbackData;

  if (!data) return null;

  return (
    <div className="page">
      <div className="card" style={{ textAlign: "center" }}>
        <h1 style={{ color: "var(--color-success)" }}>You're booked!</h1>
        <p className="muted">
          {data.eventTitle}
          {data.venueName && ` · ${data.venueName}`}
          {data.hallName && ` · ${data.hallName}`}
        </p>
        <p className="muted">
          Confirmed {new Date(data.confirmedAt).toLocaleString(undefined, {
            dateStyle: "medium",
            timeStyle: "short",
          })}
        </p>
      </div>

      <div className="card">
        <h2>Seats</h2>
        <ul className="list">
          {data.seats.map((s) => (
            <li key={s.label}>
              <strong>{s.label}</strong> — {s.tierName}
              <div className="muted">&#8377;{formatInr(s.price)}</div>
            </li>
          ))}
        </ul>
        <div style={{ marginTop: "0.75rem", fontWeight: 700 }}>
          Total paid: &#8377;{formatInr(data.total)}
        </div>
      </div>

      <Link to="/browse">
        <button>Back to Now Showing</button>
      </Link>
    </div>
  );
}