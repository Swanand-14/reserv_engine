import { useEffect, useState, useCallback } from "react";
import { listPublishedEvents } from "../../api/events";
import type { EventBrowseResponse } from "../../types/event";
import { EventCard } from "../../components/events/EventCard";
import { LoadingState } from "../../components/ui/LoadingState";
import { ErrorState } from "../../components/ui/ErrorState";
import { EmptyState } from "../../components/ui/EmptyState";

export function BrowsePage() {
  const [events, setEvents] = useState<EventBrowseResponse[] | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await listPublishedEvents();
      setEvents(data);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <div className="page-wide">
      <div className="browse-hero">
        <h1>Now Showing</h1>
        {events && (
          <p className="browse-hero__count">
            {events.length} {events.length === 1 ? "event" : "events"} live right now
          </p>
        )}
      </div>

      {loading && <LoadingState label="Loading what's on..." />}
      {!loading && error !== null && <ErrorState error={error} onRetry={load} />}
      {!loading && error === null && events && events.length === 0 && (
        <EmptyState
          title="Nothing on the marquee yet"
          message="Check back soon — published showtimes will appear here as soon as they go live."
        />
      )}
      {!loading && error === null && events && events.length > 0 && (
        <div className="event-grid">
          {events.map((event) => (
            <EventCard key={event.id} event={event} />
          ))}
        </div>
      )}
    </div>
  );
}
