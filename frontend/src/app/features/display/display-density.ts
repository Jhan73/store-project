export interface DisplayDensity {
  // Multiplies the type and spacing of every entry.
  readonly scale: number;
  // Columns of entries inside each of the two lists.
  readonly columns: number;
}

// Entries per column that fit a 1080p screen at each scale, and how many columns that scale may use before
// the next, smaller one is needed. The last step has no cap: it adds columns instead of hiding entries.
const STEPS = [
  { scale: 1, rows: 6, maxColumns: 1 },
  { scale: 0.75, rows: 8, maxColumns: 2 },
  { scale: 0.5, rows: 12, maxColumns: 2 },
  { scale: 0.35, rows: 17, maxColumns: 3 },
  { scale: 0.25, rows: 24, maxColumns: Infinity },
] as const;

export function densityOf(count: number): DisplayDensity {
  for (const step of STEPS) {
    const columns = Math.max(1, Math.ceil(count / step.rows));
    if (columns <= step.maxColumns) {
      return { scale: step.scale, columns };
    }
  }
  throw new Error('unreachable: the last step has no column cap');
}
