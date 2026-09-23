import { useState, useEffect } from "react";
import { listMyVenues, type VenueResponse } from "../../api/venue";
import { ApiError } from "../../api/client";

export function VenuesPage() {
  const [venues, setVenues] = useState<VenueResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadVenues();
  }, []);

  async function loadVenues() {
    setLoading(true);
    setError(null);
    try {
      const data = await listMyVenues();
      setVenues(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load venues");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page">
      <h1>Venues</h1>
      <p className="muted">
        Venues are seeded for this demo — pick one of these when setting up a showtime.
      </p>

      <div className="card">
        {loading && <p className="muted">Loading...</p>}
        {error && <p className="error" role="alert">{error}</p>}
        {!loading && !error && venues.length === 0 && (
          <p className="muted">No venues found for your account.</p>
        )}
        {venues.length > 0 && (
          <ul className="list">
            {venues.map((v) => (
              <li key={v.id}>
                <strong>{v.name}</strong>
                <div className="muted">
                  {v.hallCount} {v.hallCount === 1 ? "hall" : "halls"} · {v.totalSeatCount}{" "}
                  seats total · added {new Date(v.createdAt).toLocaleDateString()}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}