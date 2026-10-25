import { request, type APIRequestContext } from '@playwright/test';
import { adminCredentials, apiUrl } from './env';
import { assertDedicatedAccount, isDedicatedAccount } from './target';

export const E2E_NAME = /^e2e-\d{13}-/;

export interface Money {
  amount: string;
  currency: string;
}
interface Versioned {
  id: string;
  etag: string;
  name: string;
}
interface Product extends Versioned {
  active: boolean;
  categoryId: string;
  description: string | null;
  displayOrder: number;
  price: Money;
  quickSalePinned: boolean;
  modifierGroupIds: string[];
  allergens: string[];
}
interface Category extends Versioned {
  active: boolean;
}
interface Page<T> {
  content: T[];
  totalPages: number;
}
interface Table extends Versioned {
  active: boolean;
}
type ReasonType = 'VOID' | 'COMP' | 'CASH_OUT' | 'STOCK_ADJUSTMENT';
interface Reason {
  id: string;
  etag: string;
  code: string;
  type: ReasonType;
  active: boolean;
}
interface Shift {
  id: string;
  status: 'OPEN' | 'CLOSED';
}
interface TicketSummary {
  id: string;
  tableName: string | null;
  counterName: string | null;
}
interface TicketDetail {
  id: string;
  status: 'OPEN' | 'CLOSED';
  balance: Money;
  lines: { id: string; state: 'UNSENT' | 'SENT' | 'VOIDED' }[];
}
interface BoardOrder {
  id: string;
  label: string | null;
  status: 'WAITING' | 'PREPARING' | 'READY' | 'HANDED_OVER' | 'CANCELLED';
  markers: { id: string; acknowledgedAt: string | null }[];
}
interface TableGridEntry {
  id: string;
  status: 'FREE' | 'OCCUPIED' | 'WAITING_FOR_PAYMENT';
}

export function uniqueName(label: string): string {
  return `e2e-${Date.now()}-${label}`;
}

export function minorUnits(money: Money): number {
  const [whole = '0', cents = '0'] = money.amount.split('.');
  return Number(whole) * 100 + Number(cents.padEnd(2, '0'));
}

type Attempt = (what: string, action: () => Promise<unknown>) => Promise<void>;

export async function withAdminApi<T>(action: (api: AdminApi) => Promise<T>): Promise<T> {
  const api = await AdminApi.create();
  try {
    return await action(api);
  } finally {
    await api.dispose();
  }
}

export class AdminApi {
  private constructor(private readonly http: APIRequestContext) {}

  // A separate sign-in from the browser sessions: its refresh cookie is never reused.
  static async create(): Promise<AdminApi> {
    const { email, password } = adminCredentials();
    const anonymous = await request.newContext({ baseURL: apiUrl });
    const response = await anonymous.post('/api/v1/auth/login', { data: { email, password } });
    if (!response.ok()) {
      throw new Error(`Admin API sign-in failed with status ${response.status()}.`);
    }
    const { accessToken } = (await response.json()) as { accessToken: string };
    await anonymous.dispose();
    return new AdminApi(
      await request.newContext({
        baseURL: apiUrl,
        extraHTTPHeaders: { Authorization: `Bearer ${accessToken}` },
      }),
    );
  }

  async dispose(): Promise<void> {
    await this.http.dispose();
  }

  async currency(): Promise<string> {
    const settings = await this.get<{ currency: string }>('/api/v1/admin/settings');
    return settings.currency;
  }

  async createCategory(name: string): Promise<Category> {
    return this.send<Category>('POST', '/api/v1/admin/categories', { name, displayOrder: 0 });
  }

  async createProduct(
    name: string,
    categoryId: string,
    options: { price?: string; quickSalePinned?: boolean } = {},
  ): Promise<Product> {
    const currency = await this.currency();
    return this.send<Product>('POST', '/api/v1/admin/products', {
      name,
      categoryId,
      displayOrder: 0,
      price: { amount: options.price ?? '5.00', currency },
      quickSalePinned: options.quickSalePinned ?? false,
    });
  }

  async createTable(name: string): Promise<Table> {
    return this.send<Table>('POST', '/api/v1/admin/tables', { name });
  }

  async createReason(type: ReasonType, code: string): Promise<Reason> {
    return this.send<Reason>('POST', '/api/v1/admin/reasons', { type, code });
  }

  async registerDifferenceThreshold(): Promise<Money> {
    const settings = await this.get<{ registerDifferenceThreshold: Money }>('/api/v1/admin/settings');
    return settings.registerDifferenceThreshold;
  }

  // The account has at most one open shift, so a journey that needs a known state replaces whatever is open.
  async openShift(openingFloat: string): Promise<void> {
    await this.closeOpenShift();
    const currency = await this.currency();
    await this.send(
      'POST',
      '/api/v1/shifts',
      { openingFloat: { amount: openingFloat, currency } },
      undefined,
      { 'Idempotency-Key': crypto.randomUUID() },
    );
  }

