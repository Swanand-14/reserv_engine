import { useState, useEffect, useMemo } from "react";
import { useParams, useLocation, useNavigate, Link } from "react-router-dom";
import type { BookingHoldResponse } from "../../api/booking";
import { cancelHold } from "../../api/booking";
import { attemptPayment } from "../../api/payments";
import { confirmReservation } from "../../api/reservations";
import { formatInr } from "../../utils/currency";
import { ApiError } from "../../api/client";

interface CheckoutState {
  hold?: BookingHoldResponse;
  eventId?: string;
  showtimeId?: string;
  eventTitle?: string;
  venueName?: string;
  hallName?: string;
  startTime?: string;
}

function formatCountdown(totalSeconds: number): string {
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

export function CheckoutPage() {
  const { holdId } = useParams<{ holdId: string }>();
  const location = useLocation();
  const navigate = useNavigate();
  const state = (location.state ?? {}) as CheckoutState;
  const hold = state.hold;

  const [remainingSeconds, setRemainingSeconds] = useState<number>(() =>
    hold ? Math.max(0, Math.floor((new Date(hold.expiresAt).getTime() - Date.now()) / 1000)) : 0
  );
  const [paymentError, setPaymentError] = useState<string | null>(null);
  const [paying, setPaying] = useState(false);
  const [cancelling, setCancelling] = useState(false);

  useEffect(() => {
    if (!hold) return;
    const interval = setInterval(() => {
      setRemainingSeconds(Math.max(0, Math.floor((new Date(hold.expiresAt).getTime() - Date.now()) / 1000)));
    }, 1000);
    return () => clearInterval(interval);
  }, [hold]);

  const expired = remainingSeconds <= 0;

  const backToSeats = useMemo(() => {
    if (!state.eventId || !state.showtimeId) return "/browse";
    return `/events/${state.eventId}/showtimes/${state.showtimeId}/seats`;
  }, [state.eventId, state.showtimeId]);

  async function handlePayment(simulateSuccess: boolean) {
    if (!hold || !holdId) return;
    setPaymentError(null);
    setPaying(true);
    try {
      await attemptPayment(hold.holdId, hold.totalPrice, simulateSuccess);
      if (!simulateSuccess) {
        setPaymentError("Payment failed (simulated). Try again, or simulate success instead.");
        return;
      }
      const reservation = await confirmReservation(hold.holdId, hold.lines);
      navigate(`/reservations/${reservation.id}/confirmation`, {
        state: {
          reservation,
          hold,
          eventTitle: state.eventTitle,
          venueName: state.venueName,
          hallName: state.hallName,
        },
      });
    } catch (err) {
      setPaymentError(err instanceof ApiError ? err.message : "Payment step failed");
    } finally {
      setPaying(false);
    }
  }

  async function handleCancel() {
    if (!holdId) return;
    setCancelling(true);
    try {
      await cancelHold(holdId);
    } catch {
      // best-effort — the hold will expire on its own regardless
    } finally {
      navigate(backToSeats, {
        state: {
          eventTitle: state.eventTitle,
          venueName: state.venueName,
          hallName: state.hallName,
          startTime: state.startTime,
        },
      });
    }
  }

  if (!hold) {
    return (
      <div className="page">
        <h1>Checkout session not found</h1>
        <p className="muted">
          This page only works right after selecting seats — there's no way to reload a checkout in
          progress. Head back and pick your seats again.
        </p>
        <Link to="/browse">
          <button>Back to Now Showing</button>
        </Link>
      </div>
    );
  }

  return (
    <div className="page">
      <h1>Checkout</h1>
      <p className="muted">
        {state.eventTitle}
        {state.venueName && ` · ${state.venueName}`}
        {state.hallName && ` · ${state.hallName}`}
      </p>

      <div className="card">
        <h2>Your seats</h2>
        <ul className="list">
          {hold.lines.map((line) => (
            <li key={line.holdLineId}>
              <strong>{line.seatLabel}</strong> — {line.tierName}
              <div className="muted">&#8377;{formatInr(line.price)}</div>
            </li>
          ))}
        </ul>
        <div style={{ marginTop: "0.75rem", fontWeight: 700 }}>
          Total: &#8377;{formatInr(hold.totalPrice)}
        </div>
      </div>

      <div className="card">
        <h2>Hold expiry</h2>
        {expired ? (
          <p className="error" role="alert">
            Your hold has expired — these seats have been released. Go back and select seats again.
          </p>
        ) : (
          <p className={remainingSeconds <= 60 ? "error" : "muted"}>
            Seats held for <strong>{formatCountdown(remainingSeconds)}</strong>
          </p>
        )}
      </div>

      {!expired && (
        <div className="card">
          <h2>Payment</h2>
          <p className="muted">
            No real payment gateway is wired in for this demo — pick an outcome to simulate.
          </p>
          <div style={{ display: "flex", gap: "0.75rem", marginTop: "0.75rem" }}>
            <button onClick={() => handlePayment(true)} disabled={paying}>
              {paying ? "Processing..." : "Simulate successful payment"}
            </button>
            <button className="secondary" onClick={() => handlePayment(false)} disabled={paying}>
              Simulate failed payment
            </button>
          </div>
          {paymentError && (
            <p className="error" role="alert" style={{ marginTop: "0.75rem" }}>
              {paymentError}
            </p>
          )}
        </div>
      )}

      <button className="secondary" onClick={handleCancel} disabled={cancelling}>
        {cancelling ? "Cancelling..." : expired ? "Back to seats" : "Cancel and release seats"}
      </button>
    </div>
  );
}