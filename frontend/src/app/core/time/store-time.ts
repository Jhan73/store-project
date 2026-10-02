const DEFAULT_LOCALE = 'es-PE';

export function formatInStoreZone(
  instant: string,
  timeZone: string,
  options: Intl.DateTimeFormatOptions = { dateStyle: 'short', timeStyle: 'short' },
  locale = DEFAULT_LOCALE,
): string {
  const millis = Date.parse(instant);
  if (Number.isNaN(millis)) {
    throw new Error(`Invalid instant: "${instant}"`);
  }
  return new Intl.DateTimeFormat(locale, { ...options, timeZone }).format(millis);
}

// Positive when the server clock is ahead of the device clock.
export function clockOffsetMs(serverDateHeader: string | null, deviceNowMs: number): number {
  const serverMs = serverDateHeader === null ? NaN : Date.parse(serverDateHeader);
  return Number.isNaN(serverMs) ? 0 : serverMs - deviceNowMs;
}

export function correctedNow(deviceNowMs: number, offsetMs: number): number {
  return deviceNowMs + offsetMs;
}

export function elapsedMinutes(instant: string, deviceNowMs: number, offsetMs: number): number {
  const elapsedMs = correctedNow(deviceNowMs, offsetMs) - Date.parse(instant);
  return Math.max(0, Math.floor(elapsedMs / 60_000));
}
