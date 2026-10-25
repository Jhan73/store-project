import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject } from 'rxjs';
import { AuthStore } from '../../core/auth/auth-store';
import { API_ORIGIN } from '../../core/api/api-config';
import type { CustomerDisplay } from '../../core/api/api-types';
import { RealtimeClient } from '../../core/realtime/realtime-client';
import { ScreenWakeLock } from '../../core/screen/screen-wake-lock';
import { Display } from './display';
import { DisplayApi } from './display-api';
import { DisplayStore } from './display-store';

const DISPLAY = 'http://api.test/api/v1/board/display';

class FakeRealtime {
  readonly connected = signal(true);
  readonly isConnected = this.connected.asReadonly();
  readonly signals = new Subject<void>();

  refetch(): Observable<void> {
    return this.signals.asObservable();
  }
}

describe('Display', () => {
  let realtime: FakeRealtime;
  let wakeLock: { start: ReturnType<typeof vi.fn> };
  let http: HttpTestingController;
  let auth: { sessionExpiresAt: WritableSignal<number | null>; sessionEnded: WritableSignal<boolean> };

  beforeEach(() => {
    realtime = new FakeRealtime();
    auth = { sessionExpiresAt: signal<number | null>(null), sessionEnded: signal(false) };
    wakeLock = { start: vi.fn() };
    TestBed.overrideComponent(Display, {
      set: { providers: [DisplayStore, { provide: ScreenWakeLock, useValue: wakeLock }] },
    });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        DisplayApi,
        { provide: AuthStore, useValue: auth },
        { provide: RealtimeClient, useValue: realtime },
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(answer: CustomerDisplay | 'failure') {
    const fixture = TestBed.createComponent(Display);
    await fixture.whenStable();
    const request = http.expectOne(DISPLAY);
    if (answer === 'failure') {
      request.flush({}, { status: 500, statusText: 'Server Error' });
    } else {
      request.flush(answer);
    }
    await fixture.whenStable();
    return fixture;
  }

  const host = (fixture: ComponentFixture<Display>) => fixture.nativeElement as HTMLElement;
  const column = (fixture: ComponentFixture<Display>, name: 'preparing' | 'ready') =>
    host(fixture).querySelector(`[data-testid="column-${name}"]`) as HTMLElement;
  const entries = (fixture: ComponentFixture<Display>, name: 'preparing' | 'ready') =>
    Array.from(column(fixture, name).querySelectorAll('li')).map((item) =>
      Array.from(item.children).map((child) => child.textContent?.trim()),
    );

  it('shows the orders being prepared and the ones ready in two columns', async () => {
    const fixture = await open({
      preparing: [{ shortNumber: 'L-001', firstName: 'Ana' }],
      ready: [{ shortNumber: 'L-002', firstName: 'Beto' }],
    });

    expect(column(fixture, 'preparing').querySelector('h2')?.textContent).toContain('Preparando');
    expect(column(fixture, 'ready').querySelector('h2')?.textContent).toContain('Listos');
    expect(entries(fixture, 'preparing')).toEqual([['L-001', 'Ana']]);
    expect(entries(fixture, 'ready')).toEqual([['L-002', 'Beto']]);
  });

  it('shows only the number when the order has no name', async () => {
    const fixture = await open({ preparing: [{ shortNumber: 'L-003', firstName: null }], ready: [] });

    expect(entries(fixture, 'preparing')).toEqual([['L-003']]);
  });

  it('keeps the order of the lists', async () => {
    const fixture = await open({
      preparing: [
        { shortNumber: 'L-005', firstName: null },
        { shortNumber: 'L-004', firstName: null },
      ],
      ready: [],
    });

    expect(entries(fixture, 'preparing')).toEqual([['L-005'], ['L-004']]);
  });

  it('says there is nothing in a column once the first read is in', async () => {
    const fixture = await open({ preparing: [], ready: [] });

    expect(column(fixture, 'preparing').textContent).toContain('Sin pedidos');
    expect(column(fixture, 'ready').textContent).toContain('Sin pedidos');
  });

  it('renders text only: no buttons, links or form controls on a screen nobody operates', async () => {
    const fixture = await open({ preparing: [{ shortNumber: 'L-001', firstName: 'Ana' }], ready: [] });

    expect(host(fixture).querySelectorAll('button, a, input, select, textarea')).toHaveLength(0);
  });

  it('sizes both columns for the longer one so every number stays on screen', async () => {
    const many = Array.from({ length: 13 }, (_, index) => ({ shortNumber: `L-${index}`, firstName: null }));
    const fixture = await open({ preparing: many, ready: [{ shortNumber: 'L-99', firstName: null }] });

    const columns = host(fixture).querySelector<HTMLElement>('.columns')!;
    expect(columns.style.getPropertyValue('--display-scale')).toBe('0.75');
    expect(columns.style.getPropertyValue('--display-columns')).toBe('2');
    expect(host(fixture).querySelectorAll('li')).toHaveLength(14);
  });

  it('keeps the screen on', async () => {
    await open({ preparing: [], ready: [] });

    expect(wakeLock.start).toHaveBeenCalledTimes(1);
  });

  it('warns that the numbers may be out of date while the live connection is down', async () => {
    const fixture = await open({ preparing: [], ready: [] });
    expect(host(fixture).querySelector('[data-testid="stale"]')).toBeNull();

    realtime.connected.set(false);
    await fixture.whenStable();

    expect(host(fixture).querySelector('[data-testid="stale"]')?.textContent).toContain('Actualizando');
  });

  it('warns when the screen cannot be read', async () => {
    const fixture = await open('failure');

    expect(host(fixture).querySelector('[data-testid="stale"]')).not.toBeNull();
  });

  describe('session', () => {
    const DAY = 24 * 60 * 60 * 1000;

    it('warns a few days before the sign-in ends, without removing anything', async () => {
      auth.sessionExpiresAt.set(Date.now() + 2 * DAY + 60_000);
      const fixture = await open({ preparing: [{ shortNumber: 'L-001', firstName: 'Ana' }], ready: [] });

      expect(host(fixture).querySelector('[data-testid="session-expiry-banner"]')?.textContent).toContain('2 días');
      expect(entries(fixture, 'preparing')).toEqual([['L-001', 'Ana']]);
    });

    it('shows no warning while the end of the session is far or unknown', async () => {
      auth.sessionExpiresAt.set(Date.now() + 20 * DAY);
      const fixture = await open({ preparing: [], ready: [] });

      expect(host(fixture).querySelector('[data-testid="session-expiry-banner"]')).toBeNull();
    });

    it('keeps the last numbers and offers the sign-in once the session has ended', async () => {
      const fixture = await open({
        preparing: [{ shortNumber: 'L-001', firstName: 'Ana' }],
        ready: [{ shortNumber: 'L-002', firstName: null }],
      });

      auth.sessionEnded.set(true);
      await fixture.whenStable();

      expect(entries(fixture, 'preparing')).toEqual([['L-001', 'Ana']]);
      expect(entries(fixture, 'ready')).toEqual([['L-002']]);
      const notice = host(fixture).querySelector('[data-testid="session-ended"]');
      expect(notice?.textContent).toContain('La sesión terminó');
      const link = notice?.querySelector('a');
      expect(link?.getAttribute('href')).toContain('/login');
      expect(link?.getAttribute('href')).toContain('returnUrl=%2Fdisplay');
    });
  });
});
