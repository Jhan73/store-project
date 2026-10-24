import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MessageService } from 'primeng/api';
import type {
  LineRequest,
  Menu,
  MenuProduct,
  Money,
  PaymentMethod,
  QuickSaleItem,
  Ticket,
} from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { fromMinorUnits, safeMinorUnits } from '../../../core/money/money';
import { IntentKeys } from '../../../shared/state/intent-keys';
import { PendingIds } from '../../../shared/state/pending-ids';
import { UndoNotice } from '../../../shared/state/undo-notice';
import { UndoQueue } from '../../../shared/state/undo-queue';
import { unitPriceOf } from '../../../shared/ui/product-configurator/configurator-rules';
import { RegisterApi } from '../register/register-api';
import { isShiftStateError } from '../register/shift-store';

const MAX_QUANTITY = 99;

export interface CartLine {
  readonly key: string;
  readonly productId: string;
  readonly name: string;
  readonly quantity: number;
  // A preview from the menu prices; the sale's own total comes back from the API.
  readonly unitPrice: Money;
  readonly modifierOptionIds: readonly string[];
  readonly modifierNames: string;
  readonly note: string | null;
  readonly allergy: boolean;
}

export interface SellInput {
  readonly customerName: string;
  readonly method: PaymentMethod;
  // An amount string like "20.00", for cash only.
  readonly received: string | null;
}

