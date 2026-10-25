import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AuthStore } from './auth-store';
import { SESSION_WARNING_DAYS, SessionExpiryWarning } from './session-expiry-warning';

const DAY = 24 * 60 * 60 * 1000;
const START = Date.parse('2026-10-25T10:00:00Z');

describe('SessionExpiryWarning', () => {
  const endsAt = signal<number | null>(null);

  function setup() {
    TestBed.configureTestingModule({
      providers: [{ provide: AuthStore, useValue: { sessionExpiresAt: endsAt.asReadonly() } }],
    });
    return TestBed.inject(SessionExpiryWarning);
  }

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(START);
    endsAt.set(null);
  });

  afterEach(() => vi.useRealTimers());

  it('stays quiet while the session end is unknown', () => {
    expect(setup().daysLeft()).toBeNull();
  });

  it('stays quiet while more than the warning window is left', () => {
    const warning = setup();
    endsAt.set(START + (SESSION_WARNING_DAYS + 1) * DAY);

    expect(warning.daysLeft()).toBeNull();
  });

  it('counts whole days inside the window', () => {
    const warning = setup();
    endsAt.set(START + 2.5 * DAY);

    expect(warning.daysLeft()).toBe(2);
  });

  it('says zero days when less than a day is left or the end has passed', () => {
    const warning = setup();
    endsAt.set(START + 5 * 60 * 60 * 1000);
    expect(warning.daysLeft()).toBe(0);

    endsAt.set(START - 1_000);
    expect(warning.daysLeft()).toBe(0);
  });

  it('moves on with the clock', () => {
    const warning = setup();
    endsAt.set(START + 3 * DAY + 60_000);
    expect(warning.daysLeft()).toBeNull();

    vi.advanceTimersByTime(2 * 60_000);

    expect(warning.daysLeft()).toBe(2);
  });
});
