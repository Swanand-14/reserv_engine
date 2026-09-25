import { useState, useEffect, useCallback } from "react";
import { useParams ,useNavigate} from "react-router-dom";
import { getEventDetail } from "../../api/events";
import { listShowtimesForEvent, type ShowtimeBrowseResponse } from "../../api/showtimes";
import type { EventBrowseResponse } from "../../types/event";
import { posterArtFor } from "../../utils/posterArt";
import { formatInr } from "../../utils/currency";
import { LoadingState } from "../../components/ui/LoadingState";
import { ErrorState } from "../../components/ui/ErrorState";
import { EmptyState } from "../../components/ui/EmptyState";


interface DateGroup {
  dateKey: string;
  label: string;
  showtimes: ShowtimeBrowseResponse[];
}

function localDateKey(iso: string): string {
  const d = new Date(iso);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function groupByDate(showtimes: ShowtimeBrowseResponse[]): DateGroup[] {
  const map = new Map<string, ShowtimeBrowseResponse[]>();
  for (const s of showtimes) {
    const key = localDateKey(s.startTime);
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(s);
  }
  return Array.from(map.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([dateKey, list]) => ({
      dateKey,
      label: new Date(list[0].startTime).toLocaleDateString(undefined, {
        weekday: "short",
        day: "numeric",
        month: "short",
      }),
      showtimes: list.sort((a, b) => a.startTime.localeCompare(b.startTime)),
    }));
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
}

export function EventDetailPage() {
  const { eventId } = useParams<{ eventId: string }>();

  const [event, setEvent] = useState<EventBrowseResponse | null>(null);
  const [showtimes, setShowtimes] = useState<ShowtimeBrowseResponse[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const navigate = useNavigate();

  const load = useCallback(async () => {
    if (!eventId) return;
    setLoading(true);
    setError(null);
    try {
      const [eventData, showtimeData] = await Promise.all([
        getEventDetail(eventId),
        listShowtimesForEvent(eventId),
      ]);
      setEvent(eventData);
      setShowtimes(showtimeData);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  }, [eventId]);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) return <LoadingState label="Loading event..." />;
  if (error) {
    return (
      <div className="page-wide">
        <ErrorState error={error} onRetry={load} />
      </div>
    );
  }
  if (!event) return null;

  const { hue, hueAlt } = posterArtFor(event.title);
  const heroStyle = {
    background: `linear-gradient(135deg, hsl(${hue} 68% 22%), hsl(${hueAlt} 55% 10%))`,
  };
  const dateGroups = showtimes ? groupByDate(showtimes) : [];
  const selectedShowtime = showtimes?.find((s) => s.id === selectedId) ?? null;

  return (
    <div>
      <div className="event-hero" style={heroStyle}>
        <div className="page-wide event-hero__inner">
          <h1 className="event-hero__title">{event.title}</h1>
          <div className="event-hero__meta">
            <span>
              {event.showtimeCount} {event.showtimeCount === 1 ? "showtime" : "showtimes"}
            </span>
            <span>·</span>
            <span>from &#8377;{formatInr(event.startingPrice)}</span>
          </div>
        </div>
      </div>

      <div className="page-wide">
        <h2>Showtimes</h2>

        {dateGroups.length === 0 && (
          <EmptyState
            title="No showtimes scheduled"
            message="This event doesn't have any live showtimes right now — check back soon."
          />
        )}

        {dateGroups.length > 0 && (
          <div className="showtime-groups">
            {dateGroups.map((group) => (
              <div className="showtime-group" key={group.dateKey}>
                <h3 className="showtime-group__label">{group.label}</h3>
                <div className="showtime-pills">
                  {group.showtimes.map((s) => (
                    <button
                      key={s.id}
                      type="button"
                      className={`showtime-pill ${selectedId === s.id ? "showtime-pill--selected" : ""}`}
                      onClick={() => setSelectedId(s.id)}
                    >
                      <span className="showtime-pill__time">{formatTime(s.startTime)}</span>
                      <span className="showtime-pill__venue">
                        {s.venueName} · {s.hallName}
                      </span>
                      {s.startingPrice != null && (
                        <span className="showtime-pill__price">from &#8377;{formatInr(s.startingPrice)}</span>
                      )}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {selectedShowtime && (
        <div className="selection-bar">
          <div className="page-wide selection-bar__inner">
            <div>
              <strong>{formatTime(selectedShowtime.startTime)}</strong>{" "}
              <span className="muted">
                · {selectedShowtime.venueName} · {selectedShowtime.hallName}
              </span>
            </div>
<button
  onClick={() =>
    navigate(`/events/${eventId}/showtimes/${selectedShowtime.id}/seats`, {
      state: {
        eventTitle: event.title,
        venueName: selectedShowtime.venueName,
        hallName: selectedShowtime.hallName,
        startTime: selectedShowtime.startTime,
      },
    })
  }
>
  Continue to seat selection
</button>
          </div>
        </div>
      )}
    </div>
  );
}