// Provided by the screen: the cart and the sale in progress belong to it.
@Injectable()
export class QuickSaleStore {
  private readonly api = inject(RegisterApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly messages = inject(MessageService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly keys = new IntentKeys();
  private readonly undoNotice = inject(UndoNotice);
  private readonly removals = new UndoQueue();

  private readonly pinned = signal<QuickSaleItem[] | null>(null);
  private readonly products = signal<ReadonlyMap<string, MenuProduct>>(new Map());
  private readonly shiftState = signal<'unknown' | 'open' | 'none'>('unknown');
  private readonly loadFailed = signal(false);
  private readonly lines = signal<readonly CartLine[]>([]);
  private readonly lastSale = signal<Ticket | null>(null);
  private destroyed = false;

  readonly items = this.pinned.asReadonly();
  readonly failed = this.loadFailed.asReadonly();
  // A line waiting out its removal is already out of the cart, the total and the sale.
  readonly cart = computed(() => this.lines().filter((line) => !this.removals.has(line.key)));
  readonly sold = this.lastSale.asReadonly();
  readonly pending = new PendingIds();
  readonly hasShift = computed(() => this.shiftState() === 'open');
  readonly loaded = computed(() => this.pinned() !== null && this.shiftState() !== 'unknown');
  readonly estimatedTotal = computed<Money | null>(() => {
    const lines = this.cart();
    if (lines.length === 0) {
      return null;
    }
    let minor = 0;
    for (const line of lines) {
      const unit = safeMinorUnits(line.unitPrice);
      if (unit === null) {
        return null;
      }
      minor += unit * line.quantity;
    }
    return fromMinorUnits(minor, lines[0].unitPrice.currency);
  });

  constructor() {
    this.destroyRef.onDestroy(() => (this.destroyed = true));
  }

  load(): void {
    this.loadFailed.set(false);
    this.api
      .quickSaleItems()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (items) => this.pinned.set(items),
        error: (error: unknown) => {
          this.loadFailed.set(true);
          this.notifier.show(error);
        },
      });
    this.api
      .menu()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (menu) => this.products.set(productsOf(menu)),
        error: (error: unknown) => {
          this.loadFailed.set(true);
          this.notifier.show(error);
        },
      });
    this.loadShift();
  }

  productOf(item: QuickSaleItem): MenuProduct | undefined {
    return this.products().get(item.productId);
  }

  isOnMenu(item: QuickSaleItem): boolean {
    return this.productOf(item) !== undefined;
  }

  // A pinned product the menu does not list cannot be sold, whatever the pin says.
  isAvailable(item: QuickSaleItem): boolean {
    return this.productOf(item)?.available ?? false;
  }

  needsOptions(item: QuickSaleItem): boolean {
    return this.productOf(item)?.modifierGroups.some((group) => group.required) ?? false;
  }

  addPlain(item: QuickSaleItem): void {
    this.add(item, { productId: item.productId, quantity: 1, modifierOptionIds: [], note: null, allergy: false });
  }

  addConfigured(item: QuickSaleItem, line: LineRequest): void {
    this.add(item, line);
  }

  changeQuantity(key: string, delta: number): void {
    this.lines.update((lines) =>
      lines.map((line) =>
        line.key === key
          ? { ...line, quantity: Math.min(MAX_QUANTITY, Math.max(1, line.quantity + delta)) }
          : line,
      ),
    );
  }

  remove(line: CartLine): void {
    this.undoNotice.schedule(
      this.removals,
      line.key,
      $localize`:@@staff.quickSale.removed:Se quitó "${line.name}:name:" de la venta`,
      () => this.lines.update((lines) => lines.filter((candidate) => candidate.key !== line.key)),
    );
  }

  sell(input: SellInput): void {
    const currency = this.estimatedTotal()?.currency;
    const lines = this.cart();
    if (lines.length === 0 || currency === undefined) {
      return;
    }
    const name = input.customerName.trim();
    const request = {
      customerName: name === '' ? null : name,
      lines: lines.map(
        (line): LineRequest => ({
          productId: line.productId,
          quantity: line.quantity,
          modifierOptionIds: [...line.modifierOptionIds],
          note: line.note,
          allergy: line.allergy,
        }),
      ),
      payment: {
        method: input.method,
        receivedAmount: input.received === null ? null : { amount: input.received, currency },
      },
    };
    const intent = `sale:${JSON.stringify(request)}`;
    this.pending.add('sell');
    this.api
      .quickSale(request, this.keys.keyFor(intent))
      .subscribe({
        next: (ticket) => {
          this.pending.delete('sell');
          this.keys.release(intent);
          if (this.destroyed) {
            this.messages.add({
              severity: 'success',
              summary: $localize`:@@staff.quickSale.registered:Se registró la venta del ticket ${ticket.number}:number:.`,
            });
            return;
          }
          this.lastSale.set(ticket);
          this.clearCart();
        },
        error: (error: unknown) => {
          this.pending.delete('sell');
          this.notifier.show(error);
          if (!this.destroyed && isShiftStateError(error)) {
            this.loadShift();
          }
        },
      });
  }

  newSale(): void {
    this.lastSale.set(null);
    this.clearCart();
  }

  loadShift(): void {
    this.api
      .currentShift()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (shift) => this.shiftState.set(shift === null ? 'none' : 'open'),
        error: (error: unknown) => {
          this.loadFailed.set(true);
          this.notifier.show(error);
        },
      });
  }

  private clearCart(): void {
    this.removals.flush();
    this.lines.set([]);
  }

  private add(item: QuickSaleItem, line: LineRequest): void {
    const options = [...(line.modifierOptionIds ?? [])].sort();
    const note = line.note ?? null;
    const key = [item.productId, options.join(','), note ?? '', line.allergy].join('|');
    // Adding what is being removed starts that line again instead of reviving the old quantity.
    if (this.removals.cancel(key)) {
      this.lines.update((lines) => lines.filter((candidate) => candidate.key !== key));
    }
    const product = this.productOf(item);
    const unitPrice = product ? unitPriceOf(product, options) : item.price;
    const names =
      product?.modifierGroups
        .flatMap((group) => group.options)
        .filter((option) => options.includes(option.id))
        .map((option) => option.name)
        .join(', ') ?? '';
    this.lines.update((lines) => {
      const existing = lines.find((candidate) => candidate.key === key);
      if (existing) {
        return lines.map((candidate) =>
          candidate.key === key
            ? { ...candidate, quantity: Math.min(MAX_QUANTITY, candidate.quantity + line.quantity) }
            : candidate,
        );
      }
      return [
        ...lines,
        {
          key,
          productId: item.productId,
          name: item.name,
          quantity: line.quantity,
          unitPrice,
          modifierOptionIds: options,
          modifierNames: names,
          note,
          allergy: line.allergy,
        },
      ];
    });
  }
}

function productsOf(menu: Menu): ReadonlyMap<string, MenuProduct> {
  return new Map(
    menu.categories.flatMap((category) => category.products).map((product) => [product.id, product]),
  );
}
