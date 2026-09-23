import { useState, useEffect, useCallback, type FormEvent } from "react";
import { listMyEvents, type OrganizerEventResponse } from "../../api/organizerEvents";
import { listMyVenues, listHallsForVenue, type VenueResponse, type HallResponse } from "../../api/venue";
import {
  createShowtime,
  listMyShowtimes,
  type ShowtimeBrowseResponse,
} from "../../api/showtimes";
import { ApiError } from "../../api/client";
import { useNavigate, Link } from "react-router-dom";

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  });
}

export function ShowtimeCreatePage() {
  // Events (picker)
  const [events, setEvents] = useState<OrganizerEventResponse[]>([]);
  const [eventsLoading, setEventsLoading] = useState(true);
  const [eventsError, setEventsError] = useState<string | null>(null);
  const [eventId, setEventId] = useState("");

  // Existing showtimes for the selected event
  const [existing, setExisting] = useState<ShowtimeBrowseResponse[]>([]);
  const [existingLoading, setExistingLoading] = useState(false);
  const [existingError, setExistingError] = useState<string | null>(null);

  // Venues (picker)
  const [venues, setVenues] = useState<VenueResponse[]>([]);
  const [venuesLoading, setVenuesLoading] = useState(true);
  const [venuesError, setVenuesError] = useState<string | null>(null);
  const [venueId, setVenueId] = useState("");

  // Halls (dependent on venue)
  const [halls, setHalls] = useState<HallResponse[]>([]);
  const [hallsLoading, setHallsLoading] = useState(false);
  const [hallsError, setHallsError] = useState<string | null>(null);
  const [hallId, setHallId] = useState("");

  // Showtime window
  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");

  const [formError, setFormError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [lastCreatedId, setLastCreatedId] = useState<string | null>(null);
const navigate = useNavigate();

  // --- initial loads ---
  useEffect(() => {
    (async () => {
      setEventsLoading(true);
      setEventsError(null);
      try {
        setEvents(await listMyEvents());
      } catch (err) {
        setEventsError(err instanceof ApiError ? err.message : "Failed to load events");
      } finally {
        setEventsLoading(false);
      }
    })();

    (async () => {
      setVenuesLoading(true);
      setVenuesError(null);
      try {
        setVenues(await listMyVenues());
      } catch (err) {
        setVenuesError(err instanceof ApiError ? err.message : "Failed to load venues");
      } finally {
        setVenuesLoading(false);
      }
    })();
  }, []);

  // --- existing showtimes for the chosen event ---
  const loadExisting = useCallback(async (forEventId: string) => {
    if (!forEventId) {
      setExisting([]);
      return;
    }
    setExistingLoading(true);
    setExistingError(null);
    try {
      setExisting(await listMyShowtimes(forEventId));
    } catch (err) {
      setExistingError(err instanceof ApiError ? err.message : "Failed to load showtimes");
    } finally {
      setExistingLoading(false);
    }
  }, []);

  useEffect(() => {
    loadExisting(eventId);
  }, [eventId, loadExisting]);

  // --- halls for the chosen venue ---
  useEffect(() => {
    setHallId("");
    if (!venueId) {
      setHalls([]);
      return;
    }
    (async () => {
      setHallsLoading(true);
      setHallsError(null);
      try {
        setHalls(await listHallsForVenue(venueId));
      } catch (err) {
        setHallsError(err instanceof ApiError ? err.message : "Failed to load halls");
      } finally {
        setHallsLoading(false);
      }
    })();
  }, [venueId]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);
    setSuccessMessage(null);

    if (!eventId || !hallId || !startTime || !endTime) {
      setFormError("Fill in every field before creating the showtime.");
      return;
    }
    if (endTime <= startTime) {
      setFormError("End time has to be after the start time.");
      return;
    }

    setSubmitting(true);
    try {
      const created = await createShowtime(eventId, { hallId, startTime, endTime });
      setSuccessMessage("Showtime created. Add ticket tiers and seat assignments next.");
      setLastCreatedId(created.id);
      setStartTime("");
      setEndTime("");
      await loadExisting(eventId);
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Failed to create showtime");
    } finally {
      setSubmitting(false);
    }
  }

  const selectedEvent = events.find((e) => e.id === eventId);

  return (
    <div className="page">
      <h1>Create Showtime</h1>
      <p className="muted">Pick an event and a hall, then set the screening window.</p>

      <form className="card form" onSubmit={handleSubmit} style={{ maxWidth: "480px" }}>
        <label>
          Event
          {eventsLoading && <span className="muted">Loading events...</span>}
          {eventsError && <p className="error" role="alert">{eventsError}</p>}
          {!eventsLoading && !eventsError && (
            <select value={eventId} onChange={(e) => setEventId(e.target.value)} required>
              <option value="" disabled>
                Select an event
              </option>
              {events.map((ev) => (
                <option key={ev.id} value={ev.id}>
                  {ev.title} ({ev.lifecycleStatus.toLowerCase()})
                </option>
              ))}
            </select>
          )}
        </label>

        <label>
          Venue
          {venuesLoading && <span className="muted">Loading venues...</span>}
          {venuesError && <p className="error" role="alert">{venuesError}</p>}
          {!venuesLoading && !venuesError && (
            <select value={venueId} onChange={(e) => setVenueId(e.target.value)} required>
              <option value="" disabled>
                Select a venue
              </option>
              {venues.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.name}
                </option>
              ))}
            </select>
          )}
        </label>

        <label>
          Hall
          {hallsLoading && <span className="muted">Loading halls...</span>}
          {hallsError && <p className="error" role="alert">{hallsError}</p>}
          {!hallsLoading && !hallsError && (
            <select
              value={hallId}
              onChange={(e) => setHallId(e.target.value)}
              required
              disabled={!venueId || halls.length === 0}
            >
              <option value="" disabled>
                {venueId ? "Select a hall" : "Pick a venue first"}
              </option>
              {halls.map((h) => (
                <option key={h.id} value={h.id}>
                  {h.name}
                </option>
              ))}
            </select>
          )}
        </label>

        <label>
          Start time
          <input
            type="datetime-local"
            value={startTime}
            onChange={(e) => setStartTime(e.target.value)}
            required
          />
        </label>

        <label>
          End time
          <input
            type="datetime-local"
            value={endTime}
            onChange={(e) => setEndTime(e.target.value)}
            required
          />
        </label>

        {formError && <p className="error" role="alert">{formError}</p>}
        {successMessage && (
          <p className="muted" style={{ color: "var(--color-success)" }}>
            {successMessage}
          </p>
        )}

        <button type="submit" disabled={submitting}>
          {submitting ? "Creating..." : "Create showtime"}
        </button>
        {lastCreatedId && eventId && (
  <button
    type="button"
    className="secondary"
    onClick={() =>
      navigate(`/organizer/events/${eventId}/showtimes/${lastCreatedId}/setup`, {
        state: {
          eventTitle: selectedEvent?.title,
          venueName: venues.find((v) => v.id === venueId)?.name,
          hallName: halls.find((h) => h.id === hallId)?.name,
        },
      })
    }
  >
    Add ticket tiers &amp; seats →
  </button>
)}
      </form>

      {eventId && (
        <div className="card">
          <h2>Existing showtimes{selectedEvent ? ` for ${selectedEvent.title}` : ""}</h2>
          {existingLoading && <p className="muted">Loading...</p>}
          {existingError && <p className="error" role="alert">{existingError}</p>}
          {!existingLoading && !existingError && existing.length === 0 && (
            <p className="muted">No showtimes yet for this event.</p>
          )}
          {existing.length > 0 && (
            <ul className="list">
              {existing.map((s) => (
                <li key={s.id}>
                  <strong>{s.venueName}</strong> — {s.hallName}
                  <div className="muted">
                    {formatDateTime(s.startTime)} → {formatDateTime(s.endTime)}
                    {s.startingPrice != null && <> · from &#8377;{s.startingPrice}</>}
                  </div>
                  <Link
  to={`/organizer/events/${eventId}/showtimes/${s.id}/setup`}
  state={{ eventTitle: selectedEvent?.title, venueName: s.venueName, hallName: s.hallName }}
>
  Manage tiers &amp; seats →
</Link>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}