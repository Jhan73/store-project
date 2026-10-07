import { dayToDate, dateToDay, storeDayEnd, storeDayStart } from './store-day';

describe('storeDayStart', () => {
  it('is the instant the store calendar day begins', () => {
    expect(storeDayStart('2026-10-05', 'America/Lima')).toBe('2026-10-05T05:00:00.000Z');
    expect(storeDayStart('2026-10-05', 'Asia/Tokyo')).toBe('2026-10-04T15:00:00.000Z');
  });

  it('does not depend on the browser zone', () => {
    expect(storeDayStart('2026-01-01', 'UTC')).toBe('2026-01-01T00:00:00.000Z');
  });

  it('follows a daylight saving change inside the zone', () => {
    expect(storeDayStart('2026-03-08', 'America/New_York')).toBe('2026-03-08T05:00:00.000Z');
    expect(storeDayStart('2026-03-09', 'America/New_York')).toBe('2026-03-09T04:00:00.000Z');
  });
});

describe('storeDayEnd', () => {
  it('is the last instant of the store day, so the whole day is included', () => {
    expect(storeDayEnd('2026-10-05', 'America/Lima')).toBe('2026-10-06T04:59:59.999999Z');
  });

  it('stays before the start of the next day', () => {
    const end = Date.parse(storeDayEnd('2026-10-05', 'America/Lima'));
    expect(end).toBeLessThan(Date.parse(storeDayStart('2026-10-06', 'America/Lima')));
    expect(end + 2).toBeGreaterThanOrEqual(Date.parse(storeDayStart('2026-10-06', 'America/Lima')));
  });

  it('is shorter on the day clocks go forward', () => {
    expect(storeDayEnd('2026-03-08', 'America/New_York')).toBe('2026-03-09T03:59:59.999999Z');
  });
});

describe('day strings', () => {
  it('round-trips a calendar day without shifting it', () => {
    const date = dayToDate('2026-10-05');
    expect(date).not.toBeNull();
    expect(dateToDay(date!)).toBe('2026-10-05');
  });

  it('rejects text that is not a real calendar day', () => {
    expect(dayToDate('2026-13-01')).toBeNull();
    expect(dayToDate('2026-02-30')).toBeNull();
    expect(dayToDate('05/10/2026')).toBeNull();
    expect(dayToDate('')).toBeNull();
  });
});
