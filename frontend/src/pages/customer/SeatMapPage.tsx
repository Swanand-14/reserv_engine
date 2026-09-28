import { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { useParams, useLocation, useNavigate } from "react-router-dom";
import { getSeatMap, type SeatMapEntryResponse } from "../../api/seatMap";
import { createBookingHold } from "../../api/booking";
import { groupByRow } from "../../utils/seatLabel";
import { formatInr } from "../../utils/currency";
import { LoadingState } from "../../components/ui/LoadingState";
import { ErrorState } from "../../components/ui/ErrorState";
import { ApiError } from "../../api/client";

interface LocationState {
  eventTitle?: string;
  venueName?: string;
  hallName?: string;
  startTime?: string;
}

function seatStatusClass(seat: SeatMapEntryResponse, isSelected: boolean): string {
  if (isSelected) return "seat-btn--selected";
  if (seat.status === "AVAILABLE") return "seat-btn--available";
  if (seat.status === null) return "seat-btn--unassigned";
  return "seat-btn--unavailable"; // HELD or RESERVED
}

export function SeatMapPage() {
  const { eventId, showtimeId } = useParams<{ eventId: string; showtimeId: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const context = (location.state ?? {}) as LocationState;

  const [seatMap, setSeatMap] = useState<SeatMapEntryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [selectedSeatIds, setSelectedSeatIds] = useState<Set<string>>(new Set());
  const [proceedError, setProceedError] = useState<string | null>(null);
  const [proceeding, setProceeding] = useState(false);
  const alertRef = useRef<HTMLDivElement | null>(null);

  const load = useCallback(async () => {
    if (!showtimeId) return;
    setLoading(true);
    setError(null);
    try {
      setSeatMap(await getSeatMap(showtimeId));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [showtimeId]);

  useEffect(() => {
    load();
  }, [load]);

  // Bring the error into view — the seat grid can be taller than the screen.
  useEffect(() => {
    if (proceedError) alertRef.current?.scrollIntoView({ behavior: "smooth", block: "center" });
  }, [proceedError]);

  const rows = useMemo(() => groupByRow(seatMap), [seatMap]);

  const tierLegend = useMemo(() => {
    const seen = new Map<string, number>();
    for (const s of seatMap) {
      if (s.tierName && s.price != null && !seen.has(s.tierName)) {
        seen.set(s.tierName, s.price);
      }
    }
    return Array.from(seen.entries());
  }, [seatMap]);

  const selectedSeats = seatMap.filter((s) => selectedSeatIds.has(s.seatId));
  const totalPrice = selectedSeats.reduce((sum, s) => sum + (s.price ?? 0), 0);

  function toggleSeat(seat: SeatMapEntryResponse) {
    if (seat.status !== "AVAILABLE") return;
    setSelectedSeatIds((prev) => {
      const next = new Set(prev);
      if (next.has(seat.seatId)) next.delete(seat.seatId);
      else next.add(seat.seatId);
      return next;
    });
  }

  // Re-fetch statuses without flipping the page back to its loading state,
  // and drop any selected seat that's no longer available. Returns the fresh map.
  async function refreshSeatMap(): Promise<SeatMapEntryResponse[] | null> {
    if (!showtimeId) return null;
    try {
      const fresh = await getSeatMap(showtimeId);
      setSeatMap(fresh);
      const stillAvailable = new Set(fresh.filter((s) => s.status === "AVAILABLE").map((s) => s.seatId));
      setSelectedSeatIds((prev) => new Set([...prev].filter((id) => stillAvailable.has(id))));
      return fresh;
    } catch {
      return null;
    }
  }

  async function handleProceed() {
    if (!showtimeId || selectedSeats.length === 0) return;
    setProceedError(null);
    setProceeding(true);
    const attempted = selectedSeats;
    try {
      const hold = await createBookingHold(
        showtimeId,
        attempted.map((s) => s.seatId)
      );
      navigate(`/holds/${hold.holdId}/checkout`, {
        state: {
          hold,
          eventId,
          showtimeId,
          eventTitle: context.eventTitle,
          venueName: context.venueName,
          hallName: context.hallName,
          startTime: context.startTime,
        },
      });
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        // Another customer held or booked one of these seats after this
        // page's seat map was loaded. Refresh so the map shows the truth,
        // and tell the customer which of their seats were lost.
        const fresh = await refreshSeatMap();
        const lost = fresh
          ? attempted
              .filter((a) => fresh.find((f) => f.seatId === a.seatId)?.status !== "AVAILABLE")
              .map((a) => a.label)
          : [];
        setProceedError(
          lost.length > 0
            ? `Sorry, ${lost.length === 1 ? "seat" : "seats"} ${lost.join(", ")} ${
                lost.length === 1 ? "was" : "were"
              } just taken by another customer. The seat map has been updated — please pick different seats.`
            : "Sorry, some of those seats were just taken by another customer. The seat map has been updated — please pick different seats."
        );
      } else {
        setProceedError(
          err instanceof ApiError && err.message
            ? err.message
            : "We couldn't hold those seats. Please try again."
        );
        await refreshSeatMap();
      }
    } finally {
      setProceeding(false);
    }
  }

  if (loading) return <LoadingState label="Loading seat map..." />;
  if (error) {
    return (
      <div className="page-wide">
        <ErrorState error={error} onRetry={load} />
      </div>
    );
  }

  return (
    <div className="page-wide seat-map-page">
      <div className="seat-map-header">
        {context.eventTitle && <h1>{context.eventTitle}</h1>}
        <p className="muted">
          {context.venueName && `${context.venueName} · `}
          {context.hallName}
          {context.startTime &&
            ` · ${new Date(context.startTime).toLocaleString(undefined, {
              weekday: "short",
              day: "numeric",
              month: "short",
              hour: "numeric",
              minute: "2-digit",
            })}`}
        </p>
      </div>

      {proceedError && (
        <div className="seat-map-alert" role="alert" ref={alertRef}>
          <span>{proceedError}</span>
          <button
            type="button"
            className="seat-map-alert__close"
            onClick={() => setProceedError(null)}
            aria-label="Dismiss"
          >
            ×
          </button>
        </div>
      )}

      <div className="screen">
        <div className="screen__curve" />
        <div className="screen__label">Screen this way</div>
      </div>

      {seatMap.length === 0 ? (
        <p className="muted" style={{ textAlign: "center" }}>
          Seating isn't set up for this showtime yet.
        </p>
      ) : (
        <>
          <div className="seat-picker seat-picker--map">
            {rows.map(({ row, seats }) => (
              <div className="seat-picker__row" key={row}>
                <span className="seat-picker__row-label">{row}</span>
                <div className="seat-picker__seats">
                  {seats.map((s) => (
                    <button
                      key={s.seatId}
                      type="button"
                      className={`seat-btn ${seatStatusClass(s, selectedSeatIds.has(s.seatId))}`}
                      onClick={() => toggleSeat(s)}
                      disabled={s.status !== "AVAILABLE"}
                      title={s.tierName ? `${s.label} — ${s.tierName} · ₹${s.price}` : s.label}
                    >
                      {s.label}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>

          <div className="seat-map-legend">
            <span className="legend-item">
              <span className="legend-swatch legend-swatch--available" /> Available
            </span>
            <span className="legend-item">
              <span className="legend-swatch legend-swatch--selected" /> Selected
            </span>
            <span className="legend-item">
              <span className="legend-swatch legend-swatch--unavailable" /> Reserved / held
            </span>
            {tierLegend.map(([name, price]) => (
              <span className="legend-item" key={name}>
                {name} · &#8377;{formatInr(price)}
              </span>
            ))}
          </div>
        </>
      )}

      <div className="selection-bar">
        <div className="page-wide selection-bar__inner">
          <div>
            {selectedSeats.length === 0 ? (
              <span className="muted">Select seats to continue</span>
            ) : (
              <>
                <strong>{selectedSeats.length}</strong>{" "}
                <span className="muted">
                  seat{selectedSeats.length === 1 ? "" : "s"} · {selectedSeats.map((s) => s.label).join(", ")}
                </span>
                <div className="selection-bar__total">&#8377;{formatInr(totalPrice)}</div>
              </>
            )}
          </div>
          <button onClick={handleProceed} disabled={selectedSeats.length === 0 || proceeding}>
            {proceeding ? "Holding seats..." : "Proceed"}
          </button>
        </div>
      </div>
    </div>
  );
}