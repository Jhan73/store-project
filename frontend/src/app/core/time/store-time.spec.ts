import { clockOffsetMs, correctedNow, elapsedMinutes, formatInStoreZone } from './store-time';

describe('formatInStoreZone', () => {
  const instant = '2026-09-18T01:30:00Z';

  it('shows the store wall clock, not the browser one', () => {
    const lima = formatInStoreZone(instant, 'America/Lima', { hour: '2-digit', minute: '2-digit', hour12: false });
    const tokyo = formatInStoreZone(instant, 'Asia/Tokyo', { hour: '2-digit', minute: '2-digit', hour12: false });

    expect(lima).toBe('20:30');
    expect(tokyo).toBe('10:30');
  });

  it('rolls the calendar date over with the zone', () => {
    const day = (zone: string) =>
      formatInStoreZone(instant, zone, { year: 'numeric', month: '2-digit', day: '2-digit' });

    expect(day('America/Lima')).toBe('17/09/2026');
    expect(day('Asia/Tokyo')).toBe('18/09/2026');
  });

  it('rejects an invalid instant', () => {
    expect(() => formatInStoreZone('not a date', 'America/Lima')).toThrow();
  });
});

describe('clockOffsetMs', () => {
  it('is how far the server is ahead of the device', () => {
    const device = Date.parse('2026-09-18T10:00:00Z');

    expect(clockOffsetMs('Fri, 18 Sep 2026 10:00:05 GMT', device)).toBe(5000);
    expect(clockOffsetMs('Fri, 18 Sep 2026 09:59:50 GMT', device)).toBe(-10000);
  });

  it('is zero when the header is missing or unparsable', () => {
    expect(clockOffsetMs(null, 1_000)).toBe(0);
    expect(clockOffsetMs('garbage', 1_000)).toBe(0);
  });
});

describe('correctedNow and elapsedMinutes', () => {
  it('applies the offset to the device clock', () => {
    expect(correctedNow(1_000, 5_000)).toBe(6_000);
  });

  it('counts whole elapsed minutes on the server timeline', () => {
    const sentAt = '2026-09-18T10:00:00Z';
    const deviceNow = Date.parse('2026-09-18T10:07:30Z') - 60_000;

    expect(elapsedMinutes(sentAt, deviceNow, 60_000)).toBe(7);
  });

  it('never goes negative', () => {
    expect(elapsedMinutes('2026-09-18T10:00:10Z', Date.parse('2026-09-18T10:00:00Z'), 0)).toBe(0);
  });
});