  async closeOpenShift(): Promise<void> {
    const shift = await this.currentShift();
    if (shift) {
      assertDedicatedAccount(adminCredentials().email);
      await this.send('POST', `/api/v1/shifts/${shift.id}/force-close`, { reason: 'e2e cleanup' });
    }
  }

  // Removes everything this suite created, whichever run left it behind. Products and categories are only
  // deactivated here (the API also deletes unused ones; the sweep leaves that alone); modifier groups are deleted
  // after being detached from the products that used them. Items are processed independently and failures are
  // reported together at the end.
  async cleanUp(): Promise<void> {
    const errors: string[] = [];
    const attempt: Attempt = async (what, action) => {
      try {
        await action();
      } catch (error) {
        errors.push(`${what}: ${error instanceof Error ? error.message : String(error)}`);
      }
    };

    // Tickets and cards of a failed run are finished first: a table with an open ticket cannot be retired.
    await attempt('open tickets', () => this.settleTickets());
    await attempt('board orders', () => this.finishBoardOrders());
    if (isDedicatedAccount(adminCredentials().email)) {
      await attempt('open shift', () => this.closeOpenShift());
    } else {
      console.warn('Left the open shift alone: E2E_ADMIN_EMAIL is not a dedicated e2e account.');
    }

    await attempt('products and modifier groups', () => this.sweepCatalog(attempt));
    await attempt('categories', () => this.sweepCategories(attempt));
    await attempt('tables', () => this.sweepTables(attempt));
    await attempt('reasons', () => this.sweepReasons(attempt));
    if (errors.length > 0) {
      throw new Error([`Cleanup left ${errors.length} item(s) behind:`, ...errors].join('\n'));
    }
  }

  private async sweepCatalog(attempt: Attempt): Promise<void> {
    const products = await this.products();
    const usedByOthers = new Set(
      products.filter((product) => !E2E_NAME.test(product.name)).flatMap((product) => product.modifierGroupIds),
    );
    for (const product of products.filter((candidate) => E2E_NAME.test(candidate.name))) {
      await attempt(`product ${product.id}`, async () => {
        let current: Product = product;
        if (current.modifierGroupIds.length > 0) {
          current = await this.send<Product>(
            'PUT',
            `/api/v1/admin/products/${current.id}`,
            this.saveBody(current, []),
            current.etag,
          );
        }
        if (current.active) {
          await this.send('POST', `/api/v1/admin/products/${current.id}/deactivate`, undefined, current.etag);
        }
      });
    }
    for (const group of await this.get<Versioned[]>('/api/v1/admin/modifier-groups')) {
      if (!E2E_NAME.test(group.name)) continue;
      if (usedByOthers.has(group.id)) {
        console.warn(`Skipped modifier group ${group.id}: it is attached to a product this suite did not create.`);
        continue;
      }
      await attempt(`modifier group ${group.id}`, () =>
        this.send('DELETE', `/api/v1/admin/modifier-groups/${group.id}`, undefined, group.etag),
      );
    }
  }

  private async sweepCategories(attempt: Attempt): Promise<void> {
    for (const category of await this.get<Category[]>('/api/v1/admin/categories')) {
      if (E2E_NAME.test(category.name) && category.active) {
        await attempt(`category ${category.id}`, () =>
          this.send('POST', `/api/v1/admin/categories/${category.id}/deactivate`, undefined, category.etag),
        );
      }
    }
  }

  private async sweepTables(attempt: Attempt): Promise<void> {
    const occupied = new Set(
      (await this.get<TableGridEntry[]>('/api/v1/tables'))
        .filter((entry) => entry.status !== 'FREE')
        .map((entry) => entry.id),
    );
    for (const table of await this.get<Table[]>('/api/v1/admin/tables')) {
      if (!E2E_NAME.test(table.name) || !table.active) continue;
      if (occupied.has(table.id)) {
        console.warn(`Skipped table ${table.id}: it still has an open ticket.`);
        continue;
      }
      await attempt(`table ${table.id}`, () =>
        this.send('POST', `/api/v1/admin/tables/${table.id}/deactivate`, undefined, table.etag),
      );
    }
  }

  private async sweepReasons(attempt: Attempt): Promise<void> {
    for (const type of ['VOID', 'COMP', 'CASH_OUT', 'STOCK_ADJUSTMENT'] as const) {
      await attempt(`reasons of type ${type}`, async () => {
        for (const reason of await this.get<Reason[]>(`/api/v1/admin/reasons?type=${type}`)) {
          if (E2E_NAME.test(reason.code) && reason.active) {
            await attempt(`reason ${reason.id}`, () =>
              this.send('POST', `/api/v1/admin/reasons/${reason.id}/deactivate`, undefined, reason.etag),
            );
          }
        }
      });
    }
  }

