import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subscription } from 'rxjs';
import { API_ORIGIN } from '../api/api-config';
import { AuthStore } from '../auth/auth-store';
import { RealtimeClient, RealtimeMessage, WEB_SOCKET_FACTORY } from './realtime-client';

class FakeSocket {
  static instances: FakeSocket[] = [];
  readonly sent: string[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => void) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  closed = false;

  constructor(
    readonly url: string,
    readonly protocols: string[],
  ) {
    FakeSocket.instances.push(this);
  }

  send(data: string): void {
    this.sent.push(data);
  }

  close(): void {
    this.closed = true;
    this.onclose?.();
  }

  open(): void {
    this.onopen?.();
  }

  receive(frame: string): void {
    this.onmessage?.({ data: frame });
  }

  connected(heartBeat = '20000,20000'): void {
    this.receive(`CONNECTED\nversion:1.2\nheart-beat:${heartBeat}\n\n\0`);
  }

  get frames(): string[] {
    return this.sent.filter((data) => data !== '\n');
  }
}

const last = () => FakeSocket.instances[FakeSocket.instances.length - 1];
const message = (destination: string, body: string, subscription = 'sub-0') =>
  `MESSAGE\ndestination:${destination}\nsubscription:${subscription}\nmessage-id:1\n\n${body}\0`;

