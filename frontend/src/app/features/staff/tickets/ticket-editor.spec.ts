import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal, type WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { Observable, of, Subject } from 'rxjs';
import { API_ORIGIN } from '../../../core/api/api-config';
import type {
  BoardOrder,
  Role,
  Menu,
  Payment,
  TableGridEntry,
  Ticket,
  TicketLine,
} from '../../../core/api/api-types';
import { AuthStore } from '../../../core/auth/auth-store';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { RealtimeClient } from '../../../core/realtime/realtime-client';
import { chooseOption, optionLabels, selectedLabel } from '../../../testing/primeng-controls';
import { shift } from '../../../testing/register-fixtures';
import { RegisterApi } from '../register/register-api';
import { TicketEditor } from './ticket-editor';
import { TicketsApi } from './tickets-api';

const BASE = 'http://api.test/api/v1';
const TICKET = `${BASE}/tickets/k1`;
const MENU = `${BASE}/catalog/menu`;
const BOARD = `${BASE}/board/tickets`;
const BOARD_READY = `${BASE}/board/ready`;
const TABLES = `${BASE}/tables`;
const REASONS = `${BASE}/reasons?type=VOID`;
const SENT_AT = '2026-10-13T10:05:00Z';

const pen = (amount: string) => ({ amount, currency: 'PEN' });

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

function ticket(lines: TicketLine[], overrides: Partial<Ticket> = {}): Ticket {
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
    lines,
    total: pen('30.00'),
    paid: pen('0.00'),
    balance: pen('30.00'),
    payments: [],
    ...overrides,
  } as Ticket;
}

function payment(id: string, overrides: Partial<Payment> = {}): Payment {
  return {
    id,
    method: 'CASH',
    paid: pen('30.00'),
    received: pen('50.00'),
    change: pen('20.00'),
    lineIds: [],
    recordedBy: 'u1',
    recordedAt: '2026-10-13T10:20:00Z',
    voided: false,
    voidReasonCode: null,
    voidNote: null,
    ...overrides,
  };
}

function menu(): Menu {
  const option = (id: string, name: string) => ({
    id,
    name,
    available: true,
    allergens: [],
    priceDelta: pen('0.00'),
  });
  const product = (id: string, name: string, available: boolean, groups = false) => ({
    id,
    name,
    available,
    description: null,
    imageUrl: null,
    allergens: [],
    price: pen('10.00'),
    modifierGroups: groups
      ? [
          {
            id: 'size',
            name: 'Tamaño',
            required: true,
            minChoices: 1,
            maxChoices: 1,
            options: [option('small', 'Chico'), option('large', 'Grande')],
          },
        ]
      : [],
  });
  return {
    categories: [
      {
        id: 'c1',
        name: 'Jugos',
        products: [product('p1', 'Naranja', true, true), product('p2', 'Fresa', false)],
      },
    ],
  };
}

// A board line has its own id; `sourceLineId` is the ticket line it came from.
function board(readyTicketLineId?: string, state: 'READY' | 'PENDING' = 'READY'): BoardOrder[] {
  return [
    {
      id: 'b1',
      lines: [{ id: `board-${readyTicketLineId ?? 'none'}`, sourceLineId: readyTicketLineId ?? 'none', state }],
    } as unknown as BoardOrder,
  ];
}

function freeTable(id: string, name: string, status: TableGridEntry['status'] = 'FREE'): TableGridEntry {
  return {
    id,
    name,
    area: null,
    displayOrder: 0,
    status,
    ticketId: null,
    ticketOpenedAt: null,
  };
}

class FakeRealtime {
  readonly connected = signal(false);
  readonly isConnected = this.connected.asReadonly();
  private readonly topics = new Map<string, Subject<void>>();

  refetch(destination: string): Observable<void> {
    return this.subject(destination).asObservable();
  }

  fire(destination: string): void {
    this.subject(destination).next();
  }

  private subject(destination: string): Subject<void> {
    let subject = this.topics.get(destination);
    if (!subject) {
      subject = new Subject<void>();
      this.topics.set(destination, subject);
    }
    return subject;
  }
}

const failure = {
  type: 'about:blank',
  status: 409,
  code: 'instore.ticket-closed',
  correlationId: 'c',
};