  private async currentShift(): Promise<Shift | null> {
    const response = await this.http.get('/api/v1/shifts/current');
    if (response.status() === 404) return null;
    if (!response.ok()) {
      throw new Error(`GET /api/v1/shifts/current failed with status ${response.status()}: ${await response.text()}`);
    }
    return (await response.json()) as Shift;
  }

  // Closes the open tickets of this suite with a cash payment, which is the only way a ticket can end.
  private async settleTickets(): Promise<void> {
    const open = (await this.get<{ content: TicketSummary[] }>('/api/v1/tickets')).content;
    for (const summary of open.filter((ticket) => E2E_NAME.test(ticket.tableName ?? ticket.counterName ?? ''))) {
      const initial = await this.get<TicketDetail>(`/api/v1/tickets/${summary.id}`);
      for (const line of initial.lines.filter((candidate) => candidate.state === 'UNSENT')) {
        await this.send('DELETE', `/api/v1/tickets/${summary.id}/lines/${line.id}`);
      }
      let { balance } = await this.get<TicketDetail>(`/api/v1/tickets/${summary.id}`);
      if (minorUnits(balance) === 0) {
        balance = await this.addChargeableLine(summary.id);
      }
      if (!(await this.currentShift())) await this.openShift('0.00');
      await this.send(
        'POST',
        `/api/v1/tickets/${summary.id}/payments`,
        { method: 'CASH', amount: balance, receivedAmount: balance },
        undefined,
        { 'Idempotency-Key': crypto.randomUUID() },
      );
    }
  }

  // A ticket only ends by a payment, and one with nothing left to charge (only voided lines) has none to take: give
  // it a line, so it can be sent and paid and its table retired. The product and category go with the sweep.
  private async addChargeableLine(ticketId: string): Promise<Money> {
    const category = await this.createCategory(uniqueName('settle-category'));
    const product = await this.createProduct(uniqueName('settle'), category.id);
    await this.send(
      'POST',
      `/api/v1/tickets/${ticketId}/lines`,
      { productId: product.id, quantity: 1, allergy: false },
      undefined,
      { 'Idempotency-Key': crypto.randomUUID() },
    );
    await this.send('POST', `/api/v1/tickets/${ticketId}/send`);
    return (await this.get<TicketDetail>(`/api/v1/tickets/${ticketId}`)).balance;
  }

  private async finishBoardOrders(): Promise<void> {
    const cards = [
      ...(await this.get<BoardOrder[]>('/api/v1/board/tickets')),
      ...(await this.get<BoardOrder[]>('/api/v1/board/ready')),
    ];
    const errors: string[] = [];
    for (const card of cards.filter((candidate) => candidate.label && E2E_NAME.test(candidate.label))) {
      try {
        let status = card.status;
        if (status === 'WAITING') {
          status = (await this.send<BoardOrder>('POST', `/api/v1/board/orders/${card.id}/start`)).status;
        }
        for (const marker of card.markers.filter((candidate) => candidate.acknowledgedAt === null)) {
          await this.send('POST', `/api/v1/board/markers/${marker.id}/ack`);
        }
        if (status === 'PREPARING') {
          status = (await this.send<BoardOrder>('POST', `/api/v1/board/orders/${card.id}/ready`)).status;
        }
        if (status === 'READY') {
          await this.send('POST', `/api/v1/board/orders/${card.id}/handover`);
        }
      } catch (error) {
        errors.push(`card ${card.id}: ${error instanceof Error ? error.message : String(error)}`);
      }
    }
    if (errors.length > 0) throw new Error(errors.join('\n'));
  }

  private saveBody(product: Product, modifierGroupIds: string[]) {
    return {
      name: product.name,
      description: product.description,
      categoryId: product.categoryId,
      displayOrder: product.displayOrder,
      price: product.price,
      quickSalePinned: product.quickSalePinned,
      allergens: product.allergens,
      modifierGroupIds,
    };
  }

  private async products(): Promise<Product[]> {
    const all: Product[] = [];
    for (let page = 0; ; page++) {
      const result = await this.get<Page<Product>>(`/api/v1/admin/products?page=${page}&size=100`);
      all.push(...result.content);
      if (page + 1 >= result.totalPages) return all;
    }
  }

  private async get<T>(path: string): Promise<T> {
    return this.send<T>('GET', path);
  }

  private async send<T>(
    method: string,
    path: string,
    data?: unknown,
    etag?: string,
    headers: Record<string, string> = {},
  ): Promise<T> {
    const response = await this.http.fetch(path, {
      method,
      data,
      headers: etag ? { ...headers, 'If-Match': etag } : headers,
    });
    if (!response.ok()) {
      throw new Error(`${method} ${path} failed with status ${response.status()}: ${await response.text()}`);
    }
    return response.status() === 204 ? (undefined as T) : ((await response.json()) as T);
  }
}
