import { useState, useEffect, useCallback, type FormEvent } from "react";
import { useParams, useLocation, Link } from "react-router-dom";
import {
  createTicketTier,
  listTicketTiers,
  assignSeatsToTier,
  type TicketTierResponse,
} from "../../api/ticketTiers";
import { getSeatMap, type SeatMapEntryResponse } from "../../api/seatMap";
import { publishEvent } from "../../api/organizerEvents";
import { groupByRow } from "../../utils/seatLabel";
import { ApiError } from "../../api/client";

interface LocationState {
  eventTitle?: string;
  hallName?: string;
  venueName?: string;
}

export function ShowtimeSetupPage() {
  const { eventId, showtimeId } = useParams<{ eventId: string; showtimeId: string }>();
  const location = useLocation();
  const context = (location.state ?? {}) as LocationState;

  const [tiers, setTiers] = useState<TicketTierResponse[]>([]);
  // Capacity isn't returned by the backend after creation — tracked locally
  // for tiers created in this session. Tiers found already existing when
  // this page loads (from an earlier session) show "capacity unknown".
  const [tierCapacity, setTierCapacity] = useState<Record<string, number>>({});
  const [tiersLoading, setTiersLoading] = useState(true);
  const [tiersError, setTiersError] = useState<string | null>(null);

  const [seatMap, setSeatMap] = useState<SeatMapEntryResponse[]>([]);
  const [seatMapLoading, setSeatMapLoading] = useState(true);
  const [seatMapError, setSeatMapError] = useState<string | null>(null);

  const [tierName, setTierName] = useState("");
  const [tierPrice, setTierPrice] = useState("");
  const [tierCapacityInput, setTierCapacityInput] = useState("");
  const [tierFormError, setTierFormError] = useState<string | null>(null);
  const [creatingTier, setCreatingTier] = useState(false);

  const [assignTierId, setAssignTierId] = useState("");
  const [selectedSeatIds, setSelectedSeatIds] = useState<Set<string>>(new Set());
  const [assignError, setAssignError] = useState<string | null>(null);
  const [assigning, setAssigning] = useState(false);

  const [publishError, setPublishError] = useState<string | null>(null);
  const [publishing, setPublishing] = useState(false);
  const [publishedStatus, setPublishedStatus] = useState<string | null>(null);

  const loadTiers = useCallback(async () => {
    if (!showtimeId) return;
    setTiersLoading(true);
    setTiersError(null);
    try {
      setTiers(await listTicketTiers(showtimeId));
    } catch (err) {
      setTiersError(err instanceof ApiError ? err.message : "Failed to load ticket tiers");
    } finally {
      setTiersLoading(false);
    }
  }, [showtimeId]);

  const loadSeatMap = useCallback(async () => {
    if (!showtimeId) return;
    setSeatMapLoading(true);
    setSeatMapError(null);
    try {
      setSeatMap(await getSeatMap(showtimeId));
    } catch (err) {
      setSeatMapError(err instanceof ApiError ? err.message : "Failed to load the seat map");
    } finally {
      setSeatMapLoading(false);
    }
  }, [showtimeId]);

  useEffect(() => {
    loadTiers();
    loadSeatMap();
  }, [loadTiers, loadSeatMap]);

  async function handleCreateTier(e: FormEvent) {
    e.preventDefault();
    setTierFormError(null);
    if (!showtimeId) return;

    const price = Number(tierPrice);
    const capacity = Number(tierCapacityInput);
    if (!tierName.trim() || !(price > 0) || !(capacity > 0)) {
      setTierFormError("Name, a positive price, and a positive capacity are all required.");
      return;
    }

    setCreatingTier(true);
    try {
      const created = await createTicketTier(showtimeId, {
        name: tierName.trim(),
        price,
        totalCapacity: capacity,
      });
      setTierCapacity((prev) => ({ ...prev, [created.id]: capacity }));
      setTierName("");
      setTierPrice("");
      setTierCapacityInput("");
      await loadTiers();
    } catch (err) {
      setTierFormError(err instanceof ApiError ? err.message : "Failed to create ticket tier");
    } finally {
      setCreatingTier(false);
    }
  }

  function assignedCountFor(tier: TicketTierResponse): number {
    return seatMap.filter((s) => s.tierName === tier.name).length;
  }

  function toggleSeat(seatId: string) {
    setSelectedSeatIds((prev) => {
      const next = new Set(prev);
      if (next.has(seatId)) next.delete(seatId);
      else next.add(seatId);
      return next;
    });
  }

  async function handleAssign() {
    setAssignError(null);
    if (!assignTierId || selectedSeatIds.size === 0) {
      setAssignError("Pick a tier and at least one seat.");
      return;
    }
    const target = tierCapacity[assignTierId];
    if (target !== undefined && selectedSeatIds.size !== target) {
      setAssignError(
        `This tier needs exactly ${target} seat${target === 1 ? "" : "s"} assigned — you've selected ${selectedSeatIds.size}.`
      );
      return;
    }

    setAssigning(true);
    try {
      await assignSeatsToTier(assignTierId, Array.from(selectedSeatIds));
      setSelectedSeatIds(new Set());
      await Promise.all([loadSeatMap(), loadTiers()]);
    } catch (err) {
      setAssignError(err instanceof ApiError ? err.message : "Failed to assign seats");
    } finally {
      setAssigning(false);
    }
  }

  async function handlePublish() {
    setPublishError(null);
    if (!eventId) return;
    setPublishing(true);
    try {
      const result = await publishEvent(eventId);
      setPublishedStatus(result.lifecycleStatus);
    } catch (err) {
      setPublishError(err instanceof ApiError ? err.message : "Failed to publish event");
    } finally {
      setPublishing(false);
    }
  }

  const unassignedSeats = seatMap.filter((s) => s.tierName === null);
  const rows = groupByRow(unassignedSeats);
  const assignedRows = groupByRow(seatMap.filter((s) => s.tierName !== null));
  const anyUnassigned = unassignedSeats.length > 0;

  return (
    <div className="page">
      <h1>Configure Showtime</h1>
      <p className="muted">
        {context.eventTitle ?? "Event"}
        {context.venueName && ` · ${context.venueName}`}
        {context.hallName && ` · ${context.hallName}`}
      </p>

      {/* --- Ticket tiers --- */}
      <div className="card">
        <h2>Ticket tiers</h2>

        <form className="form" onSubmit={handleCreateTier} style={{ marginBottom: "1rem" }}>
          <label>
            Name
            <input value={tierName} onChange={(e) => setTierName(e.target.value)} placeholder="Standard" />
          </label>
          <label>
            Price (&#8377;)
            <input
              type="number"
              min="1"
              step="0.01"
              value={tierPrice}
              onChange={(e) => setTierPrice(e.target.value)}
            />
          </label>
          <label>
            Seat capacity
            <input
              type="number"
              min="1"
              value={tierCapacityInput}
              onChange={(e) => setTierCapacityInput(e.target.value)}
            />
          </label>
          {tierFormError && <p className="error" role="alert">{tierFormError}</p>}
          <button type="submit" disabled={creatingTier}>
            {creatingTier ? "Adding..." : "Add tier"}
          </button>
        </form>

        {tiersLoading && <p className="muted">Loading tiers...</p>}
        {tiersError && <p className="error" role="alert">{tiersError}</p>}
        {!tiersLoading && !tiersError && tiers.length === 0 && (
          <p className="muted">No ticket tiers yet — add at least one above.</p>
        )}
        {tiers.length > 0 && (
          <ul className="list">
            {tiers.map((t) => {
              const assigned = assignedCountFor(t);
              const capacity = tierCapacity[t.id];
              return (
                <li key={t.id}>
                  <strong>{t.name}</strong> — &#8377;{t.price}
                  <div className="muted">
                    {capacity !== undefined
                      ? `${assigned} / ${capacity} seats assigned`
                      : `${assigned} seats assigned (capacity not tracked — created in an earlier session)`}
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      {/* --- Seat assignment --- */}
      <div className="card">
        <h2>Assign seats</h2>

        {tiers.length === 0 && <p className="muted">Add a ticket tier first.</p>}

        {tiers.length > 0 && (
          <>
            <label style={{ maxWidth: "320px", marginBottom: "1rem" }}>
              Assigning seats to
              <select value={assignTierId} onChange={(e) => setAssignTierId(e.target.value)}>
                <option value="" disabled>
                  Select a tier
                </option>
                {tiers.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                    {tierCapacity[t.id] !== undefined
                      ? ` (${assignedCountFor(t)}/${tierCapacity[t.id]})`
                      : ""}
                  </option>
                ))}
              </select>
            </label>

            {seatMapLoading && <p className="muted">Loading seat map...</p>}
            {seatMapError && <p className="error" role="alert">{seatMapError}</p>}

            {!seatMapLoading && !seatMapError && (
              <>
                {!anyUnassigned && (
                  <p className="muted">Every seat in this hall is already assigned to a tier.</p>
                )}
                {anyUnassigned && (
                  <div className="seat-picker">
                    {rows.map(({ row, seats }) => (
                      <div className="seat-picker__row" key={row}>
                        <span className="seat-picker__row-label">{row}</span>
                        <div className="seat-picker__seats">
                          {seats.map((s) => (
                            <button
                              key={s.seatId}
                              type="button"
                              className={`seat-btn ${selectedSeatIds.has(s.seatId) ? "seat-btn--selected" : ""}`}
                              onClick={() => toggleSeat(s.seatId)}
                              disabled={!assignTierId}
                              title={s.label}
                            >
                              {s.label}
                            </button>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                {assignedRows.length > 0 && (
                  <details style={{ marginTop: "1rem" }}>
                    <summary className="muted">Already-assigned seats ({seatMap.length - unassignedSeats.length})</summary>
                    <div className="seat-picker">
                      {assignedRows.map(({ row, seats }) => (
                        <div className="seat-picker__row" key={row}>
                          <span className="seat-picker__row-label">{row}</span>
                          <div className="seat-picker__seats">
                            {seats.map((s) => (
                              <button
                                key={s.seatId}
                                type="button"
                                className="seat-btn seat-btn--taken"
                                disabled
                                title={`${s.label} — ${s.tierName}`}
                              >
                                {s.label}
                              </button>
                            ))}
                          </div>
                        </div>
                      ))}
                    </div>
                  </details>
                )}

                <div style={{ marginTop: "1rem", display: "flex", alignItems: "center", gap: "1rem" }}>
                  <span className="muted">{selectedSeatIds.size} selected</span>
                  <button type="button" onClick={handleAssign} disabled={assigning || selectedSeatIds.size === 0}>
                    {assigning ? "Assigning..." : "Assign selected seats"}
                  </button>
                </div>
                {assignError && <p className="error" role="alert" style={{ marginTop: "0.5rem" }}>{assignError}</p>}
              </>
            )}
          </>
        )}
      </div>

      {/* --- Publish --- */}
      <div className="card">
        <h2>Publish</h2>
        {anyUnassigned && (
          <p className="muted">
            Heads up — {unassignedSeats.length} seat{unassignedSeats.length === 1 ? "" : "s"} in this hall
            {unassignedSeats.length === 1 ? " isn't" : " aren't"} assigned to any tier yet. You can still
            publish; unassigned seats just won't be bookable.
          </p>
        )}
        {publishedStatus ? (
          <p className="muted" style={{ color: "var(--color-success)" }}>
            Published — this event is now live at <Link to="/browse">Now Showing</Link>.
          </p>
        ) : (
          <>
            <button onClick={handlePublish} disabled={publishing || tiers.length === 0}>
              {publishing ? "Publishing..." : "Publish event"}
            </button>
            {publishError && <p className="error" role="alert" style={{ marginTop: "0.5rem" }}>{publishError}</p>}
          </>
        )}
      </div>
    </div>
  );
}