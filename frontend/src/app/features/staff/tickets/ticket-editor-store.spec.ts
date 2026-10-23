import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import {
  createEnvironmentInjector,
  EnvironmentInjector,
  signal,
  type WritableSignal,
} from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { MessageService } from 'primeng/api';
import { Observable, of, Subject } from 'rxjs';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { RecordPaymentRequest, Role, Ticket, TicketLine } from '../../../core/api/api-types';
import { AuthStore } from '../../../core/auth/auth-store';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { RealtimeClient } from '../../../core/realtime/realtime-client';
import { pen, problem } from '../../../testing/register-fixtures';
import { RegisterApi } from '../register/register-api';
import { TicketEditorStore } from './ticket-editor-store';
import { TicketsApi } from './tickets-api';

const BASE = 'http://api.test/api/v1';
const TICKET = `${BASE}/tickets/k1`;

class FakeRealtime {
  readonly connected = signal(false);
  readonly isConnected = this.connected.asReadonly();
  private readonly topics = new Map<string, Subject<void>>();

  refetch(destination: string): Observable<void> {
    let subject = this.topics.get(destination);
    if (!subject) {
      subject = new Subject<void>();
      this.topics.set(destination, subject);
    }
    return subject.asObservable();
  }

  fire(destination: string): void {
    this.topics.get(destination)?.next();
  }
}

function line(id: string, overrides: Partial<TicketLine> = {}): TicketLine {
  return {
    id,
    productId: 'p1',
    productName: 'Naranja',
    quantity: 1,
    unitPrice: pen('10.00'),
    lineTotal: pen('10.00'),
    modifiers: [],
    note: null,
    allergy: false,
    state: 'UNSENT',
    sentAt: null,
    voidNote: null,
    voidReasonCode: null,
    waste: false,
    ...overrides,
  };
}

function ticket(overrides: Partial<Ticket> = {}): Ticket {
  return {
    id: 'k1',
    number: 12,
    type: 'TABLE',
    status: 'OPEN',
    tableId: 't1',
    tableName: 'Mesa 1',
    counterName: null,
    note: null,
    billRequested: false,
    quickSale: false,
    openedAt: '2026-10-13T10:00:00Z',
    lines: [line('l1', { state: 'SENT', sentAt: '2026-10-13T10:05:00Z' })],
    total: pen('10.00'),
    paid: pen('0.00'),
    balance: pen('10.00'),
    payments: [],
    ...overrides,
  } as Ticket;
}

const payRequest: RecordPaymentRequest = {
  method: 'CASH',
  amount: pen('10.00'),
  receivedAmount: pen('10.00'),
} as RecordPaymentRequest;

describe('TicketEditorStore', () => {
  let http: HttpTestingController;
  let messages: MessageService;
  let realtime: FakeRealtime;
  let role: WritableSignal<Role | null>;
  let screen: EnvironmentInjector;
  let store: TicketEditorStore;

  beforeEach(() => {
    realtime = new FakeRealtime();
    role = signal<Role | null>('CASHIER');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        MessageService,
        TicketsApi,
        RegisterApi,
        { provide: RealtimeClient, useValue: realtime },
        { provide: AuthStore, useValue: { role, session: () => null } },
        { provide: API_ORIGIN, useValue: 'http://api.test' },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ id: 'k1' })) } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    messages = TestBed.inject(MessageService);
    screen = createEnvironmentInjector([TicketEditorStore], TestBed.inject(EnvironmentInjector));
    store = screen.get(TicketEditorStore);
    http.expectOne(TICKET).flush(ticket());
    http.expectOne(`${BASE}/catalog/menu`).flush({ categories: [] });
    http.expectOne(`${BASE}/board/tickets`).flush([]);
    http.expectOne(`${BASE}/board/ready`).flush([]);
  });

  afterEach(() => http.verify());

  describe('money commands that outlive the screen', () => {
    it('keeps a payment going after the screen is destroyed and does not touch the old screen', () => {
      const paid = vi.fn();
      const added = vi.spyOn(messages, 'add');
      store.pay(payRequest, paid);
      const request = http.expectOne(`${TICKET}/payments`);

      screen.destroy();
      expect(request.cancelled).toBe(false);
      request.flush(ticket({ status: 'CLOSED', paid: pen('10.00'), balance: pen('0.00') }));

      expect(paid).not.toHaveBeenCalled();
      expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    });

    it('reports a payment that fails after the screen is gone, without reading again', () => {
      const added = vi.spyOn(messages, 'add');
      store.pay(payRequest, vi.fn());
      const request = http.expectOne(`${TICKET}/payments`);

      screen.destroy();
      request.flush(problem(503, 'common.service-unavailable'), {
        status: 503,
        statusText: 'Unavailable',
      });

      expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    });

    it('keeps a payment void and a sale void going after the screen is destroyed', () => {
      store.voidPayment('pay1', { reasonCode: 'Mistake', note: null } as never, vi.fn());
      const paymentVoid = http.expectOne(`${TICKET}/payments/pay1/void`);
      store.voidSale({ reasonCode: 'Mistake', note: null } as never, vi.fn());
      const saleVoid = http.expectOne(`${TICKET}/void`);

      screen.destroy();

      expect(paymentVoid.cancelled).toBe(false);
      expect(saleVoid.cancelled).toBe(false);
      paymentVoid.flush(ticket());
      saleVoid.flush(ticket());
    });

    it('still completes a payment while the screen is alive', () => {
      const paid = vi.fn();
      store.pay(payRequest, paid);

      http
        .expectOne(`${TICKET}/payments`)
        .flush(ticket({ status: 'CLOSED', paid: pen('10.00'), balance: pen('0.00') }));

      expect(paid).toHaveBeenCalled();
      expect(store.ticket()?.status).toBe('CLOSED');
    });
  });
});