describe('RealtimeClient', () => {
  let client: RealtimeClient;
  let http: HttpTestingController;
  const subscriptions: Subscription[] = [];

  function setup(platform = 'browser') {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_ORIGIN, useValue: 'https://api.test' },
        { provide: PLATFORM_ID, useValue: platform },
        {
          provide: WEB_SOCKET_FACTORY,
          useValue: (url: string, protocols: string[]) =>
            new FakeSocket(url, protocols) as unknown as WebSocket,
        },
      ],
    });
    client = TestBed.inject(RealtimeClient);
    http = TestBed.inject(HttpTestingController);
  }

  beforeEach(() => {
    FakeSocket.instances = [];
    vi.useFakeTimers();
    vi.spyOn(Math, 'random').mockReturnValue(0);
  });

  afterEach(() => {
    subscriptions.splice(0).forEach((subscription) => subscription.unsubscribe());
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  function watch(destination: string, into: RealtimeMessage[] = []) {
    subscriptions.push(client.topic(destination).subscribe((value) => into.push(value)));
    return into;
  }

  it('does not open a connection until something subscribes', () => {
    setup();

    expect(FakeSocket.instances).toHaveLength(0);
    expect(client.state()).toBe('idle');
  });

  it('connects over wss to /ws speaking STOMP 1.2', () => {
    setup();

    watch('/topic/catalog');

    expect(last().url).toBe('wss://api.test/ws');
    expect(last().protocols).toEqual(['v12.stomp']);
    expect(client.state()).toBe('connecting');
  });

  it('connects anonymously when nobody is signed in', () => {
    setup();
    watch('/topic/catalog');

    last().open();

    expect(last().frames[0]).toContain('CONNECT\n');
    expect(last().frames[0]).toContain('accept-version:1.2');
    expect(last().frames[0]).not.toContain('Authorization');
  });

  it('sends the access token in the CONNECT frame when signed in', async () => {
    setup();
    const login = TestBed.inject(AuthStore).login({ email: 'a@b.pe', password: 'x' });
    http
      .expectOne('https://api.test/api/v1/auth/login')
      .flush({ accessToken: 'jwt-1', tokenType: 'Bearer', userId: 'u1', role: 'CASHIER' });
    await login;

    watch('/topic/catalog');
    last().open();

    expect(last().frames[0]).toContain('Authorization:Bearer jwt-1');
  });

  it('subscribes once connected and delivers each signal', () => {
    setup();
    const received = watch('/topic/catalog');
    last().open();
    expect(last().frames).toHaveLength(1);

    last().connected();

    expect(client.state()).toBe('connected');
    expect(client.isConnected()).toBe(true);
    expect(last().frames[1]).toBe('SUBSCRIBE\nid:sub-0\ndestination:/topic/catalog\nack:auto\n\n\0');
    last().receive(
      message('/topic/catalog', '{"type":"PRODUCT_CHANGED","ids":{"productId":"p1"}}'),
    );
    expect(received).toEqual([{ type: 'PRODUCT_CHANGED', ids: { productId: 'p1' } }]);
  });

  it('ignores a message body that is not a signal', () => {
    setup();
    const received = watch('/topic/catalog');
    last().open();
    last().connected();

    last().receive(message('/topic/catalog', 'not json'));

    expect(received).toEqual([]);
  });

  it('shares one STOMP subscription among subscribers of a destination', () => {
    setup();
    const first = watch('/topic/catalog');
    const second = watch('/topic/catalog');
    last().open();
    last().connected();

    last().receive(message('/topic/catalog', '{"type":"X","ids":{}}'));

    expect(last().frames.filter((frame) => frame.startsWith('SUBSCRIBE'))).toHaveLength(1);
    expect(first).toHaveLength(1);
    expect(second).toHaveLength(1);
  });

  it('subscribes immediately when the connection already exists', () => {
    setup();
    watch('/topic/catalog');
    last().open();
    last().connected();

    watch('/topic/store-status');

    expect(last().frames.at(-1)).toContain('destination:/topic/store-status');
  });

  it('unsubscribes and closes the connection when nobody is listening any more', () => {
    setup();
    const subscription = client.topic('/topic/catalog').subscribe();
    last().open();
    last().connected();

    subscription.unsubscribe();

    expect(last().frames.at(-1)).toBe('UNSUBSCRIBE\nid:sub-0\n\n\0');
    expect(last().closed).toBe(true);
    expect(client.state()).toBe('idle');
    vi.advanceTimersByTime(60_000);
    expect(FakeSocket.instances).toHaveLength(1);
  });

  describe('reconnection', () => {
    it('reconnects with a growing delay and resubscribes', () => {
      setup();
      watch('/topic/catalog');
      last().open();
      last().connected();

      last().close();
      expect(client.state()).toBe('reconnecting');
      expect(client.isConnected()).toBe(false);
      vi.advanceTimersByTime(999);
      expect(FakeSocket.instances).toHaveLength(1);
      vi.advanceTimersByTime(1);
      expect(FakeSocket.instances).toHaveLength(2);

      last().open();
      last().close();
      vi.advanceTimersByTime(1_999);
      expect(FakeSocket.instances).toHaveLength(2);
      vi.advanceTimersByTime(1);
      expect(FakeSocket.instances).toHaveLength(3);

      last().open();
      last().connected();
      expect(last().frames[1]).toContain('SUBSCRIBE');
      expect(client.state()).toBe('connected');
    });

    it('caps the delay at 30 seconds and starts over after a successful connection', () => {
      setup();
      watch('/topic/catalog');
      for (let attempt = 0; attempt < 8; attempt++) {
        last().close();
        vi.advanceTimersByTime(30_000);
      }
      const attempts = FakeSocket.instances.length;
      last().close();
      vi.advanceTimersByTime(29_999);
      expect(FakeSocket.instances).toHaveLength(attempts);
      vi.advanceTimersByTime(1);
      expect(FakeSocket.instances).toHaveLength(attempts + 1);

      last().open();
      last().connected();
      last().close();
      vi.advanceTimersByTime(1_000);
      expect(FakeSocket.instances).toHaveLength(attempts + 2);
    });

    it('asks to re-fetch on every message and every (re)connection', () => {
      setup();
      let refetches = 0;
      subscriptions.push(client.refetch('/topic/catalog').subscribe(() => refetches++));

      last().open();
      last().connected();
      expect(refetches).toBe(1);

      last().receive(message('/topic/catalog', '{"type":"X","ids":{}}'));
      expect(refetches).toBe(2);

      last().close();
      vi.advanceTimersByTime(1_000);
      last().open();
      last().connected();
      expect(refetches).toBe(3);
    });
  });

  describe('heart-beats', () => {
    it('pings on the negotiated interval', () => {
      setup();
      watch('/topic/catalog');
      last().open();
      last().connected();
      const before = last().sent.length;

      vi.advanceTimersByTime(20_000);

      expect(last().sent.slice(before)).toEqual(['\n']);
    });

    it('drops a connection that went silent and reconnects', () => {
      setup();
      watch('/topic/catalog');
      last().open();
      last().connected();
      const silent = last();

      vi.advanceTimersByTime(60_000);

      expect(silent.closed).toBe(true);
      expect(FakeSocket.instances).toHaveLength(2);
    });

    it('stays up while the server keeps beating', () => {
      setup();
      watch('/topic/catalog');
      last().open();
      last().connected();

      for (let beat = 0; beat < 6; beat++) {
        vi.advanceTimersByTime(20_000);
        last().receive('\n');
      }

      expect(last().closed).toBe(false);
    });
  });

  describe('authentication', () => {
    async function signIn() {
      const login = TestBed.inject(AuthStore).login({ email: 'a@b.pe', password: 'x' });
      http
        .expectOne('https://api.test/api/v1/auth/login')
        .flush({ accessToken: 'jwt-1', tokenType: 'Bearer', userId: 'u1', role: 'CASHIER' });
      await login;
    }

    it('refreshes the session when the broker rejects the token', async () => {
      setup();
      await signIn();
      watch('/topic/catalog');
      last().open();

      last().receive('ERROR\nmessage:Invalid or expired token\n\n\0');

      http
        .expectOne('https://api.test/api/v1/auth/refresh')
        .flush({ accessToken: 'jwt-2', tokenType: 'Bearer', userId: 'u1', role: 'CASHIER' });
      await vi.advanceTimersByTimeAsync(1_000);
      last().open();
      expect(last().frames[0]).toContain('Authorization:Bearer jwt-2');
    });

    it('reconnects when the signed-in user changes', async () => {
      setup();
      watch('/topic/catalog');
      TestBed.tick();
      last().open();
      last().connected();

      await signIn();
      TestBed.tick();

      expect(FakeSocket.instances).toHaveLength(2);
      last().open();
      expect(last().frames[0]).toContain('Authorization:Bearer jwt-1');
    });

    it('keeps the connection when only the token is renewed', async () => {
      setup();
      await signIn();
      watch('/topic/catalog');
      TestBed.tick();
      last().open();
      last().connected();

      const refresh = TestBed.inject(AuthStore).refresh();
      http
        .expectOne('https://api.test/api/v1/auth/refresh')
        .flush({ accessToken: 'jwt-2', tokenType: 'Bearer', userId: 'u1', role: 'CASHIER' });
      await refresh;
      TestBed.tick();

      expect(FakeSocket.instances).toHaveLength(1);
    });
  });

  it('never opens a socket on the server', () => {
    setup('server');

    watch('/topic/catalog');

    expect(FakeSocket.instances).toHaveLength(0);
    expect(client.state()).toBe('idle');
  });
});
