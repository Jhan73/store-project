import { isPlatformBrowser } from '@angular/common';
import {
  computed,
  effect,
  inject,
  Injectable,
  InjectionToken,
  PLATFORM_ID,
  signal,
  untracked,
} from '@angular/core';
import { EMPTY, map, merge, Observable, Subject } from 'rxjs';
import { Router } from '@angular/router';
import { API_ORIGIN } from '../api/api-config';
import { AuthStore } from '../auth/auth-store';
import { redirectToLogin } from '../auth/session-redirect';
import { encodeFrame, StompFrame, StompParser } from './stomp-frame';

export type RealtimeState = 'idle' | 'connecting' | 'connected' | 'reconnecting';

// Signals only carry what changed (type + IDs); REST stays the source of truth.
export interface RealtimeMessage {
  readonly type: string;
  readonly ids: Readonly<Record<string, string>>;
}

export const WEB_SOCKET_FACTORY = new InjectionToken<(url: string, protocols: string[]) => WebSocket>(
  'WEB_SOCKET_FACTORY',
  { providedIn: 'root', factory: () => (url, protocols) => new WebSocket(url, protocols) },
);

const HEART_BEAT_MS = 20_000;
const INITIAL_DELAY_MS = 1_000;
const MAX_DELAY_MS = 30_000;
// A connection counts as healthy only after surviving this long; a broker that accepts CONNECT and then
// rejects a SUBSCRIBE would otherwise reset the backoff on every cycle.
const STABLE_AFTER_MS = 10_000;

interface Topic {
  readonly subject: Subject<RealtimeMessage>;
  readonly id: string;
  refs: number;
}

function parseSignal(body: string | undefined): RealtimeMessage | null {
  try {
    const value: unknown = JSON.parse(body ?? '');
    if (typeof value === 'object' && value !== null && typeof (value as { type?: unknown }).type === 'string') {
      const { type, ids } = value as { type: string; ids?: Record<string, string> };
      return { type, ids: ids ?? {} };
    }
  } catch {
    // A body that is not JSON is not one of our signals.
  }
  return null;
}

@Injectable({ providedIn: 'root' })
export class RealtimeClient {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly createSocket = inject(WEB_SOCKET_FACTORY);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly origin = inject(API_ORIGIN);
  private readonly url = `${this.origin.replace(/^http/, 'ws')}/ws`;
  private readonly topics = new Map<string, Topic>();
  private readonly connected = new Subject<void>();
  private readonly status = signal<RealtimeState>('idle');

  private socket: WebSocket | null = null;
  private parser = new StompParser();
  private identity: string | null = null;
  private usedToken = false;
  private attempts = 0;
  private nextTopicId = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | undefined;
  private pingTimer: ReturnType<typeof setInterval> | undefined;
  private watchdogTimer: ReturnType<typeof setInterval> | undefined;
  private stableTimer: ReturnType<typeof setTimeout> | undefined;
  private resyncPending = false;
  private lastReceivedAt = 0;

  readonly state = this.status.asReadonly();
  readonly isConnected = computed(() => this.status() === 'connected');

  constructor() {
    // The broker validates the token only at CONNECT, so a different user needs a new connection.
    effect(() => {
      const userId = this.auth.session()?.userId ?? null;
      untracked(() => {
        if (this.socket && userId !== this.identity) {
          this.restart();
        }
      });
    });
  }

  topic(destination: string): Observable<RealtimeMessage> {
    if (!this.isBrowser) {
      return EMPTY;
    }
    return new Observable<RealtimeMessage>((subscriber) => {
      const topic = this.acquire(destination);
      const subscription = topic.subject.subscribe(subscriber);
      return () => {
        subscription.unsubscribe();
        this.release(destination);
      };
    });
  }

  // Emits when REST should be re-read: on every signal and on every (re)connection. The first connection counts
  // too, because it closes the gap between the initial fetch and the moment the subscription became active.
  refetch(destination: string): Observable<void> {
    return merge(this.topic(destination), this.connected).pipe(map(() => undefined));
  }

  private acquire(destination: string): Topic {
    let topic = this.topics.get(destination);
    if (!topic) {
      topic = { subject: new Subject(), id: `sub-${this.nextTopicId++}`, refs: 0 };
      this.topics.set(destination, topic);
      if (this.status() === 'connected') {
        this.sendSubscribe(destination, topic);
      }
    }
    topic.refs++;
    if (!this.socket && this.reconnectTimer === undefined) {
      this.open();
    }
    return topic;
  }

  private release(destination: string): void {
    const topic = this.topics.get(destination);
    if (!topic || --topic.refs > 0) {
      return;
    }
    this.topics.delete(destination);
    if (this.status() === 'connected') {
      this.send({ command: 'UNSUBSCRIBE', headers: { id: topic.id } });
    }
    if (this.topics.size === 0) {
      this.shutDown();
    }
  }

