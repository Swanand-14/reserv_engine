import { useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import { listMyEvents, publishEvent, type OrganizerEventResponse } from "../../api/organizerEvents";
import { ApiError } from "../../api/client";

export function EventsPage() {
  const [events, setEvents] = useState<OrganizerEventResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [publishingId, setPublishingId] = useState<string | null>(null);
  const [publishError, setPublishError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setEvents(await listMyEvents());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load events");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function handlePublish(eventId: string) {
    setPublishError(null);
    setPublishingId(eventId);
    try {
      await publishEvent(eventId);
      await load();
    } catch (err) {
      setPublishError(err instanceof ApiError ? err.message : "Failed to publish event");
    } finally {
      setPublishingId(null);
    }
  }

  return (
    <div className="page">
      <h1>Events</h1>
      <p className="muted">
        Events are seeded for this demo — configure showtimes and publish from here.
      </p>

      {loading && <p className="muted">Loading...</p>}
      {error && <p className="error" role="alert">{error}</p>}
      {publishError && <p className="error" role="alert">{publishError}</p>}

      {!loading && !error && events.length === 0 && (
        <p className="muted">No events found for your account.</p>
      )}

      {events.length > 0 && (
        <div className="reservation-list">
          {events.map((ev) => (
            <div className="card reservation-card" key={ev.id}>
              <div className="reservation-card__header">
                <div>
                  <h3>{ev.title}</h3>
                  <p className="muted">
                    {ev.showtimeCount} {ev.showtimeCount === 1 ? "showtime" : "showtimes"} · added{" "}
                    {new Date(ev.createdAt).toLocaleDateString()}
                  </p>
                </div>
                <span
                  className={`status-badge ${
                    ev.lifecycleStatus === "PUBLISHED" ? "status-badge--confirmed" : ""
                  }`}
                >
                  {ev.lifecycleStatus.toLowerCase()}
                </span>
              </div>

              <div className="reservation-card__footer">
                <Link to="/organizer/showtimes/new" state={{ presetEventId: ev.id }}>
                  Manage showtimes →
                </Link>
                <div style={{ display: "flex", alignItems: "center", gap: "0.75rem" }}>
                  {ev.lifecycleStatus === "PUBLISHED" && (
                    <Link to={`/events/${ev.id}`}>View live listing →</Link>
                  )}
                  {ev.lifecycleStatus === "DRAFT" && (
                    <button
                      className="secondary"
                      onClick={() => handlePublish(ev.id)}
                      disabled={publishingId === ev.id || ev.showtimeCount === 0}
                      title={ev.showtimeCount === 0 ? "Add a showtime first" : undefined}
                    >
                      {publishingId === ev.id ? "Publishing..." : "Publish"}
                    </button>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}