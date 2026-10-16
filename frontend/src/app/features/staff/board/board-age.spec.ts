import { ageLevel, ageStartOf, DEFAULT_AGE_THRESHOLDS, thresholdsOf } from './board-age';

describe('board age', () => {
  const thresholds = { warningMinutes: 5, lateMinutes: 10 };

  it.each([
    [0, 'normal'],
    [4, 'normal'],
    [5, 'warning'],
    [9, 'warning'],
    [10, 'late'],
    [45, 'late'],
  ] as const)('puts %i minutes at the %s level', (minutes, level) => {
    expect(ageLevel(minutes, thresholds)).toBe(level);
  });

  it('follows the thresholds the store configured', () => {
    expect(ageLevel(3, { warningMinutes: 2, lateMinutes: 3 })).toBe('late');
  });

  it('defaults to 5 and 10 minutes', () => {
    expect(DEFAULT_AGE_THRESHOLDS).toEqual(thresholds);
  });

  it('reads the thresholds from the API response', () => {
    expect(thresholdsOf({ boardWarningMinutes: 3, boardLateMinutes: 6, timeZone: 'America/Lima' })).toEqual({
      warningMinutes: 3,
      lateMinutes: 6,
    });
  });

  it('counts a scheduled card from the moment it fires and any other from the moment it was sent', () => {
    expect(ageStartOf({ sentAt: '2026-10-13T10:00:00Z', fireAt: null })).toBe('2026-10-13T10:00:00Z');
    expect(ageStartOf({ sentAt: '2026-10-13T10:00:00Z', fireAt: '2026-10-13T12:00:00Z' })).toBe(
      '2026-10-13T12:00:00Z',
    );
  });
});