  private open(): void {
    this.status.set(this.attempts === 0 ? 'connecting' : 'reconnecting');
    this.parser = new StompParser();
    this.identity = this.auth.session()?.userId ?? null;
    const socket = this.createSocket(this.url, ['v12.stomp']);
    this.socket = socket;
    socket.onopen = () => this.sendConnect(socket);
    socket.onmessage = (event) => this.receive(socket, String(event.data));
    socket.onclose = () => this.closed(socket);
  }

  private sendConnect(socket: WebSocket): void {
    const token = this.auth.accessToken();
    this.usedToken = token !== null;
    this.send({
      command: 'CONNECT',
      headers: {
        'accept-version': '1.2',
        host: new URL(this.origin).host,
        'heart-beat': `${HEART_BEAT_MS},${HEART_BEAT_MS}`,
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
    }, socket);
  }

  private receive(socket: WebSocket, data: string): void {
    if (socket !== this.socket) {
      return;
    }
    this.lastReceivedAt = Date.now();
    for (const frame of this.parser.push(data)) {
      switch (frame.command) {
        case 'CONNECTED':
          this.onConnected(frame);
          break;
        case 'MESSAGE':
          this.onMessage(frame);
          break;
        case 'ERROR':
          this.onError();
          break;
      }
    }
  }

  private onConnected(frame: StompFrame): void {
    this.status.set('connected');
    this.startHeartBeats(frame.headers['heart-beat']);
    for (const [destination, topic] of this.topics) {
      this.sendSubscribe(destination, topic);
    }
    // After a recent failure, wait for the connection to prove itself so a flapping one does not hammer REST.
    if (this.attempts === 0) {
      this.connected.next();
    } else {
      this.resyncPending = true;
    }
    this.stableTimer = setTimeout(() => {
      this.attempts = 0;
      if (this.resyncPending) {
        this.resyncPending = false;
        this.connected.next();
      }
    }, STABLE_AFTER_MS);
  }

  private onMessage(frame: StompFrame): void {
    const signal = parseSignal(frame.body);
    const topic = frame.headers['destination'] ? this.topics.get(frame.headers['destination']) : undefined;
    if (signal && topic) {
      topic.subject.next(signal);
    }
  }

  // Most likely an expired token: renew it so the next attempt connects with a fresh one (or anonymously).
  private onError(): void {
    if (this.status() !== 'connected' && this.usedToken) {
      void this.auth.refresh().then((outcome) => {
        if (outcome === 'rejected') {
          redirectToLogin(this.router);
        }
      });
    }
    this.drop();
  }

  private closed(socket: WebSocket): void {
    if (socket !== this.socket) {
      return;
    }
    this.socket = null;
    this.stopHeartBeats();
    if (this.topics.size === 0) {
      this.status.set('idle');
      return;
    }
    this.status.set('reconnecting');
    const delay = Math.min(MAX_DELAY_MS, INITIAL_DELAY_MS * 2 ** this.attempts);
    this.attempts++;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = undefined;
      if (this.topics.size > 0) {
        this.open();
      }
    }, delay * (1 + 0.2 * Math.random()));
  }

  // A dead connection may take the browser a long time to report as closed, so do not wait for it.
  private drop(): void {
    const socket = this.socket;
    if (socket) {
      socket.close();
      this.closed(socket);
    }
  }

  private restart(): void {
    const previous = this.socket;
    this.socket = null;
    this.stopHeartBeats();
    previous?.close();
    this.attempts = 0;
    this.open();
  }

  private shutDown(): void {
    clearTimeout(this.reconnectTimer);
    this.reconnectTimer = undefined;
    this.stopHeartBeats();
    const socket = this.socket;
    this.socket = null;
    this.attempts = 0;
    this.status.set('idle');
    socket?.close();
  }

  private startHeartBeats(serverHeader: string | undefined): void {
    this.stopHeartBeats();
    const [serverSends = 0, serverWants = 0] = (serverHeader ?? '0,0').split(',').map(Number);
    const pingEvery = serverWants > 0 ? Math.max(HEART_BEAT_MS, serverWants) : 0;
    const expectEvery = serverSends > 0 ? Math.max(HEART_BEAT_MS, serverSends) : 0;
    this.lastReceivedAt = Date.now();
    if (pingEvery > 0) {
      this.pingTimer = setInterval(() => this.socket?.send('\n'), pingEvery);
    }
    if (expectEvery > 0) {
      this.watchdogTimer = setInterval(() => {
        if (Date.now() - this.lastReceivedAt > expectEvery * 2) {
          this.drop();
        }
      }, expectEvery / 2);
    }
  }

  private stopHeartBeats(): void {
    clearInterval(this.pingTimer);
    clearInterval(this.watchdogTimer);
    clearTimeout(this.stableTimer);
    this.resyncPending = false;
  }

  private sendSubscribe(destination: string, topic: Topic): void {
    this.send({ command: 'SUBSCRIBE', headers: { id: topic.id, destination, ack: 'auto' } });
  }

  private send(frame: StompFrame, socket: WebSocket | null = this.socket): void {
    socket?.send(encodeFrame(frame));
  }
}
