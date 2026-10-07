const DAY = /^(\d{4})-(\d{2})-(\d{2})$/;

function parts(day: string): [number, number, number] {
  const match = DAY.exec(day);
  if (!match) {
    throw new Error(`Invalid day: "${day}"`);
  }
  return [Number(match[1]), Number(match[2]), Number(match[3])];
}

function zoneOffsetMs(millis: number, timeZone: string): number {
  const wall = Object.fromEntries(
    new Intl.DateTimeFormat('en-US', {
      timeZone,
      hourCycle: 'h23',
      year: 'numeric',
      month: 'numeric',
      day: 'numeric',
      hour: 'numeric',
      minute: 'numeric',
      second: 'numeric',
    })
      .formatToParts(millis)
      .map(({ type, value }) => [type, Number(value)]),
  ) as Record<string, number>;
  const asUtc = Date.UTC(wall['year'], wall['month'] - 1, wall['day'], wall['hour'], wall['minute'], wall['second']);
  return asUtc - Math.floor(millis / 1000) * 1000;
}

function midnightMs(year: number, month: number, day: number, timeZone: string): number {
  const wallAsUtc = Date.UTC(year, month - 1, day);
  const first = wallAsUtc - zoneOffsetMs(wallAsUtc, timeZone);
  return wallAsUtc - zoneOffsetMs(first, timeZone);
}

// `day` is a calendar day (YYYY-MM-DD) in the store's zone, never the browser's.
export function storeDayStart(day: string, timeZone: string): string {
  const [year, month, date] = parts(day);
  return new Date(midnightMs(year, month, date, timeZone)).toISOString();
}

// The last microsecond of the day, for an API that compares with "less than or equal".
export function storeDayEnd(day: string, timeZone: string): string {
  const [year, month, date] = parts(day);
  const next = new Date(Date.UTC(year, month - 1, date + 1));
  const nextStart = midnightMs(next.getUTCFullYear(), next.getUTCMonth() + 1, next.getUTCDate(), timeZone);
  return new Date(nextStart - 1).toISOString().replace('.999Z', '.999999Z');
}

export function dayToDate(day: string): Date | null {
  const match = DAY.exec(day);
  if (!match) {
    return null;
  }
  const [year, month, date] = [Number(match[1]), Number(match[2]), Number(match[3])];
  if (year < 1000) {
    return null;
  }
  const result = new Date(year, month - 1, date);
  const real =
    result.getFullYear() === year && result.getMonth() === month - 1 && result.getDate() === date;
  return real ? result : null;
}

// Reads the calendar fields the user picked, so the day does not shift with the browser zone.
export function dateToDay(date: Date): string {
  const pad = (value: number, size = 2) => String(value).padStart(size, '0');
  return `${pad(date.getFullYear(), 4)}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
