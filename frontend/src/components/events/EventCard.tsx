import { Link } from "react-router-dom";
import type { EventBrowseResponse } from "../../types/event";
import { posterArtFor } from "../../utils/posterArt";

export function EventCard({ event }: { event: EventBrowseResponse }) {
  const { hue, hueAlt } = posterArtFor(event.title);
  const artStyle = {
    background: `linear-gradient(135deg, hsl(${hue} 68% 20%), hsl(${hueAlt} 55% 11%))`,
  };
  const priceFormatted = new Intl.NumberFormat("en-IN", {
    maximumFractionDigits: 0,
  }).format(event.startingPrice);

  return (
    <Link to={`/events/${event.id}`} className="event-card">
      <div className="event-card__art" style={artStyle}>
        <span className="event-card__title">{event.title}</span>
      </div>

      <div className="event-card__seam">
        <span className="event-card__notch event-card__notch--left" />
        <span className="event-card__notch event-card__notch--right" />
      </div>

      <div className="event-card__info">
        <div className="event-card__meta">
          <span className="event-card__showtimes">
            {event.showtimeCount} {event.showtimeCount === 1 ? "showtime" : "showtimes"}
          </span>
          <span className="event-card__price">
            <span>from</span>&#8377;{priceFormatted}
          </span>
        </div>
      </div>
    </Link>
  );
}
