import { useState, useEffect, type FormEvent } from "react";
import { createVenue, listMyVenues, type VenueResponse } from "../../api/venue";
import { ApiError } from "../../api/client";

export function VenuesPage() {
  const [venues, setVenues] = useState<VenueResponse[]>([]);
  const [venuesLoading, setVenuesLoading] = useState(true);
  const [venuesError, setVenuesError] = useState<string | null>(null);

  const [name, setName] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    loadVenues();
  }, []);

  async function loadVenues() {
    setVenuesLoading(true);
    setVenuesError(null);
    try {
      const data = await listMyVenues();
      setVenues(data);
    } catch (err) {
      setVenuesError(err instanceof ApiError ? err.message : "Failed to load venues");
    } finally {
      setVenuesLoading(false);
    }
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);
    setSubmitting(true);
    try {
      await createVenue(name);
      setName("");
      await loadVenues(); // re-fetch so the list reflects the real backend state, not an optimistic guess
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Failed to create venue");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page">
      <h1>Venues</h1>

      <form className="card form" onSubmit={handleSubmit}>
        <label>
          Venue name
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
        </label>
        {formError && <p className="error" role="alert">{formError}</p>}
        <button type="submit" disabled={submitting}>
          {submitting ? "Creating..." : "Create venue"}
        </button>
      </form>

      <div className="card">
        <h2>Your venues</h2>
        {venuesLoading && <p className="muted">Loading...</p>}
        {venuesError && <p className="error" role="alert">{venuesError}</p>}
        {!venuesLoading && !venuesError && venues.length === 0 && (
          <p className="muted">No venues yet — create one above.</p>
        )}
        {venues.length > 0 && (
          <ul className="list">
            {venues.map((v) => (
              <li key={v.id}>
                <strong>{v.name}</strong>
                <span className="muted"> — {v.id}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}