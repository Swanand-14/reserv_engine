import { useState, useEffect, useCallback } from "react";
import { listMyReservations, cancelReservation, type MyReservationResponse } from "../../api/reservations";
import { formatInr } from "../../utils/currency";
import { LoadingState } from "../../components/ui/LoadingState";
import { ErrorState } from "../../components/ui/ErrorState";
import { EmptyState } from "../../components/ui/EmptyState";
import { ApiError } from "../../api/client";

function statusBadgeClass(status: string): string {
  switch (status) {
    case "CONFIRMED":
      return "status-badge status-badge--confirmed";
    case "CANCELLED":
      return "status-badge status-badge--cancelled";
    case "COMPLETED":
      return "status-badge status-badge--completed";
    default:
      return "status-badge";
  }
}

function formatShowtime(start: string, end: string): string {
  const startDate = new Date(start);
  const endDate = new Date(end);
  return `${startDate.toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" })} · ${startDate.toLocaleTimeString(
    undefined,
    { hour: "numeric", minute: "2-digit" }
  )} – ${endDate.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" })}`;
}

export function MyReservationsPage() {
  const [reservations, setReservations] = useState<MyReservationResponse[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [cancellingId, setCancellingId] = useState<string | null>(null);
  const [cancelError, setCancelError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listMyReservations();
      data.sort((a, b) => new Date(b.confirmedAt).getTime() - new Date(a.confirmedAt).getTime());
      setReservations(data);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function handleCancel(reservationId: string) {
    setCancelError(null);
    setCancellingId(reservationId);
    try {
      await cancelReservation(reservationId);
      await load();
    } catch (err) {
      setCancelError(err instanceof ApiError ? err.message : "Failed to cancel reservation");
    } finally {
      setCancellingId(null);
    }
  }

  return (
    <div className="page-wide">
      <div className="browse-hero">
        <h1>My Reservations</h1>
        {reservations && (
          <p className="browse-hero__count">
            {reservations.length} {reservations.length === 1 ? "reservation" : "reservations"}
          </p>
        )}
      </div>

      {loading && <LoadingState label="Loading your reservations..." />}
      {!loading && error !== null && <ErrorState error={error} onRetry={load} />}
      {!loading && error === null && reservations && reservations.length === 0 && (
        <EmptyState
          title="No reservations yet"
          message="Once you book a showtime, it'll show up here."
        />
      )}

      {!loading && error === null && reservations && reservations.length > 0 && (
        <>
          {cancelError && (
            <p className="error" role="alert" style={{ marginBottom: "1rem" }}>
              {cancelError}
            </p>
          )}
          <div className="reservation-list">
            {reservations.map((r) => (
              <div className="card reservation-card" key={r.reservationId}>
                <div className="reservation-card__header">
                  <div>
                    <h3>{r.eventTitle}</h3>
                    <p className="muted">{formatShowtime(r.startTime, r.endTime)}</p>
                  </div>
                  <span className={statusBadgeClass(r.status)}>{r.status.toLowerCase()}</span>
                </div>

                <ul className="list">
                  {r.seats.map((s) => (
                    <li key={s.seatLabel}>
                      <strong>{s.seatLabel}</strong> — {s.tierName}
                      <span className="muted"> · &#8377;{formatInr(s.price)}</span>
                    </li>
                  ))}
                </ul>

                <div className="reservation-card__footer">
                  <span className="muted">
                    Confirmed{" "}
                    {new Date(r.confirmedAt).toLocaleString(undefined, {
                      dateStyle: "medium",
                      timeStyle: "short",
                    })}
                  </span>
                  <div style={{ display: "flex", alignItems: "center", gap: "1rem" }}>
                    <strong>&#8377;{formatInr(r.totalPaid)}</strong>
                    {r.status === "CONFIRMED" && (
                      <button
                        className="secondary"
                        onClick={() => handleCancel(r.reservationId)}
                        disabled={cancellingId === r.reservationId}
                      >
                        {cancellingId === r.reservationId ? "Cancelling..." : "Cancel"}
                      </button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}