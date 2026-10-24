import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import {
  createEnvironmentInjector,
  EnvironmentInjector,
  signal,
  type WritableSignal,
} from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, type ParamMap, Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { BehaviorSubject, EMPTY, Observable, Subject } from 'rxjs';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { RecordPaymentRequest, Role, Ticket, TicketLine } from '../../../core/api/api-types';
import { AuthStore } from '../../../core/auth/auth-store';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { RealtimeClient } from '../../../core/realtime/realtime-client';
import { UndoNotice } from '../../../shared/state/undo-notice';
import { UNDO_WINDOW_MS } from '../../../shared/state/undo-queue';
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
    compReasonCode: null,
    compNote: null,
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
  let router: { navigate: ReturnType<typeof vi.fn> };
  let role: WritableSignal<Role | null>;
  let params: BehaviorSubject<ParamMap>;
  let signOutHooks: (() => Promise<void> | void)[];
  let ensureFresh: ReturnType<typeof vi.fn>;
  let screen: EnvironmentInjector;
  let store: TicketEditorStore;

  beforeEach(() => {
    realtime = new FakeRealtime();
    role = signal<Role | null>('CASHIER');
    router = { navigate: vi.fn().mockResolvedValue(true) };
    params = new BehaviorSubject(convertToParamMap({ id: 'k1' }));
    signOutHooks = [];
    ensureFresh = vi.fn().mockResolvedValue(undefined);
    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: router },
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        MessageService,
        TicketsApi,
        RegisterApi,
        { provide: RealtimeClient, useValue: realtime },
        {
          provide: AuthStore,
          useValue: {
            role,
            session: () => null,
            ensureFresh,
            onSignOut: (hook: () => Promise<void> | void) => {
              signOutHooks.push(hook);
              return () => undefined;
            },
          },
        },
        { provide: API_ORIGIN, useValue: 'http://api.test' },
        { provide: ActivatedRoute, useValue: { paramMap: params } },
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

  describe('reading the ticket', () => {
    it('holds the ticket and the menu', () => {
      expect(store.ticket()?.id).toBe('k1');
      expect(store.menu()).toEqual({ categories: [] });
      expect(store.missing()).toBe(false);
      expect(store.failed()).toBe(false);
    });

    it('shows a sent line as ready once the board lists it by its source line', () => {
      store.load();
      http.expectOne(TICKET).flush(ticket());
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([
        { lines: [{ id: 'board-line', sourceLineId: 'l1', state: 'READY' }] },
      ]);

      const [sent] = store.ticket()!.lines;
      expect(store.stateOf(sent)).toBe('READY');
    });

    it('says the ticket is missing when the API does not know it', () => {
      store.load();
      http
        .expectOne(TICKET)
        .flush(problem(404, 'instore.ticket-not-found'), { status: 404, statusText: 'Not Found' });
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([]);

      expect(store.missing()).toBe(true);
      expect(store.failed()).toBe(false);
    });

    it('reports a failed read and lets it be retried', () => {
      const added = vi.spyOn(messages, 'add');
      store.load();
      http
        .expectOne(TICKET)
        .flush(problem(500, 'common.internal-error'), { status: 500, statusText: 'Error' });
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([]);

      expect(store.failed()).toBe(true);
      expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));

      store.load();
      http.expectOne(TICKET).flush(ticket());
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([]);
      expect(store.failed()).toBe(false);
    });

    it('applies only the latest ticket read', () => {
      store.load();
      const first = http.expectOne(TICKET);
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([]);
      store.load();
      const second = http.expectOne(TICKET);
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([]);

      second.flush(ticket({ number: 99 }));
      first.flush(ticket({ number: 1 }));

      expect(store.ticket()?.number).toBe(99);
    });

    it('enables the actions only after a read made on the live connection', () => {
      expect(store.enabled()).toBe(false);

      realtime.connected.set(true);
      TestBed.tick();
      realtime.fire('/topic/tables');
      http.expectOne(TICKET).flush(ticket());

      expect(store.enabled()).toBe(true);
    });
  });

  describe('what the roles may void', () => {
    const sentLine = line('l1', { state: 'SENT', sentAt: '2026-10-13T10:05:00Z' });

    function markReady() {
      store.load();
      http.expectOne(TICKET).flush(ticket());
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([
        { lines: [{ id: 'b1', sourceLineId: 'l1', state: 'READY' }] },
      ]);
    }

    it('lets a server void a sent line, but not a ready one', () => {
      role.set('SERVER');
      expect(store.mayVoid(sentLine)).toBe(true);

      markReady();

      expect(store.mayVoid(sentLine)).toBe(false);
      expect(store.mustAskCashier(sentLine)).toBe(true);
    });

    it('lets a cashier void a ready line', () => {
      markReady();

      expect(store.mayVoid(sentLine)).toBe(true);
      expect(store.mustAskCashier(sentLine)).toBe(false);
    });

    it('never offers a void on an unsent line', () => {
      expect(store.mayVoid(line('l2'))).toBe(false);
      expect(store.mustAskCashier(line('l2'))).toBe(false);
    });
  });

  describe('commands', () => {
    function rereadAfterFailure() {
      http.expectOne(TICKET).flush(ticket());
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([]);
    }

    it('adds a line under one key, reuses it on a retry and releases it after success', () => {
      const added = vi.fn();
      const request = { productId: 'p1', quantity: 1, modifierOptionIds: [], note: null, allergy: false };

      store.addLine(request, added);
      const first = http.expectOne(`${TICKET}/lines`);
      const key = first.request.headers.get('Idempotency-Key');
      first.flush(problem(503, 'common.service-unavailable'), { status: 503, statusText: 'Unavailable' });
      rereadAfterFailure();

      store.addLine(request, added);
      const second = http.expectOne(`${TICKET}/lines`);
      expect(second.request.headers.get('Idempotency-Key')).toBe(key);
      second.flush(ticket());
      expect(added).toHaveBeenCalledTimes(1);

      store.addLine(request, added);
      const third = http.expectOne(`${TICKET}/lines`);
      expect(third.request.headers.get('Idempotency-Key')).not.toBe(key);
      third.flush(ticket());
    });

    it('blocks a command while it is in flight and takes the ticket from its answer', () => {
      store.send();
      expect(store.pending.has('send')).toBe(true);
      http.expectOne(`${TICKET}/send`).flush(ticket({ number: 55 }));
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([]);

      expect(store.pending.has('send')).toBe(false);
      expect(store.ticket()?.number).toBe(55);
    });

    it('reads the ticket again when a command is refused', () => {
      store.send();
      http
        .expectOne(`${TICKET}/send`)
        .flush(problem(409, 'instore.ticket-closed'), { status: 409, statusText: 'Conflict' });
      http.expectOne(TICKET).flush(ticket({ status: 'CLOSED' }));
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([]);

      expect(store.isOpen()).toBe(false);
    });

    it('counts the commands refused because of the cashier shift', () => {
      expect(store.shiftStateErrors()).toBe(0);

      store.pay(payRequest, vi.fn());
      http
        .expectOne(`${TICKET}/payments`)
        .flush(problem(409, 'instore.no-open-shift'), { status: 409, statusText: 'Conflict' });
      rereadAfterFailure();

      expect(store.shiftStateErrors()).toBe(1);
    });

    it('releases the payment key after success so the next payment is a new intent', () => {
      store.pay(payRequest, vi.fn());
      const first = http.expectOne(`${TICKET}/payments`);
      const key = first.request.headers.get('Idempotency-Key');
      first.flush(ticket());

      store.pay(payRequest, vi.fn());
      const second = http.expectOne(`${TICKET}/payments`);
      expect(second.request.headers.get('Idempotency-Key')).not.toBe(key);
      second.flush(ticket());
    });

    it('repeats the lines of the latest round only', () => {
      store.load();
      http.expectOne(TICKET).flush(
        ticket({
          lines: [
            line('old', { state: 'SENT', sentAt: '2026-10-13T10:00:00Z' }),
            line('a', { state: 'SENT', sentAt: '2026-10-13T10:30:00Z' }),
            line('b', { state: 'SENT', sentAt: '2026-10-13T10:30:00Z' }),
            line('gone', { state: 'VOIDED', sentAt: '2026-10-13T10:30:00Z' }),
          ],
        }),
      );
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([]);

      store.repeatLastRound();

      const request = http.expectOne(`${TICKET}/repeat`);
      expect(request.request.body).toEqual({ lineIds: ['a', 'b'] });
      request.flush(ticket());
    });

    it('cancels the ticket only while nothing is billed', () => {
      expect(store.canCancel()).toBe(false);

      store.load();
      http.expectOne(TICKET).flush(ticket({ lines: [line('l1')] }));
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([]);
      expect(store.canCancel()).toBe(true);

      const done = vi.fn();
      store.cancel(done);
      http.expectOne(`${TICKET}/cancel`).flush(ticket({ status: 'CANCELLED', lines: [] }));
      expect(done).toHaveBeenCalled();
      expect(store.canCancel()).toBe(false);
    });

    it('replaces the ticket note', () => {
      store.changeNote('Sin hielo');

      const request = http.expectOne(`${TICKET}/note`);
      expect(request.request.body).toEqual({ note: 'Sin hielo' });
      request.flush(ticket({ note: 'Sin hielo' }));

      expect(store.ticket()?.note).toBe('Sin hielo');
    });
  });

  describe('comping a line', () => {
    const sent = line('l1', { state: 'SENT', sentAt: '2026-10-13T10:05:00Z' });
    const request = { reason: 'Staff meal', note: null };

    it('offers a comp on a sent line to a cashier or an admin, never to a server', () => {
      expect(store.mayComp(sent)).toBe(true);

      role.set('ADMIN');
      expect(store.mayComp(sent)).toBe(true);

      role.set('SERVER');
      expect(store.mayComp(sent)).toBe(false);
    });

    it('never offers a comp on an unsent, voided or comped line', () => {
      expect(store.mayComp(line('l2'))).toBe(false);
      expect(store.mayComp(line('l3', { state: 'VOIDED' }))).toBe(false);
      expect(store.mayComp(line('l4', { state: 'COMPED' }))).toBe(false);
    });

    it('comps under one key, reuses it on a retry, releases it after success and takes the totals from the answer', () => {
      const done = vi.fn();
      store.compLine(sent, request, done);
      const first = http.expectOne(`${TICKET}/lines/l1/comp`);
      const key = first.request.headers.get('Idempotency-Key');
      expect(first.request.body).toEqual(request);
      first.flush(problem(503, 'common.service-unavailable'), { status: 503, statusText: 'Unavailable' });
      http.expectOne(TICKET).flush(ticket());
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([]);

      store.compLine(sent, request, done);
      const second = http.expectOne(`${TICKET}/lines/l1/comp`);
      expect(second.request.headers.get('Idempotency-Key')).toBe(key);
      second.flush(
        ticket({
          total: pen('0.00'),
          balance: pen('0.00'),
          lines: [line('l1', { state: 'COMPED', lineTotal: pen('0.00') })],
        }),
      );

      expect(done).toHaveBeenCalledTimes(1);
      expect(store.ticket()?.total).toEqual(pen('0.00'));
      expect(store.stateOf(store.ticket()!.lines[0])).toBe('COMPED');

      store.compLine(sent, request, done);
      const third = http.expectOne(`${TICKET}/lines/l1/comp`);
      expect(third.request.headers.get('Idempotency-Key')).not.toBe(key);
      third.flush(ticket());
    });

    it('keeps the comp going after the screen is destroyed and reports it', () => {
      const done = vi.fn();
      const added = vi.spyOn(messages, 'add');
      store.compLine(sent, request, done);
      const comp = http.expectOne(`${TICKET}/lines/l1/comp`);

      screen.destroy();
      expect(comp.cancelled).toBe(false);
      comp.flush(ticket());

      expect(done).not.toHaveBeenCalled();
      expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    });

    it('reads the comp reasons', () => {
      store.loadCompReasons();
      http.expectOne(`${BASE}/reasons?type=COMP`).flush([{ type: 'COMP', code: 'Staff meal' }]);

      expect(store.reasons()).toEqual([{ type: 'COMP', code: 'Staff meal' }]);
    });
  });

  describe('merging tickets', () => {
    const summary = (id: string) => ({ id, number: 1, type: 'TABLE', tableName: 'Mesa' });

    it('lists the other open tickets, not the one being edited', () => {
      store.loadMergeCandidates();
      http
        .expectOne(`${BASE}/tickets?page=0&size=100`)
        .flush({ content: [summary('k1'), summary('k2')], page: 0, size: 100, totalElements: 2, totalPages: 1 });

      expect(store.mergeCandidates().map((candidate) => candidate.id)).toEqual(['k2']);
    });

    it('reports a failed candidate read so it can be retried', () => {
      store.loadMergeCandidates();
      http
        .expectOne(`${BASE}/tickets?page=0&size=100`)
        .flush(problem(500, 'common.internal-error'), { status: 500, statusText: 'Error' });

      expect(store.mergeCandidatesFailed()).toBe(true);
      expect(store.mergeCandidates()).toEqual([]);
    });

    it('merges the chosen ticket into this one and takes the surviving ticket from the answer', () => {
      const done = vi.fn();
      store.merge('k2', done);
      const request = http.expectOne(`${TICKET}/merge`);
      expect(request.request.body).toEqual({ sourceTicketId: 'k2' });
      expect(store.pending.has('merge')).toBe(true);
      request.flush(ticket({ number: 40, total: pen('30.00') }));

      expect(done).toHaveBeenCalled();
      expect(store.ticket()?.total).toEqual(pen('30.00'));
      expect(store.pending.has('merge')).toBe(false);
    });

    it('keeps the toast and reads again when the merge is refused', () => {
      const added = vi.spyOn(messages, 'add');
      const done = vi.fn();
      store.merge('k2', done);
      http
        .expectOne(`${TICKET}/merge`)
        .flush(problem(409, 'instore.ticket-has-payments'), { status: 409, statusText: 'Conflict' });
      http.expectOne(TICKET).flush(ticket());
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([]);

      expect(done).not.toHaveBeenCalled();
      expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    });
  });

  describe('a ticket that was merged into another', () => {
    it('goes to the surviving ticket with an info toast', () => {
      const added = vi.spyOn(messages, 'add');
      store.load();
      http.expectOne(TICKET).flush(ticket({ status: 'MERGED', mergedIntoId: 'k9' }));
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([]);

      expect(router.navigate).toHaveBeenCalledWith(['/staff/tickets', 'k9'], { replaceUrl: true });
      expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'info' }));
    });

    it('stays put when a merged ticket names no survivor', () => {
      store.load();
      http.expectOne(TICKET).flush(ticket({ status: 'MERGED', mergedIntoId: null }));
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([]);

      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('does not move for an open ticket', () => {
      expect(router.navigate).not.toHaveBeenCalled();
    });
  });

  describe('void reasons', () => {
    it('reads the void reasons and reports a failure so it can be retried', () => {
      store.loadVoidReasons();
      http.expectOne(`${BASE}/reasons?type=VOID`).flush([{ type: 'VOID', code: 'Error' }]);
      expect(store.reasons()).toEqual([{ type: 'VOID', code: 'Error' }]);

      store.loadVoidReasons();
      http
        .expectOne(`${BASE}/reasons?type=VOID`)
        .flush(problem(500, 'common.internal-error'), { status: 500, statusText: 'Error' });
      expect(store.reasonsLoadFailed()).toBe(true);
      expect(store.reasons()).toBeNull();
    });
  });

  describe('splitting the bill', () => {
    it('keeps the parts the API computed until they are cleared', () => {
      store.loadSplit(2);
      expect(store.pending.has('split')).toBe(true);
      http.expectOne(`${TICKET}/split?parts=2`).flush({ parts: [pen('5.00'), pen('5.00')] });
      expect(store.splitParts()).toEqual([pen('5.00'), pen('5.00')]);

      store.clearSplit();
      expect(store.splitParts()).toBeNull();
    });
  });

  describe('removing a line', () => {
    const unsent = line('l2');

    afterEach(() => vi.useRealTimers());

    it('waits out the undo window before it sends anything and shows an undo notice', () => {
      vi.useFakeTimers();
      store.removeLine(unsent);

      expect(store.isRemoving('l2')).toBe(true);
      expect(TestBed.inject(UndoNotice).active()).toHaveLength(1);
      http.expectNone(`${TICKET}/lines/l2`);

      vi.advanceTimersByTime(UNDO_WINDOW_MS);
      const request = http.expectOne(`${TICKET}/lines/l2`);
      expect(request.request.method).toBe('DELETE');
      request.flush(null, { status: 204, statusText: 'No Content' });
      http.expectOne(TICKET).flush(ticket());
      expect(store.isRemoving('l2')).toBe(false);
    });

    it('sends nothing when the notice is undone', () => {
      vi.useFakeTimers();
      const notice = TestBed.inject(UndoNotice);
      store.removeLine(unsent);

      notice.undo(notice.active()[0]);
      vi.advanceTimersByTime(UNDO_WINDOW_MS * 2);

      expect(store.isRemoving('l2')).toBe(false);
      http.expectNone(`${TICKET}/lines/l2`);
    });

    it('sends a waiting removal when the screen is destroyed', () => {
      vi.useFakeTimers();
      store.removeLine(unsent);

      screen.destroy();

      http.expectOne(`${TICKET}/lines/l2`).flush(null, { status: 204, statusText: 'No Content' });
    });
    it('sends the removal to the ticket it was queued on even if the screen moved to another one', () => {
      vi.useFakeTimers();
      store.removeLine(unsent);

      params.next(convertToParamMap({ id: 'k2' }));
      http.expectOne(`${BASE}/tickets/k2`).flush(ticket({ id: 'k2' }));
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([]);
      http.expectOne(`${BASE}/catalog/menu`).flush({ categories: [] });
      vi.advanceTimersByTime(UNDO_WINDOW_MS);

      http.expectOne(`${TICKET}/lines/l2`).flush(null, { status: 204, statusText: 'No Content' });
    });

    it('renews a token that would expire inside the undo window', () => {
      vi.useFakeTimers();
      store.removeLine(unsent);

      expect(ensureFresh).toHaveBeenCalledWith(UNDO_WINDOW_MS + 30_000);
    });

    it('sends a waiting removal at sign-out and resolves once it was answered', async () => {
      vi.useFakeTimers();
      store.removeLine(unsent);

      let drained = false;
      const draining = Promise.resolve(signOutHooks[0]()).then(() => (drained = true));
      const request = http.expectOne(`${TICKET}/lines/l2`);
      await Promise.resolve();
      expect(drained).toBe(false);

      request.flush(null, { status: 204, statusText: 'No Content' });
      http.expectOne(TICKET).flush(ticket());
      await draining;
      expect(drained).toBe(true);
    });
  });

  describe('sending to the kitchen while a line is being removed', () => {
    const unsent = line('l2');
    const other = line('l3');

    function withUnsent(): void {
      store.load();
      http.expectOne(TICKET).flush(ticket({ lines: [line('l1', { state: 'SENT', sentAt: '2026-10-13T10:05:00Z' }), unsent, other] }));
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([]);
    }

    afterEach(() => vi.useRealTimers());

    it('never offers a line that is being removed to the kitchen', () => {
      vi.useFakeTimers();
      withUnsent();
      store.removeLine(unsent);

      expect(store.sendable().map((candidate) => candidate.id)).toEqual(['l3']);
      expect(store.unsent()).toHaveLength(2);
    });

    it('cannot send while a removal waits, and can again once it is undone', () => {
      vi.useFakeTimers();
      withUnsent();
      const notice = TestBed.inject(UndoNotice);
      store.removeLine(unsent);

      expect(store.canSend()).toBe(false);
      store.send();
      http.expectNone(`${TICKET}/send`);

      notice.undo(notice.active()[0]);
      expect(store.canSend()).toBe(true);
    });

    it('cannot send while the removal request is in flight, and can once it is answered', () => {
      vi.useFakeTimers();
      withUnsent();
      store.removeLine(unsent);
      vi.advanceTimersByTime(UNDO_WINDOW_MS);
      const request = http.expectOne(`${TICKET}/lines/l2`);

      expect(store.canSend()).toBe(false);
      request.flush(null, { status: 204, statusText: 'No Content' });
      http.expectOne(TICKET).flush(ticket({ lines: [line('l3')] }));
      expect(store.canSend()).toBe(true);
    });

    it('can send again when the removal request completes without an answer', () => {
      vi.useFakeTimers();
      withUnsent();
      vi.spyOn(TestBed.inject(TicketsApi), 'removeLine').mockReturnValue(EMPTY);
      store.removeLine(unsent);
      vi.advanceTimersByTime(UNDO_WINDOW_MS);

      expect(store.canSend()).toBe(true);
    });

    it('can send again when the removal request fails, and shows the failure', () => {
      vi.useFakeTimers();
      withUnsent();
      const shown = vi.spyOn(messages, 'add');
      store.removeLine(unsent);
      vi.advanceTimersByTime(UNDO_WINDOW_MS);

      http.expectOne(`${TICKET}/lines/l2`).flush(null, { status: 500, statusText: 'Server Error' });
      http.expectOne(TICKET).flush(ticket({ lines: [unsent] }));
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([]);

      expect(store.canSend()).toBe(true);
      expect(shown).toHaveBeenCalled();
    });

    it('does not reload a ticket the screen has already left when the removal is answered', () => {
      vi.useFakeTimers();
      withUnsent();
      store.removeLine(unsent);
      vi.advanceTimersByTime(UNDO_WINDOW_MS);
      const request = http.expectOne(`${TICKET}/lines/l2`);
      params.next(convertToParamMap({ id: 'k2' }));
      http.expectOne(`${BASE}/tickets/k2`).flush(ticket({ id: 'k2' }));
      http.expectOne(`${BASE}/board/tickets`).flush([]);
      http.expectOne(`${BASE}/board/ready`).flush([]);
      http.expectOne(`${BASE}/catalog/menu`).flush({ categories: [] });

      request.flush(null, { status: 204, statusText: 'No Content' });

      http.expectNone(TICKET);
    });

    it('cannot send when only lines being removed are unsent', () => {
      vi.useFakeTimers();
      store.removeLine(unsent);

      expect(store.sendable()).toEqual([]);
      expect(store.canSend()).toBe(false);
    });
  });
});