describe('TicketEditor', () => {
  let realtime: FakeRealtime;
  let http: HttpTestingController;
  let messages: MessageService;
  let role: WritableSignal<Role | null>;

  beforeEach(() => {
    realtime = new FakeRealtime();
    role = signal<Role | null>('CASHIER');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
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
  });

  afterEach(() => http.verify());

  // The editor reads both views: a card that is fully ready leaves `tickets` and shows in `ready`.
  function flushBoard(tickets: BoardOrder[] = [], readyView: BoardOrder[] = []) {
    http.expectOne(BOARD).flush(tickets);
    http.expectOne(BOARD_READY).flush(readyView);
  }

  async function open(
    current: Ticket = ticket([line('l1')]),
    onBoard: BoardOrder[] = [],
    onReady: BoardOrder[] = [],
  ) {
    const fixture = TestBed.createComponent(TicketEditor);
    await fixture.whenStable();
    http.expectOne(TICKET).flush(current);
    http.expectOne(MENU).flush(menu());
    flushBoard(onBoard, onReady);
    await fixture.whenStable();
    return fixture;
  }

  // Connecting emits the re-fetch signal; answering that read is what enables the actions.
  async function ready(
    current: Ticket = ticket([line('l1')]),
    onBoard: BoardOrder[] = [],
    onReady: BoardOrder[] = [],
  ) {
    const fixture = await open(current, onBoard, onReady);
    realtime.connected.set(true);
    realtime.fire('/topic/board');
    http.expectOne(TICKET).flush(current);
    flushBoard(onBoard, onReady);
    await fixture.whenStable();
    return fixture;
  }

  const host = (fixture: ComponentFixture<TicketEditor>) => fixture.nativeElement as HTMLElement;
  const find = <T extends HTMLElement>(fixture: ComponentFixture<TicketEditor>, testId: string) =>
    host(fixture).querySelector<T>(`[data-testid="${testId}"]`);
  const text = (fixture: ComponentFixture<TicketEditor>, testId: string) =>
    find(fixture, testId)?.textContent ?? '';
  const press = async (fixture: ComponentFixture<TicketEditor>, testId: string) => {
    find<HTMLButtonElement>(fixture, testId)!.click();
    await fixture.whenStable();
  };
  const inDialog = <T extends HTMLElement>(selector: string) =>
    document.body.querySelector<T>(`[role="dialog"] ${selector}`);

  it('shows the table, the ticket number, the total and every line with its state', async () => {
    const sent = line('l2', { state: 'SENT', sentAt: '2026-10-13T10:05:00Z', productName: 'Fresa' });
    const done = line('l3', {
      state: 'SENT',
      sentAt: '2026-10-13T10:05:00Z',
      modifiers: [{ optionId: 'large', name: 'Grande', priceDelta: pen('0.00') }],
      note: 'Sin hielo',
      allergy: true,
    });
    const voided = line('l4', { state: 'VOIDED', voidReasonCode: 'Error' });
    const fixture = await open(ticket([line('l1'), sent, done, voided]), board('l3'));

    expect(text(fixture, 'ticket-title')).toContain('Mesa 1');
    expect(text(fixture, 'ticket-total')).toContain('30.00');
    expect(text(fixture, 'line-status-l1')).toContain('Sin enviar');
    expect(text(fixture, 'line-status-l2')).toContain('Enviada');
    expect(text(fixture, 'line-status-l3')).toContain('Lista');
    expect(text(fixture, 'line-status-l4')).toContain('Anulada');
    expect(text(fixture, 'line-l3')).toContain('Grande');
    expect(text(fixture, 'line-l3')).toContain('Sin hielo');
    expect(text(fixture, 'line-l3')).toContain('Alergia');
  });

  it('marks a line Ready from the ready view when its whole card is ready', async () => {
    const sentLine = line('l1', { state: 'SENT', sentAt: SENT_AT });
    const fixture = await open(ticket([sentLine]), [], board('l1'));

    expect(text(fixture, 'line-status-l1')).toContain('Lista');
  });

  it('does not take a board line for a ticket line just because the ids are equal', async () => {
    const sentLine = line('l1', { state: 'SENT', sentAt: SENT_AT });
    const lookalike = [
      { id: 'l1', sourceLineId: 'other', state: 'READY' },
    ] as unknown as BoardOrder['lines'];
    const fixture = await open(ticket([sentLine]), [{ id: 'b1', lines: lookalike } as BoardOrder]);

    expect(text(fixture, 'line-status-l1')).toContain('Enviada');
  });

  it('does not mark a line Ready while its board line is still pending', async () => {
    const sentLine = line('l1', { state: 'SENT', sentAt: SENT_AT });
    const fixture = await open(ticket([sentLine]), board('l1', 'PENDING'));

    expect(text(fixture, 'line-status-l1')).toContain('Enviada');
  });

  it('titles a counter ticket with the customer name', async () => {
    const fixture = await open(
      ticket([], { type: 'COUNTER', tableId: null, tableName: null, counterName: 'Ana' }),
    );

    expect(text(fixture, 'ticket-title')).toContain('Ana');
  });

  it('keeps the actions off until the live connection has been read', async () => {
    const fixture = await open();

    expect(find<HTMLButtonElement>(fixture, 'send')!.disabled).toBe(true);
    expect(find<HTMLButtonElement>(fixture, 'product-p1')!.disabled).toBe(true);
    expect(text(fixture, 'offline-banner')).toContain('Sin conexión en tiempo real');

    realtime.connected.set(true);
    realtime.fire('/topic/board');
    http.expectOne(TICKET).flush(ticket([line('l1')]));
    flushBoard([]);
    await fixture.whenStable();

    expect(find<HTMLButtonElement>(fixture, 'send')!.disabled).toBe(false);
    expect(find<HTMLButtonElement>(fixture, 'product-p1')!.disabled).toBe(false);
    expect(find(fixture, 'offline-banner')).toBeNull();
  });

  it('lists the products by category and disables a sold-out one', async () => {
    const fixture = await ready();

    expect(host(fixture).textContent).toContain('Jugos');
    expect(find<HTMLButtonElement>(fixture, 'product-p1')!.disabled).toBe(false);
    expect(find<HTMLButtonElement>(fixture, 'product-p2')!.disabled).toBe(true);
    expect(text(fixture, 'product-p2')).toContain('Agotado');
  });

  it('adds a configured product as a line with the idempotency key of the intent', async () => {
    const fixture = await ready();

    await press(fixture, 'product-p1');
    inDialog<HTMLInputElement>('[data-testid="option-large"] input')!.click();
    await fixture.whenStable();
    inDialog<HTMLButtonElement>('[data-testid="submit"]')!.click();
    await fixture.whenStable();

    const request = http.expectOne(`${TICKET}/lines`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      productId: 'p1',
      quantity: 1,
      modifierOptionIds: ['large'],
      note: null,
      allergy: false,
    });
    expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
    request.flush(ticket([line('l1'), line('l9', { productName: 'Naranja grande' })]), {
      status: 201,
      statusText: 'Created',
    });
    await fixture.whenStable();

    expect(text(fixture, 'line-l9')).toContain('Naranja grande');
    expect(inDialog('[data-testid="submit"]')).toBeNull();
  });

  it('retries an add with the same key and keeps the dialog open after a failure', async () => {
    const fixture = await ready();
    const added = vi.spyOn(messages, 'add');
    await press(fixture, 'product-p1');
    inDialog<HTMLInputElement>('[data-testid="option-small"] input')!.click();
    await fixture.whenStable();

    inDialog<HTMLButtonElement>('[data-testid="submit"]')!.click();
    await fixture.whenStable();
    const first = http.expectOne(`${TICKET}/lines`);
    const key = first.request.headers.get('Idempotency-Key');
    first.flush(failure, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
    http.expectOne(TICKET).flush(ticket([line('l1')]));
    flushBoard([]);
    await fixture.whenStable();

    expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    expect(inDialog('[data-testid="submit"]')).not.toBeNull();

    inDialog<HTMLButtonElement>('[data-testid="submit"]')!.click();
    await fixture.whenStable();
    const second = http.expectOne(`${TICKET}/lines`);
    expect(second.request.headers.get('Idempotency-Key')).toBe(key);
    second.flush(ticket([line('l1')]), { status: 201, statusText: 'Created' });
  });

  it('closes the add dialog on cancel without calling the API', async () => {
    const fixture = await ready();
    await press(fixture, 'product-p1');

    inDialog<HTMLButtonElement>('[data-testid="cancel"]')!.click();
    await fixture.whenStable();

    expect(inDialog('[data-testid="submit"]')).toBeNull();
  });

  it('sends the unsent lines to the kitchen', async () => {
    const fixture = await ready();

    await press(fixture, 'send');
    const request = http.expectOne(`${TICKET}/send`);
    expect(request.request.method).toBe('POST');
    request.flush(ticket([line('l1', { state: 'SENT', sentAt: '2026-10-13T10:05:00Z' })]));
    await fixture.whenStable();
    flushBoard([]);
    await fixture.whenStable();

    expect(text(fixture, 'line-status-l1')).toContain('Enviada');
    expect(find<HTMLButtonElement>(fixture, 'send')!.disabled).toBe(true);
  });

  it('removes an unsent line and reads the ticket again', async () => {
    const fixture = await ready();

    await press(fixture, 'remove-l1');
    const request = http.expectOne(`${TICKET}/lines/l1`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();
    http.expectOne(TICKET).flush(ticket([]));
    await fixture.whenStable();

    expect(find(fixture, 'line-l1')).toBeNull();
  });

  it('changes the quantity of an unsent line with its own choices', async () => {
    const current = ticket([
      line('l1', {
        quantity: 2,
        note: 'Frío',
        modifiers: [{ optionId: 'large', name: 'Grande', priceDelta: pen('0.00') }],
      }),
    ]);
    const fixture = await ready(current);

    await press(fixture, 'quantity-up-l1');

    const request = http.expectOne(`${TICKET}/lines/l1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      productId: 'p1',
      quantity: 3,
      modifierOptionIds: ['large'],
      note: 'Frío',
      allergy: false,
    });
    request.flush(current);
  });

  it('does not let the quantity of a line drop below one or rise above ninety-nine', async () => {
    const fixture = await ready(ticket([line('l1', { quantity: 1 }), line('l2', { quantity: 99 })]));

    expect(find<HTMLButtonElement>(fixture, 'quantity-down-l1')!.disabled).toBe(true);
    expect(find<HTMLButtonElement>(fixture, 'quantity-up-l2')!.disabled).toBe(true);
  });

  it('offers no editing on a sent line', async () => {
    const fixture = await ready(
      ticket([line('l1', { state: 'SENT', sentAt: '2026-10-13T10:05:00Z' })]),
    );

    expect(find(fixture, 'remove-l1')).toBeNull();
    expect(find(fixture, 'quantity-up-l1')).toBeNull();
  });

  it('repeats the last round without the lines voided since', async () => {
    const earlier = '2026-10-13T10:05:00Z';
    const later = '2026-10-13T10:30:00Z';
    const fixture = await ready(
      ticket([
        line('l1', { state: 'SENT', sentAt: earlier }),
        line('l2', { state: 'SENT', sentAt: later }),
        line('l3', { state: 'SENT', sentAt: later }),
        line('l4', { state: 'VOIDED', sentAt: later }),
      ]),
    );

    await press(fixture, 'repeat');

    const request = http.expectOne(`${TICKET}/repeat`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ lineIds: ['l2', 'l3'] });
    expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
    request.flush(ticket([line('l5')]), { status: 201, statusText: 'Created' });
  });

  it('has nothing to repeat before a first send', async () => {
    const fixture = await ready();

    expect(find<HTMLButtonElement>(fixture, 'repeat')!.disabled).toBe(true);
  });

  it('asks for the bill and takes the request back', async () => {
    const fixture = await ready();

    await press(fixture, 'bill');
    const ask = http.expectOne(`${TICKET}/bill-requested`);
    expect(ask.request.body).toEqual({ requested: true });
    ask.flush(ticket([line('l1')], { billRequested: true }));
    await fixture.whenStable();

    expect(text(fixture, 'bill')).toContain('Quitar el pedido de cuenta');
    await press(fixture, 'bill');
    const back = http.expectOne(`${TICKET}/bill-requested`);
    expect(back.request.body).toEqual({ requested: false });
    back.flush(ticket([line('l1')]));
  });

  it('offers neither bill nor transfer on a counter ticket', async () => {
    const fixture = await ready(
      ticket([line('l1')], { type: 'COUNTER', tableId: null, tableName: null, counterName: 'Ana' }),
    );

    expect(find(fixture, 'bill')).toBeNull();
    expect(find(fixture, 'transfer-open')).toBeNull();
  });

  it('moves the ticket to a free table', async () => {
    const fixture = await ready();

    await press(fixture, 'transfer-open');
    http.expectOne(TABLES).flush([
      freeTable('t1', 'Mesa 1', 'OCCUPIED'),
      freeTable('t2', 'Mesa 2'),
      freeTable('t3', 'Mesa 3', 'OCCUPIED'),
      freeTable('t4', 'Mesa 4'),
    ]);
    await fixture.whenStable();
    const dialog = document.body.querySelector('[role="dialog"]') as HTMLElement;
    await chooseOption(fixture, dialog, 'transfer-table', 'Mesa 4');
    inDialog<HTMLButtonElement>('[data-testid="transfer-confirm"]')!.click();
    await fixture.whenStable();

    const request = http.expectOne(`${TICKET}/transfer`);
    expect(request.request.body).toEqual({ tableId: 't4' });
    request.flush(ticket([line('l1')], { tableId: 't4', tableName: 'Mesa 4' }));
    await fixture.whenStable();

    expect(text(fixture, 'ticket-title')).toContain('Mesa 4');
    expect(inDialog('[data-testid="transfer-confirm"]')).toBeNull();
  });

  describe('comping a sent line', () => {
    const COMP_REASONS = `${BASE}/reasons?type=COMP`;
    const sent = () => ticket([line('l1', { state: 'SENT', sentAt: SENT_AT }), line('l2')]);
    const dialog = () => document.body.querySelector('[role="dialog"]') as HTMLElement;

    it('offers the comp on a sent line to a cashier and an admin, not to a server', async () => {
      const cashier = await ready(sent());
      expect(find(cashier, 'comp-l1')).not.toBeNull();
      expect(find(cashier, 'comp-l2')).toBeNull();
      cashier.destroy();

      role.set('SERVER');
      const server = await ready(sent());
      expect(find(server, 'comp-l1')).toBeNull();
    });

    it('offers no comp on a voided or comped line or on a closed ticket', async () => {
      const lines = [
        line('l1', { state: 'VOIDED' }),
        line('l2', { state: 'COMPED', compReasonCode: 'Staff meal' }),
      ];
      const fixture = await ready(ticket(lines));
      expect(find(fixture, 'comp-l1')).toBeNull();
      expect(find(fixture, 'comp-l2')).toBeNull();
      fixture.destroy();

      const closed = await ready(
        ticket([line('l1', { state: 'SENT', sentAt: SENT_AT })], { status: 'CLOSED' }),
      );
      expect(find(closed, 'comp-l1')).toBeNull();
    });

    it('comps the line with a COMP reason and shows it as a courtesy with the totals from the API', async () => {
      const fixture = await ready(sent());
      await press(fixture, 'comp-l1');
      http.expectOne(COMP_REASONS).flush([
        { type: 'COMP', code: 'Staff meal' },
        { type: 'COMP', code: 'Other' },
      ]);
      await fixture.whenStable();
      await chooseOption(fixture, dialog(), 'void-dialog-reason', 'Staff meal');
      inDialog<HTMLButtonElement>('[data-testid="vd-confirm"]')!.click();
      await fixture.whenStable();

      const request = http.expectOne(`${TICKET}/lines/l1/comp`);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({ reason: 'Staff meal', note: null });
      expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
      request.flush(
        ticket(
          [
            line('l1', {
              state: 'COMPED',
              sentAt: SENT_AT,
              compReasonCode: 'Staff meal',
              lineTotal: pen('10.00'),
            }),
            line('l2'),
          ],
          { total: pen('10.00'), balance: pen('10.00') },
        ),
      );
      await fixture.whenStable();

      expect(text(fixture, 'line-status-l1')).toContain('Cortesía');
      expect(text(fixture, 'line-status-l1')).toContain('Staff meal');
      expect(find(fixture, 'line-l1')!.classList.contains('comped')).toBe(true);
      expect(find(fixture, 'line-l1')!.classList.contains('voided')).toBe(false);
      expect(text(fixture, 'ticket-total')).toContain('10.00');
      expect(find(fixture, 'comp-l1')).toBeNull();
      expect(find(fixture, 'void-l1')).toBeNull();
      expect(inDialog('[data-testid="vd-confirm"]')).toBeNull();
    });

    it('needs a note for the reason "Other" and sends it', async () => {
      const fixture = await ready(sent());
      await press(fixture, 'comp-l1');
      http.expectOne(COMP_REASONS).flush([{ type: 'COMP', code: 'Other' }]);
      await fixture.whenStable();
      await chooseOption(fixture, dialog(), 'void-dialog-reason', 'Otro');
      expect(inDialog<HTMLButtonElement>('[data-testid="vd-confirm"]')!.disabled).toBe(true);

      const note = inDialog<HTMLTextAreaElement>('[data-testid="vd-note"]')!;
      note.value = 'Tasting';
      note.dispatchEvent(new Event('input'));
      await fixture.whenStable();
      inDialog<HTMLButtonElement>('[data-testid="vd-confirm"]')!.click();
      await fixture.whenStable();

      const request = http.expectOne(`${TICKET}/lines/l1/comp`);
      expect(request.request.body).toEqual({ reason: 'Other', note: 'Tasting' });
      request.flush(sent());
    });

    it('keeps the dialog and shows the refusal when the line is covered by a payment', async () => {
      const added = vi.spyOn(messages, 'add');
      const fixture = await ready(sent());
      await press(fixture, 'comp-l1');
      http.expectOne(COMP_REASONS).flush([{ type: 'COMP', code: 'Staff meal' }]);
      await fixture.whenStable();
      await chooseOption(fixture, dialog(), 'void-dialog-reason', 'Staff meal');
      inDialog<HTMLButtonElement>('[data-testid="vd-confirm"]')!.click();
      await fixture.whenStable();

      http
        .expectOne(`${TICKET}/lines/l1/comp`)
        .flush(
          { ...failure, status: 409, code: 'instore.line-covered-by-payment' },
          { status: 409, statusText: 'Conflict' },
        );
      await fixture.whenStable();
      http.expectOne(TICKET).flush(sent());
      flushBoard();
      await fixture.whenStable();

      expect(added).toHaveBeenCalledWith(
        expect.objectContaining({ severity: 'error', summary: expect.stringContaining('Anula primero el pago') }),
      );
      expect(inDialog('[data-testid="vd-confirm"]')).not.toBeNull();
    });
  });

  describe('merging another open ticket into this one', () => {
    const OPEN_LIST = `${BASE}/tickets?page=0&size=100`;
    const summary = (id: string, number: number, tableName: string | null, counterName: string | null = null) => ({
      id,
      number,
      type: tableName ? 'TABLE' : 'COUNTER',
      tableName,
      counterName,
      billRequested: false,
      openedAt: '2026-10-13T10:00:00Z',
      tableId: null,
      total: pen('5.00'),
    });
    const page = (content: unknown[]) => ({ content, page: 0, size: 100, totalElements: content.length, totalPages: 1 });
    const dialog = () => document.body.querySelector('[role="dialog"]') as HTMLElement;

    async function openMerge(content: unknown[]) {
      const fixture = await ready();
      await press(fixture, 'merge-open');
      http.expectOne(OPEN_LIST).flush(page(content));
      await fixture.whenStable();
      return fixture;
    }

    it('offers the action to floor staff on an open ticket, even a server', async () => {
      role.set('SERVER');
      const fixture = await ready();

      expect(find(fixture, 'merge-open')).not.toBeNull();
    });

    it('offers nothing on a closed ticket', async () => {
      const fixture = await ready(ticket([line('l1')], { status: 'CLOSED' }));

      expect(find(fixture, 'merge-open')).toBeNull();
    });

    it('lists the other open tickets, never the one being edited', async () => {
      const fixture = await openMerge([
        summary('k1', 12, 'Mesa 1'),
        summary('k2', 13, 'Mesa 2'),
        summary('k3', 14, null, 'Ana'),
      ]);
      const labels = await optionLabels(fixture, dialog(), 'merge-source');

      expect(labels).toEqual(['Mesa 2 · Cuenta 13', 'Mostrador: Ana · Cuenta 14']);
    });

    it('says there is nothing to merge when no other ticket is open', async () => {
      await openMerge([summary('k1', 12, 'Mesa 1')]);

      expect(inDialog('[data-testid="merge-empty"]')).not.toBeNull();
      expect(inDialog<HTMLButtonElement>('[data-testid="merge-confirm"]')!.disabled).toBe(true);
    });

    it('merges the chosen ticket, shows the surviving ticket from the answer and closes the dialog', async () => {
      const fixture = await openMerge([summary('k2', 13, 'Mesa 2')]);
      await chooseOption(fixture, dialog(), 'merge-source', 'Mesa 2 · Cuenta 13');
      inDialog<HTMLButtonElement>('[data-testid="merge-confirm"]')!.click();
      await fixture.whenStable();

      const request = http.expectOne(`${TICKET}/merge`);
      expect(request.request.body).toEqual({ sourceTicketId: 'k2' });
      request.flush(ticket([line('l1'), line('l9')], { total: pen('50.00'), balance: pen('50.00') }));
      await fixture.whenStable();

      expect(text(fixture, 'ticket-total')).toContain('50.00');
      expect(find(fixture, 'line-l9')).not.toBeNull();
      expect(inDialog('[data-testid="merge-confirm"]')).toBeNull();
    });

    it('keeps the dialog and shows the refusal when the other ticket has payments', async () => {
      const added = vi.spyOn(messages, 'add');
      const fixture = await openMerge([summary('k2', 13, 'Mesa 2')]);
      await chooseOption(fixture, dialog(), 'merge-source', 'Mesa 2 · Cuenta 13');
      inDialog<HTMLButtonElement>('[data-testid="merge-confirm"]')!.click();
      await fixture.whenStable();

      http
        .expectOne(`${TICKET}/merge`)
        .flush(
          { ...failure, code: 'instore.ticket-has-payments' },
          { status: 409, statusText: 'Conflict' },
        );
      await fixture.whenStable();
      http.expectOne(TICKET).flush(ticket([line('l1')]));
      flushBoard();
      await fixture.whenStable();

      expect(added).toHaveBeenCalledWith(
        expect.objectContaining({ severity: 'error', summary: expect.stringContaining('tiene pagos') }),
      );
      expect(inDialog('[data-testid="merge-confirm"]')).not.toBeNull();
    });

    it('offers a retry when the open tickets cannot be read', async () => {
      const fixture = await ready();
      await press(fixture, 'merge-open');
      http
        .expectOne(OPEN_LIST)
        .flush({ ...failure, status: 500, code: 'common.internal-error' }, { status: 500, statusText: 'Error' });
      await fixture.whenStable();

      expect(inDialog('[data-testid="merge-failed"]')).not.toBeNull();
      inDialog<HTMLButtonElement>('[data-testid="merge-retry"]')!.click();
      await fixture.whenStable();
      http.expectOne(OPEN_LIST).flush(page([]));
    });
  });

  it('tells the employee a merged ticket is no longer editable', async () => {
    const fixture = await open(ticket([line('l1')], { status: 'MERGED', mergedIntoId: null }));

    expect(find(fixture, 'merged-notice')).not.toBeNull();
    expect(find(fixture, 'closed-notice')).toBeNull();
  });

  describe('cancelling a ticket with nothing billed', () => {
    const cancelButton = (fixture: ComponentFixture<TicketEditor>) => find(fixture, 'cancel-open');

    it.each([
      ['an empty ticket', ticket([])],
      ['only unsent lines', ticket([line('l1'), line('l2')])],
      [
        'lines that were all voided',
        ticket([line('l1', { state: 'VOIDED', sentAt: SENT_AT })]),
      ],
      [
        'a payment that was voided',
        ticket([line('l1', { state: 'VOIDED', sentAt: SENT_AT })], {
          payments: [payment('pay1', { voided: true })],
        }),
      ],
    ])('offers it for %s', async (_label, current) => {
      const fixture = await ready(current);

      expect(cancelButton(fixture)).not.toBeNull();
    });

    it.each([
      ['a sent line', ticket([line('l1', { state: 'SENT', sentAt: SENT_AT })])],
      ['a live payment', ticket([line('l1')], { payments: [payment('pay1')] })],
      ['a closed ticket', ticket([line('l1')], { status: 'CLOSED' })],
    ])('does not offer it with %s', async (_label, current) => {
      const fixture = await ready(current);

      expect(cancelButton(fixture)).toBeNull();
    });

    it('asks first and says what will happen, then cancels and goes back to the tables', async () => {
      const navigated = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      const fixture = await ready(ticket([line('l1')]));

      await press(fixture, 'cancel-open');

      expect(inDialog('[data-testid="cancel-explain"]')?.textContent).toContain('sin enviar');
      expect(inDialog('[data-testid="cancel-explain"]')?.textContent).toContain('mesa');
      http.expectNone(`${TICKET}/cancel`);

      inDialog<HTMLButtonElement>('[data-testid="cancel-confirm"]')!.click();
      await fixture.whenStable();
      const request = http.expectOne(`${TICKET}/cancel`);
      expect(request.request.method).toBe('POST');
      request.flush(ticket([], { status: 'CANCELLED' }));
      await fixture.whenStable();

      expect(navigated).toHaveBeenCalledWith('/staff/tables');
    });

    it('keeps the ticket when the question is dismissed', async () => {
      const fixture = await ready(ticket([line('l1')]));

      await press(fixture, 'cancel-open');
      inDialog<HTMLButtonElement>('[data-testid="cancel-back"]')!.click();
      await fixture.whenStable();

      http.expectNone(`${TICKET}/cancel`);
      expect(inDialog('[data-testid="cancel-confirm"]')).toBeNull();
    });

    it('says a cancelled ticket was cancelled, not closed', async () => {
      const fixture = await ready(ticket([], { status: 'CANCELLED' }));

      expect(text(fixture, 'cancelled-notice')).toContain('cancelada');
      expect(find(fixture, 'closed-notice')).toBeNull();
    });
  });

  describe('the ticket note', () => {
    const field = (fixture: ComponentFixture<TicketEditor>) => find<HTMLTextAreaElement>(fixture, 'ticket-note')!;

    async function typeNote(fixture: ComponentFixture<TicketEditor>, value: string) {
      field(fixture).value = value;
      field(fixture).dispatchEvent(new Event('input'));
      await fixture.whenStable();
    }

    it('shows the note the ticket has', async () => {
      const fixture = await ready(ticket([line('l1')], { note: 'Cumpleaños' }));

      expect(field(fixture).value).toBe('Cumpleaños');
    });

    it('saves a new note', async () => {
      const fixture = await ready(ticket([line('l1')]));

      await typeNote(fixture, '  Sin hielo ');
      await press(fixture, 'ticket-note-save');

      const request = http.expectOne(`${TICKET}/note`);
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toEqual({ note: 'Sin hielo' });
      request.flush(ticket([line('l1')], { note: 'Sin hielo' }));
      await fixture.whenStable();

      expect(field(fixture).value).toBe('Sin hielo');
    });

    it('clears the note with an empty text', async () => {
      const fixture = await ready(ticket([line('l1')], { note: 'Cumpleaños' }));

      await typeNote(fixture, '');
      await press(fixture, 'ticket-note-save');

      expect(http.expectOne(`${TICKET}/note`).request.body).toEqual({ note: null });
    });

    it('offers no save while the text matches the saved note', async () => {
      const fixture = await ready(ticket([line('l1')], { note: 'Cumpleaños' }));

      expect(find<HTMLButtonElement>(fixture, 'ticket-note-save')!.disabled).toBe(true);
      await typeNote(fixture, 'Otra');
      expect(find<HTMLButtonElement>(fixture, 'ticket-note-save')!.disabled).toBe(false);
    });

    it('does not show the note editor on a closed ticket', async () => {
      const fixture = await ready(ticket([line('l1')], { status: 'CLOSED' }));

      expect(find(fixture, 'ticket-note')).toBeNull();
    });
  });

  describe('voiding a sent line', () => {
    const sent = () => ticket([line('l1', { state: 'SENT', sentAt: SENT_AT })]);

    async function openVoid(onBoard: BoardOrder[] = []) {
      const fixture = await ready(sent(), onBoard);
      await press(fixture, 'void-l1');
      http.expectOne(REASONS).flush([
        { type: 'VOID', code: 'Error' },
        { type: 'VOID', code: 'Other' },
      ]);
      await fixture.whenStable();
      return fixture;
    }

    const confirm = () => inDialog<HTMLButtonElement>('[data-testid="void-confirm"]')!;

    it('offers the void on a sent line only, not on an unsent or a voided one', async () => {
      const fixture = await ready(
        ticket([line('l1', { state: 'SENT', sentAt: SENT_AT }), line('l2'), line('l3', { state: 'VOIDED' })]),
      );

      expect(find(fixture, 'void-l1')).not.toBeNull();
      expect(find(fixture, 'void-l2')).toBeNull();
      expect(find(fixture, 'void-l3')).toBeNull();
    });

    it('lets a cashier void a line that is already ready', async () => {
      const fixture = await ready(sent(), board('l1'));

      expect(find<HTMLButtonElement>(fixture, 'void-l1')!.disabled).toBe(false);
    });

    it('tells a server to ask a cashier once the line is ready', async () => {
      role.set('SERVER');
      const fixture = await ready(sent(), board('l1'));

      expect(find(fixture, 'void-l1')).toBeNull();
      expect(text(fixture, 'void-blocked-l1')).toContain('pide a un cajero');
    });

    it('lets a server void a line that is not ready yet', async () => {
      role.set('SERVER');
      const fixture = await ready(sent());

      expect(find(fixture, 'void-l1')).not.toBeNull();
      expect(find(fixture, 'void-blocked-l1')).toBeNull();
    });

    it('asks for a reason from the active list and voids the line with it', async () => {
      const fixture = await openVoid();
      expect(confirm().disabled).toBe(true);
      const dialog = document.body.querySelector('[role="dialog"]') as HTMLElement;

      await chooseOption(fixture, dialog, 'void-reason', 'Error');
      expect(confirm().disabled).toBe(false);
      confirm().click();
      await fixture.whenStable();

      const request = http.expectOne(`${TICKET}/lines/l1/void`);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({ reason: 'Error', note: null });
      expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
      request.flush(ticket([line('l1', { state: 'VOIDED', sentAt: SENT_AT, voidReasonCode: 'Error' })]));
      await fixture.whenStable();

      expect(text(fixture, 'line-status-l1')).toContain('Anulada');
      expect(inDialog('[data-testid="void-confirm"]')).toBeNull();
    });

    it('needs a note for the reason "Other" and sends it', async () => {
      const fixture = await openVoid();
      const dialog = document.body.querySelector('[role="dialog"]') as HTMLElement;

      await chooseOption(fixture, dialog, 'void-reason', 'Otro');
      expect(confirm().disabled).toBe(true);
      const note = inDialog<HTMLTextAreaElement>('[data-testid="void-note"]')!;
      expect(note.getAttribute('maxlength')).toBe('280');
      note.value = '  Cliente se fue ';
      note.dispatchEvent(new Event('input'));
      await fixture.whenStable();
      expect(confirm().disabled).toBe(false);
      confirm().click();
      await fixture.whenStable();

      const request = http.expectOne(`${TICKET}/lines/l1/void`);
      expect(request.request.body).toEqual({ reason: 'Other', note: 'Cliente se fue' });
      request.flush(sent());
    });

    it('keeps the dialog and the idempotency key after a refusal', async () => {
      const fixture = await openVoid();
      const added = vi.spyOn(messages, 'add');
      const dialog = document.body.querySelector('[role="dialog"]') as HTMLElement;
      await chooseOption(fixture, dialog, 'void-reason', 'Error');

      confirm().click();
      await fixture.whenStable();
      const first = http.expectOne(`${TICKET}/lines/l1/void`);
      const key = first.request.headers.get('Idempotency-Key');
      first.flush(
        { type: 'about:blank', status: 409, code: 'preparation.line-already-ready', correlationId: 'c' },
        { status: 409, statusText: 'Conflict' },
      );
      await fixture.whenStable();
      http.expectOne(TICKET).flush(sent());
      flushBoard([]);
      await fixture.whenStable();

      expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
      expect(selectedLabel(dialog, 'void-reason')).toBe('Error');
      confirm().click();
      await fixture.whenStable();
      const second = http.expectOne(`${TICKET}/lines/l1/void`);
      expect(second.request.headers.get('Idempotency-Key')).toBe(key);
      second.flush(sent());
    });

    it('shows the reason "Other" as Otro, in the list and on the voided line', async () => {
      const fixture = await ready(
        ticket([line('l1', { state: 'VOIDED', sentAt: SENT_AT, voidReasonCode: 'Other' })]),
      );

      expect(text(fixture, 'line-status-l1')).toContain('Otro');
      expect(text(fixture, 'line-status-l1')).not.toContain('Other');
    });

    it('says the reasons could not be loaded and offers a retry', async () => {
      const fixture = await ready(sent());
      await press(fixture, 'void-l1');
      http.expectOne(REASONS).flush(
        { type: 'about:blank', status: 500, code: 'common.internal-error', correlationId: 'c' },
        { status: 500, statusText: 'Server Error' },
      );
      await fixture.whenStable();

      expect(inDialog('[data-testid="void-reasons-failed"]')?.textContent).toContain(
        'No se pudieron cargar los motivos',
      );
      expect(inDialog('[data-testid="void-reason-box"]')).toBeNull();

      inDialog<HTMLButtonElement>('[data-testid="void-retry"]')!.click();
      await fixture.whenStable();
      http.expectOne(REASONS).flush([{ type: 'VOID', code: 'Error' }]);
      await fixture.whenStable();

      expect(inDialog('[data-testid="void-reasons-failed"]')).toBeNull();
      expect(inDialog('[data-testid="void-reason-box"]')).not.toBeNull();
    });

    it('closes the dialog on cancel without calling the API', async () => {
      const fixture = await openVoid();

      inDialog<HTMLButtonElement>('[data-testid="void-cancel"]')!.click();
      await fixture.whenStable();

      expect(inDialog('[data-testid="void-confirm"]')).toBeNull();
    });
  });

  describe('taking payment', () => {
    const SHIFT = `${BASE}/shifts/current`;
    const PAYMENTS = `${TICKET}/payments`;
    const sentLine = (id: string, total = '10.00') =>
      line(id, { state: 'SENT', sentAt: SENT_AT, lineTotal: pen(total) });
    const sent = (overrides: Partial<Ticket> = {}) =>
      ticket([sentLine('l1', '30.00')], overrides);

    async function openPanel(current: Ticket = sent(), openShift = true) {
      const fixture = await ready(current);
      await press(fixture, 'pay-open');
      const read = http.expectOne(SHIFT);
      if (openShift) {
        read.flush(shift());
      } else {
        read.flush(
          { type: 'about:blank', status: 404, code: 'instore.shift-not-found', correlationId: 'c' },
          { status: 404, statusText: 'Not Found' },
        );
      }
      await fixture.whenStable();
      return fixture;
    }

    const type = (fixture: ComponentFixture<TicketEditor>, testId: string, value: string) => {
      const input = find<HTMLInputElement>(fixture, testId)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };

    it('offers the pay action to a cashier and an administrator, not to a server', async () => {
      const cashier = await ready(sent());
      expect(find(cashier, 'pay-open')).not.toBeNull();
      cashier.destroy();

      role.set('ADMIN');
      const admin = await ready(sent());
      expect(find(admin, 'pay-open')).not.toBeNull();
      admin.destroy();

      role.set('SERVER');
      const server = await ready(sent());
      expect(find(server, 'pay-open')).toBeNull();
    });

    it('offers no payment on a closed ticket', async () => {
      const fixture = await ready(sent({ status: 'CLOSED' }));

      expect(find(fixture, 'pay-open')).toBeNull();
    });

    it('shows what was paid and what is left, as the API states them', async () => {
      const fixture = await ready(
        sent({ paid: pen('10.00'), balance: pen('20.00'), payments: [payment('p1', { paid: pen('10.00') })] }),
      );

      expect(text(fixture, 'ticket-paid')).toContain('10.00');
      expect(text(fixture, 'ticket-balance')).toContain('20.00');
      expect(text(fixture, 'payment-p1')).toContain('Efectivo');
    });

    it('reads the shift when the panel opens and prefills the balance', async () => {
      const fixture = await openPanel();

      expect(find<HTMLInputElement>(fixture, 'pay-amount')!.value).toBe('30.00');
      expect(find(fixture, 'no-shift')).toBeNull();
    });

    it('points to the register when no shift is open and blocks the payment', async () => {
      const fixture = await openPanel(sent(), false);

      expect(find(fixture, 'no-shift')).not.toBeNull();
      expect(host(fixture).querySelector('a[href="/staff/register"]')).not.toBeNull();
      expect(find<HTMLButtonElement>(fixture, 'pay-submit')!.disabled).toBe(true);
    });

    it('blocks the payment while the ticket has unsent lines', async () => {
      const fixture = await openPanel(ticket([line('l1'), sentLine('l2')]));

      expect(find(fixture, 'unsent-lines')).not.toBeNull();
      expect(find<HTMLButtonElement>(fixture, 'pay-submit')!.disabled).toBe(true);
    });

    it('takes the whole balance in cash and shows the change the API returns', async () => {
      const fixture = await openPanel();
      type(fixture, 'pay-received', '50');
      await fixture.whenStable();

      await press(fixture, 'pay-submit');
      const request = http.expectOne(PAYMENTS);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({
        method: 'CASH',
        amount: pen('30.00'),
        receivedAmount: pen('50.00'),
      });
      expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
      request.flush(
        sent({
          status: 'CLOSED',
          paid: pen('30.00'),
          balance: pen('0.00'),
          payments: [payment('p1')],
        }),
        { status: 201, statusText: 'Created' },
      );
      await fixture.whenStable();

      expect(text(fixture, 'closed-notice')).toContain('La cuenta está cerrada');
      expect(text(fixture, 'payment-p1')).toContain('20.00');
      expect(find(fixture, 'pay-submit')).toBeNull();
    });

    it('does not let the cash received be less than the amount', async () => {
      const fixture = await openPanel();
      type(fixture, 'pay-received', '10');
      await fixture.whenStable();

      expect(find<HTMLButtonElement>(fixture, 'pay-submit')!.disabled).toBe(true);
    });

    it('pays by card without an amount received', async () => {
      const fixture = await openPanel();
      await chooseOption(fixture, host(fixture), 'pay-method', 'Tarjeta');

      expect(find(fixture, 'pay-received')).toBeNull();
      await press(fixture, 'pay-submit');

      const request = http.expectOne(PAYMENTS);
      expect(request.request.body).toEqual({ method: 'CARD', amount: pen('30.00') });
      request.flush(sent(), { status: 201, statusText: 'Created' });
    });

    it('takes a partial payment and offers the new balance for the next one', async () => {
      const fixture = await openPanel();
      await chooseOption(fixture, host(fixture), 'pay-method', 'Billetera');
      type(fixture, 'pay-amount', '10');
      await fixture.whenStable();

      await press(fixture, 'pay-submit');
      const request = http.expectOne(PAYMENTS);
      expect(request.request.body).toEqual({ method: 'WALLET', amount: pen('10.00') });
      request.flush(
        sent({
          paid: pen('10.00'),
          balance: pen('20.00'),
          payments: [payment('p1', { method: 'WALLET', paid: pen('10.00'), received: null, change: null })],
        }),
        { status: 201, statusText: 'Created' },
      );
      await fixture.whenStable();

      expect(find<HTMLInputElement>(fixture, 'pay-amount')!.value).toBe('20.00');
      expect(text(fixture, 'ticket-balance')).toContain('20.00');
      expect(text(fixture, 'payment-p1')).toContain('Billetera');
    });

    it('retries a refused payment with the same idempotency key', async () => {
      const fixture = await openPanel();
      const added = vi.spyOn(messages, 'add');
      await chooseOption(fixture, host(fixture), 'pay-method', 'Tarjeta');

      await press(fixture, 'pay-submit');
      const first = http.expectOne(PAYMENTS);
      const key = first.request.headers.get('Idempotency-Key');
      first.flush(
        { type: 'about:blank', status: 409, code: 'instore.no-open-shift', correlationId: 'c' },
        { status: 409, statusText: 'Conflict' },
      );
      await fixture.whenStable();
      http.expectOne(TICKET).flush(sent());
      flushBoard([]);
      http.expectOne(SHIFT).flush(shift());
      await fixture.whenStable();

      expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
      await press(fixture, 'pay-submit');
      const second = http.expectOne(PAYMENTS);
      expect(second.request.headers.get('Idempotency-Key')).toBe(key);
      second.flush(sent(), { status: 201, statusText: 'Created' });
    });

    it('reads the shift again after a payment refused for the shift, and blocks the payment', async () => {
      const fixture = await openPanel();
      await chooseOption(fixture, host(fixture), 'pay-method', 'Tarjeta');

      await press(fixture, 'pay-submit');
      http.expectOne(PAYMENTS).flush(
        { type: 'about:blank', status: 409, code: 'instore.shift-closing', correlationId: 'c' },
        { status: 409, statusText: 'Conflict' },
      );
      await fixture.whenStable();
      http.expectOne(TICKET).flush(sent());
      flushBoard([]);
      http.expectOne(SHIFT).flush(
        { type: 'about:blank', status: 404, code: 'instore.shift-not-found', correlationId: 'c' },
        { status: 404, statusText: 'Not Found' },
      );
      await fixture.whenStable();

      expect(find(fixture, 'no-shift')).not.toBeNull();
      expect(find<HTMLButtonElement>(fixture, 'pay-submit')!.disabled).toBe(true);
    });

    it('does not read the shift again after a refusal that has nothing to do with it', async () => {
      const fixture = await openPanel();
      await chooseOption(fixture, host(fixture), 'pay-method', 'Tarjeta');

      await press(fixture, 'pay-submit');
      http.expectOne(PAYMENTS).flush(
        { type: 'about:blank', status: 409, code: 'instore.ticket-closed', correlationId: 'c' },
        { status: 409, statusText: 'Conflict' },
      );
      await fixture.whenStable();
      http.expectOne(TICKET).flush(sent());
      flushBoard([]);
      await fixture.whenStable();

      expect(find(fixture, 'no-shift')).toBeNull();
    });

    it('pays the chosen lines by items with their total', async () => {
      const fixture = await openPanel(
        ticket([sentLine('l1', '12.50'), sentLine('l2', '7.50'), sentLine('l3', '10.00')]),
      );
      await chooseOption(fixture, host(fixture), 'pay-method', 'Tarjeta');

      host(fixture).querySelector<HTMLInputElement>('[data-testid="pay-line-l1"] input')!.click();
      host(fixture).querySelector<HTMLInputElement>('[data-testid="pay-line-l2"] input')!.click();
      await fixture.whenStable();

      expect(find<HTMLInputElement>(fixture, 'pay-amount')!.value).toBe('20.00');
      expect(find<HTMLInputElement>(fixture, 'pay-amount')!.disabled).toBe(true);
      await press(fixture, 'pay-submit');
      const request = http.expectOne(PAYMENTS);
      expect(request.request.body).toEqual({
        method: 'CARD',
        amount: pen('20.00'),
        lineIds: ['l1', 'l2'],
      });
      request.flush(sent(), { status: 201, statusText: 'Created' });
    });

    it('drops a picked line that a re-read shows as already paid and pays the balance instead', async () => {
      const fixture = await openPanel(ticket([sentLine('l1', '12.50'), sentLine('l2', '7.50')]));
      await chooseOption(fixture, host(fixture), 'pay-method', 'Tarjeta');
      host(fixture).querySelector<HTMLInputElement>('[data-testid="pay-line-l1"] input')!.click();
      await fixture.whenStable();
      expect(find<HTMLInputElement>(fixture, 'pay-amount')!.disabled).toBe(true);

      realtime.fire('/topic/tables');
      http.expectOne(TICKET).flush(
        ticket([sentLine('l1', '12.50'), sentLine('l2', '7.50')], {
          paid: pen('12.50'),
          balance: pen('7.50'),
          payments: [payment('p1', { paid: pen('12.50'), lineIds: ['l1'] })],
        }),
      );
      await fixture.whenStable();

      expect(find<HTMLInputElement>(fixture, 'pay-amount')!.disabled).toBe(false);
      expect(find<HTMLInputElement>(fixture, 'pay-amount')!.value).toBe('7.50');
      await press(fixture, 'pay-submit');
      const request = http.expectOne(PAYMENTS);
      expect(request.request.body).toEqual({ method: 'CARD', amount: pen('7.50') });
      request.flush(sent(), { status: 201, statusText: 'Created' });
    });

    it('does not offer a line that a payment already covers', async () => {
      const fixture = await openPanel(
        ticket([sentLine('l1'), sentLine('l2')], {
          payments: [payment('p1', { lineIds: ['l1'] })],
        }),
      );

      expect(find(fixture, 'pay-line-l1')).toBeNull();
      expect(find(fixture, 'pay-line-l2')).not.toBeNull();
    });

    it('splits the balance in parts from the API and fills the amount with the chosen one', async () => {
      const fixture = await openPanel();
      await chooseOption(fixture, host(fixture), 'pay-parts', '3');
      await press(fixture, 'split-load');

      const request = http.expectOne(`${TICKET}/split?parts=3`);
      expect(request.request.method).toBe('GET');
      request.flush({ parts: [pen('10.00'), pen('10.00'), pen('10.00')] });
      await fixture.whenStable();

      expect(host(fixture).querySelectorAll('[data-testid^="split-part-"]')).toHaveLength(3);
      await press(fixture, 'split-part-1');

      expect(find<HTMLInputElement>(fixture, 'pay-amount')!.value).toBe('10.00');
    });

    it('marks a split part as paid after its payment', async () => {
      const fixture = await openPanel();
      await chooseOption(fixture, host(fixture), 'pay-parts', '2');
      await press(fixture, 'split-load');
      http.expectOne(`${TICKET}/split?parts=2`).flush({ parts: [pen('15.00'), pen('15.00')] });
      await fixture.whenStable();
      await press(fixture, 'split-part-0');
      await chooseOption(fixture, host(fixture), 'pay-method', 'Tarjeta');

      await press(fixture, 'pay-submit');
      http.expectOne(PAYMENTS).flush(
        sent({ paid: pen('15.00'), balance: pen('15.00'), payments: [payment('p1', { paid: pen('15.00') })] }),
        { status: 201, statusText: 'Created' },
      );
      await fixture.whenStable();

      expect(find<HTMLButtonElement>(fixture, 'split-part-0')!.disabled).toBe(true);
      expect(find<HTMLButtonElement>(fixture, 'split-part-1')!.disabled).toBe(false);
    });
  });

  describe('voiding a payment or a sale', () => {
    const open = (overrides: Partial<Ticket> = {}) =>
      ticket([line('l1', { state: 'SENT', sentAt: SENT_AT })], {
        paid: pen('10.00'),
        balance: pen('20.00'),
        payments: [payment('p1', { paid: pen('10.00') })],
        ...overrides,
      });
    const closedSale = (overrides: Partial<Ticket> = {}) =>
      open({ status: 'CLOSED', paid: pen('30.00'), balance: pen('0.00'), ...overrides });
    const reasons = [
      { type: 'VOID', code: 'Error' },
      { type: 'VOID', code: 'Other' },
    ];
    const dialog = () => document.body.querySelector('[role="dialog"]') as HTMLElement;

    it('offers to void a live payment of an open ticket, not a voided one', async () => {
      const fixture = await ready(
        open({
          payments: [payment('p1'), payment('p2', { voided: true, voidReasonCode: 'Error' })],
        }),
      );

      expect(find(fixture, 'void-payment-p1')).not.toBeNull();
      expect(find(fixture, 'void-payment-p2')).toBeNull();
      expect(text(fixture, 'payment-p2')).toContain('Anulado');
    });

    it('offers no payment or sale void to a server', async () => {
      role.set('SERVER');
      const openTicket = await ready(open());
      expect(find(openTicket, 'void-payment-p1')).toBeNull();
      openTicket.destroy();

      const closed = await ready(closedSale());
      expect(find(closed, 'void-sale')).toBeNull();
    });

    it('voids a payment with a reason and no idempotency key', async () => {
      const fixture = await ready(open());
      await press(fixture, 'void-payment-p1');
      http.expectOne(REASONS).flush(reasons);
      await fixture.whenStable();
      await chooseOption(fixture, dialog(), 'void-dialog-reason', 'Error');

      inDialog<HTMLButtonElement>('[data-testid="vd-confirm"]')!.click();
      await fixture.whenStable();

      const request = http.expectOne(`${TICKET}/payments/p1/void`);
      expect(request.request.method).toBe('POST');
      expect(request.request.headers.has('Idempotency-Key')).toBe(false);
      expect(request.request.body).toEqual({ reason: 'Error', note: null });
      request.flush(
        open({
          paid: pen('0.00'),
          balance: pen('30.00'),
          payments: [payment('p1', { voided: true, voidReasonCode: 'Error' })],
        }),
      );
      await fixture.whenStable();

      expect(text(fixture, 'payment-p1')).toContain('Anulado');
      expect(find(fixture, 'void-payment-p1')).toBeNull();
      expect(inDialog('[data-testid="vd-confirm"]')).toBeNull();
    });

    it('offers the sale void on a closed ticket and voids it with a reason', async () => {
      const fixture = await ready(closedSale());
      await press(fixture, 'void-sale');
      http.expectOne(REASONS).flush(reasons);
      await fixture.whenStable();
      await chooseOption(fixture, dialog(), 'void-dialog-reason', 'Error');

      inDialog<HTMLButtonElement>('[data-testid="vd-confirm"]')!.click();
      await fixture.whenStable();

      const request = http.expectOne(`${TICKET}/void`);
      expect(request.request.method).toBe('POST');
      expect(request.request.headers.has('Idempotency-Key')).toBe(false);
      expect(request.request.body).toEqual({ reason: 'Error', note: null });
      request.flush(
        closedSale({
          saleVoid: { reasonCode: 'Error', note: null, voidedAt: SENT_AT, voidedBy: 'u1' },
        }),
      );
      await fixture.whenStable();

      expect(text(fixture, 'sale-voided')).toContain('Venta anulada');
      expect(find(fixture, 'void-sale')).toBeNull();
    });

    it('offers no sale void on an open ticket or on a sale already voided', async () => {
      const openTicket = await ready(open());
      expect(find(openTicket, 'void-sale')).toBeNull();
      openTicket.destroy();

      const voided = await ready(
        closedSale({ saleVoid: { reasonCode: 'Error', note: null, voidedAt: SENT_AT, voidedBy: 'u1' } }),
      );
      expect(find(voided, 'void-sale')).toBeNull();
    });

    it('reads the shift again when a payment void is refused for the shift and the panel is open', async () => {
      const fixture = await ready(open());
      await press(fixture, 'pay-open');
      http.expectOne(`${BASE}/shifts/current`).flush(shift());
      await fixture.whenStable();
      await press(fixture, 'void-payment-p1');
      http.expectOne(REASONS).flush(reasons);
      await fixture.whenStable();
      await chooseOption(fixture, dialog(), 'void-dialog-reason', 'Error');

      inDialog<HTMLButtonElement>('[data-testid="vd-confirm"]')!.click();
      await fixture.whenStable();
      http.expectOne(`${TICKET}/payments/p1/void`).flush(
        { type: 'about:blank', status: 409, code: 'instore.no-open-shift', correlationId: 'c' },
        { status: 409, statusText: 'Conflict' },
      );
      await fixture.whenStable();
      http.expectOne(TICKET).flush(open());
      flushBoard([]);
      http.expectOne(`${BASE}/shifts/current`).flush(
        { type: 'about:blank', status: 404, code: 'instore.shift-not-found', correlationId: 'c' },
        { status: 404, statusText: 'Not Found' },
      );
      await fixture.whenStable();

      expect(find(fixture, 'no-shift')).not.toBeNull();
    });

    it('does not read the shift for a sale void refused for the shift when the panel was never opened', async () => {
      const fixture = await ready(closedSale());
      await press(fixture, 'void-sale');
      http.expectOne(REASONS).flush(reasons);
      await fixture.whenStable();
      await chooseOption(fixture, dialog(), 'void-dialog-reason', 'Error');

      inDialog<HTMLButtonElement>('[data-testid="vd-confirm"]')!.click();
      await fixture.whenStable();
      http.expectOne(`${TICKET}/void`).flush(
        { type: 'about:blank', status: 409, code: 'instore.no-open-shift', correlationId: 'c' },
        { status: 409, statusText: 'Conflict' },
      );
      await fixture.whenStable();
      http.expectOne(TICKET).flush(closedSale());
      flushBoard([]);
      await fixture.whenStable();
    });

    it('keeps the dialog and shows the refusal when the sale void is not permitted', async () => {
      const fixture = await ready(closedSale());
      const added = vi.spyOn(messages, 'add');
      await press(fixture, 'void-sale');
      http.expectOne(REASONS).flush(reasons);
      await fixture.whenStable();
      await chooseOption(fixture, dialog(), 'void-dialog-reason', 'Error');

      inDialog<HTMLButtonElement>('[data-testid="vd-confirm"]')!.click();
      await fixture.whenStable();
      http.expectOne(`${TICKET}/void`).flush(
        { type: 'about:blank', status: 403, code: 'instore.sale-void-not-permitted', correlationId: 'c' },
        { status: 403, statusText: 'Forbidden' },
      );
      await fixture.whenStable();
      http.expectOne(TICKET).flush(closedSale());
      flushBoard([]);
      await fixture.whenStable();

      expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
      expect(inDialog('[data-testid="vd-confirm"]')).not.toBeNull();
    });
  });

  it('enables the actions again when a command ends while the reconnect read is in flight', async () => {
    const fixture = await ready();
    await press(fixture, 'send');
    const send = http.expectOne(`${TICKET}/send`);

    realtime.connected.set(false);
    await fixture.whenStable();
    realtime.connected.set(true);
    realtime.fire('/topic/board');
    await fixture.whenStable();
    const sentTicket = ticket([line('l1', { state: 'SENT', sentAt: SENT_AT })]);
    send.flush(sentTicket);
    await fixture.whenStable();
    http.match(TICKET).forEach((request) => request.flush(sentTicket));
    http.match(BOARD).forEach((request) => request.flush([]));
    http.match(BOARD_READY).forEach((request) => request.flush([]));
    await fixture.whenStable();

    expect(find(fixture, 'offline-banner')).toBeNull();
    expect(find<HTMLButtonElement>(fixture, 'product-p1')!.disabled).toBe(false);
  });

  it('reads the ticket and the board again on a board signal', async () => {
    const fixture = await ready();

    realtime.fire('/topic/board');
    http.expectOne(TICKET).flush(ticket([line('l1', { state: 'SENT', sentAt: '2026-10-13T10:05:00Z' })]));
    flushBoard(board('l1'));
    await fixture.whenStable();

    expect(text(fixture, 'line-status-l1')).toContain('Lista');
  });

  it('reads only the ticket on a tables signal and only the menu on a catalog signal', async () => {
    const fixture = await ready();

    realtime.fire('/topic/tables');
    http.expectOne(TICKET).flush(ticket([line('l1')], { billRequested: true }));
    realtime.fire('/topic/catalog');
    http.expectOne(MENU).flush(menu());
    await fixture.whenStable();

    expect(text(fixture, 'bill')).toContain('Quitar el pedido de cuenta');
  });

  it('is read-only once the ticket is closed', async () => {
    const fixture = await ready(ticket([line('l1', { state: 'SENT', sentAt: '2026-10-13T10:05:00Z' })], {
      status: 'CLOSED',
    }));

    expect(text(fixture, 'closed-notice')).toContain('La cuenta está cerrada');
    expect(find(fixture, 'send')).toBeNull();
    expect(find(fixture, 'product-p1')).toBeNull();
  });

  it('says so when the ticket does not exist', async () => {
    const fixture = TestBed.createComponent(TicketEditor);
    await fixture.whenStable();
    http
      .expectOne(TICKET)
      .flush(
        { type: 'about:blank', status: 404, code: 'instore.ticket-not-found', correlationId: 'c' },
        { status: 404, statusText: 'Not Found' },
      );
    http.expectOne(MENU).flush(menu());
    flushBoard([]);
    await fixture.whenStable();

    expect(text(fixture, 'missing-notice')).toContain('La cuenta no existe');
  });

  it('offers a retry when the ticket cannot be read', async () => {
    const fixture = TestBed.createComponent(TicketEditor);
    await fixture.whenStable();
    http.expectOne(TICKET).flush(
      { type: 'about:blank', status: 500, code: 'common.internal-error', correlationId: 'c' },
      { status: 500, statusText: 'Server Error' },
    );
    http.expectOne(MENU).flush(menu());
    flushBoard([]);
    await fixture.whenStable();

    expect(text(fixture, 'offline-banner')).toContain('No se pudo cargar la cuenta');

    await press(fixture, 'retry');
    http.expectOne(TICKET).flush(ticket([line('l1')]));
    flushBoard([]);
    await fixture.whenStable();

    expect(text(fixture, 'ticket-title')).toContain('Mesa 1');
  });
});
