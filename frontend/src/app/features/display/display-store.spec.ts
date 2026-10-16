import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { createEnvironmentInjector, EnvironmentInjector, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Observable, Subject } from 'rxjs';
import { API_ORIGIN } from '../../core/api/api-config';
import type { CustomerDisplay } from '../../core/api/api-types';
import { RealtimeClient } from '../../core/realtime/realtime-client';
import { DisplayApi } from './display-api';
import { DisplayStore } from './display-store';

const DISPLAY = 'http://api.test/api/v1/board/display';

class FakeRealtime {
  readonly connected = signal(false);
  readonly isConnected = this.connected.asReadonly();
  readonly signals = new Subject<void>();
  readonly destinations: string[] = [];

  refetch(destination: string): Observable<void> {
    this.destinations.push(destination);
    return this.signals.asObservable();
  }
}

const failure = { status: 500, statusText: 'Server Error' };
const board = (preparing: string[], ready: string[] = []): CustomerDisplay => ({
  preparing: preparing.map((shortNumber) => ({ shortNumber, firstName: null })),
  ready: ready.map((shortNumber) => ({ shortNumber, firstName: null })),
});

describe('DisplayStore', () => {
  let realtime: FakeRealtime;
  let http: HttpTestingController;
  let screen: EnvironmentInjector;
  let screenClosed: boolean;

  beforeEach(() => {
    realtime = new FakeRealtime();
    screenClosed = false;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        DisplayApi,
        { provide: RealtimeClient, useValue: realtime },
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    closeScreen();
    http.verify();
  });

  function build(): DisplayStore {
    screen = createEnvironmentInjector([DisplayStore], TestBed.inject(EnvironmentInjector));
    return screen.get(DisplayStore);
  }

  function closeScreen() {
    if (!screenClosed) {
      screenClosed = true;
      screen.destroy();
    }
  }

  it('listens to the display topic and reads the screen once', () => {
    const store = build();

    http.expectOne(DISPLAY).flush({
      preparing: [{ shortNumber: 'L-001', firstName: 'Ana' }],
      ready: [{ shortNumber: 'L-002', firstName: null }],
    });

    expect(realtime.destinations).toEqual(['/topic/display']);
    expect(store.preparing()).toEqual([{ shortNumber: 'L-001', firstName: 'Ana' }]);
    expect(store.ready()).toEqual([{ shortNumber: 'L-002', firstName: null }]);
    expect(store.loaded()).toBe(true);
    expect(store.failed()).toBe(false);
  });

  it('reads again on every signal and keeps only the latest answer', () => {
    const store = build();
    http.expectOne(DISPLAY).flush(board([]));

    realtime.signals.next();
    realtime.signals.next();
    const [first, second] = http.match(DISPLAY);
    second.flush(board(['second']));
    first.flush(board(['first']));

    expect(store.preparing().map((entry) => entry.shortNumber)).toEqual(['second']);
  });

  it('flags a failed first read and recovers on the next one', () => {
    const store = build();
    http.expectOne(DISPLAY).flush({}, failure);

    expect(store.failed()).toBe(true);
    expect(store.loaded()).toBe(false);

    realtime.signals.next();
    http.expectOne(DISPLAY).flush(board(['L-001']));
    expect(store.failed()).toBe(false);
    expect(store.preparing()).toHaveLength(1);
  });

  it('keeps what it shows when a later read fails, but says it may be out of date', () => {
    const store = build();
    http.expectOne(DISPLAY).flush(board(['L-001']));

    realtime.signals.next();
    http.expectOne(DISPLAY).flush({}, failure);

    expect(store.preparing()).toHaveLength(1);
    expect(store.failed()).toBe(true);
  });

  it('reads every minute while connected so a scheduled order appears when it fires', () => {
    vi.useFakeTimers();
    const store = build();
    http.expectOne(DISPLAY).flush(board([]));
    realtime.connected.set(true);
    TestBed.tick();

    vi.advanceTimersByTime(60_000);

    http.expectOne(DISPLAY).flush(board(['W-001']));
    expect(store.preparing().map((entry) => entry.shortNumber)).toEqual(['W-001']);
  });

  it('does not poll while disconnected', () => {
    vi.useFakeTimers();
    build();
    http.expectOne(DISPLAY).flush(board([]));

    vi.advanceTimersByTime(120_000);

    http.expectNone(DISPLAY);
  });

  it('exposes the connection so the screen can say when it is not live', () => {
    const store = build();
    http.expectOne(DISPLAY).flush(board([]));
    expect(store.connected()).toBe(false);

    realtime.connected.set(true);

    expect(store.connected()).toBe(true);
  });

  it('stops listening and polling when it is destroyed', () => {
    vi.useFakeTimers();
    build();
    http.expectOne(DISPLAY).flush(board([]));
    realtime.connected.set(true);
    TestBed.tick();

    closeScreen();
    vi.advanceTimersByTime(120_000);
    realtime.signals.next();

    http.expectNone(DISPLAY);
  });
});
