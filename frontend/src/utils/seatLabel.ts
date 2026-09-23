export interface SeatLabelParts {
  row: string;
  seatNumber: number;
}

const LABEL_PATTERN = /^([A-Za-z]+)0*(\d+)$/;

export function parseSeatLabel(label: string): SeatLabelParts {
  const match = LABEL_PATTERN.exec(label.trim());
  if (!match) {
    return { row: label, seatNumber: 0 };
  }
  return { row: match[1].toUpperCase(), seatNumber: Number(match[2]) };
}

export function groupByRow<T extends { label: string }>(seats: T[]): Array<{ row: string; seats: T[] }> {
  const rows = new Map<string, T[]>();
  for (const seat of seats) {
    const { row } = parseSeatLabel(seat.label);
    if (!rows.has(row)) rows.set(row, []);
    rows.get(row)!.push(seat);
  }
  return Array.from(rows.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([row, rowSeats]) => ({
      row,
      seats: rowSeats.sort(
        (a, b) => parseSeatLabel(a.label).seatNumber - parseSeatLabel(b.label).seatNumber
      ),
    }));
}