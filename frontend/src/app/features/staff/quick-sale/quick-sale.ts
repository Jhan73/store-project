import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  IconCash,
  IconLockOpen,
  IconMinus,
  IconPlus,
  IconRefresh,
  IconShoppingCartPlus,
  IconTrash,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Tooltip } from 'primeng/tooltip';
import type { LineRequest, MenuProduct, PaymentMethod, QuickSaleItem } from '../../../core/api/api-types';
import { formatMoney, parseAmount, toMinorUnits } from '../../../core/money/money';
import { ProductConfigurator } from '../../../shared/ui/product-configurator/product-configurator';
import { methodLabel, PAYMENT_METHODS } from '../register/payment-methods';
import { QuickSaleStore } from './quick-sale-store';

interface Configuring {
  readonly item: QuickSaleItem;
  readonly product: MenuProduct;
}

@Component({
  selector: 'app-quick-sale',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonDirective,
    Dialog,
    InputText,
    Select,
    Tooltip,
    TablerIconComponent,
    ProductConfigurator,
  ],
  providers: [QuickSaleStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './quick-sale.scss',
  template: `
    <h1 i18n="@@staff.quickSale.title">Venta rápida</h1>

    @if (store.failed()) {
      <div class="notice" role="status" data-testid="load-failed">
        <ng-container i18n="@@staff.quickSale.failed">No se pudo cargar la venta rápida.</ng-container>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="retry"
          pTooltip="Volver a cargar los productos y el turno"
          i18n-pTooltip="@@staff.quickSale.retry.tooltip"
          tooltipPosition="top"
          (click)="store.load()"
        >
          <tabler-icon [icon]="icons.retry" aria-hidden="true" />
          <span i18n="@@staff.quickSale.retry">Reintentar</span>
        </button>
      </div>
    }

    @if (store.loaded() && !store.hasShift() && store.sold() === null) {
      <div class="notice" role="status" data-testid="no-shift">
        <ng-container i18n="@@staff.quickSale.noShift">Necesitas un turno de caja abierto para vender.</ng-container>
        <a
          pButton
          severity="secondary"
          routerLink="/staff/register"
          pTooltip="Ir a Caja para abrir un turno"
          i18n-pTooltip="@@staff.quickSale.openShift.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.openShift" aria-hidden="true" />
          <span i18n="@@staff.quickSale.openShift">Abrir turno</span>
        </a>
      </div>
    }

    @if (store.sold(); as sold) {
      <section class="result" data-testid="sale-result" [attr.aria-labelledby]="'sale-heading'">
        <h2 id="sale-heading" i18n="@@staff.quickSale.done">Venta registrada</h2>
        <p class="number" data-testid="sale-number">
          <ng-container i18n="@@staff.quickSale.number">Número</ng-container>
          <strong>{{ sold.number }}</strong>
        </p>
        <p>
          <ng-container i18n="@@staff.quickSale.total">Total</ng-container>
          <strong data-testid="sale-total">{{ money(sold.total) }}</strong>
        </p>
        @if (changeOf(sold); as change) {
          <p>
            <ng-container i18n="@@staff.quickSale.change">Vuelto</ng-container>
            <strong data-testid="sale-change">{{ money(change) }}</strong>
          </p>
        }
        <button
          pButton
          type="button"
          data-testid="new-sale"
          pTooltip="Empezar otra venta rápida"
          i18n-pTooltip="@@staff.quickSale.new.tooltip"
          tooltipPosition="top"
          (click)="newSale()"
        >
          <tabler-icon [icon]="icons.add" aria-hidden="true" />
          <span i18n="@@staff.quickSale.new">Nueva venta</span>
        </button>
      </section>
    } @else if (store.items(); as pinned) {
      <div class="layout">
        <section [attr.aria-labelledby]="'items-heading'">
          <h2 id="items-heading" i18n="@@staff.quickSale.items">Productos</h2>
          @if (pinned.length === 0) {
            <p class="muted" data-testid="no-items" i18n="@@staff.quickSale.noItems">
              No hay productos fijados para la venta rápida.
            </p>
          }
          <ul class="items">
            @for (item of pinned; track item.productId) {
              <li>
                <button
                  pButton
                  type="button"
                  severity="secondary"
                  class="item"
                  [attr.data-testid]="'item-' + item.productId"
                  [disabled]="!store.isAvailable(item)"
                  pTooltip="Agregar este producto a la venta"
                  i18n-pTooltip="@@staff.quickSale.item.tooltip"
                  tooltipPosition="top"
                  (click)="choose(item)"
                >
                  <tabler-icon [icon]="icons.item" aria-hidden="true" />
                  <span class="body">
                    <span class="name">{{ item.name }}</span>
                    @if (store.isAvailable(item)) {
                      <span class="price">{{ money(item.price) }}</span>
                    } @else {
                      <span class="price" i18n="@@staff.quickSale.soldOut">Agotado</span>
                    }
                  </span>
                </button>
              </li>
            }
          </ul>
        </section>

        <section [attr.aria-labelledby]="'cart-heading'">
          <h2 id="cart-heading" i18n="@@staff.quickSale.cart">Venta</h2>
          <ul class="cart">
            @for (line of store.cart(); track line.key; let index = $index) {
              <li [attr.data-testid]="'cart-line-' + index">
                <div class="line-main">
                  <span class="name">{{ line.quantity }} × {{ line.name }}</span>
                  <span>{{ money(line.unitPrice) }}</span>
                </div>
                @if (line.modifierNames) {
                  <p class="detail">{{ line.modifierNames }}</p>
                }
                @if (line.note) {
                  <p class="detail">{{ line.note }}</p>
                }
                <div class="line-actions">
                  <button
                    pButton
                    type="button"
                    severity="secondary"
                    [attr.data-testid]="'qty-down-' + index"
                    [disabled]="line.quantity <= 1"
                    aria-label="Una unidad menos"
                    i18n-aria-label="@@staff.quickSale.quantity.downLabel"
                    pTooltip="Una unidad menos"
                    i18n-pTooltip="@@staff.quickSale.quantity.down"
                    tooltipPosition="top"
                    (click)="store.changeQuantity(line.key, -1)"
                  >
                    <tabler-icon [icon]="icons.minus" aria-hidden="true" />
                  </button>
                  <button
                    pButton
                    type="button"
                    severity="secondary"
                    [attr.data-testid]="'qty-up-' + index"
                    [disabled]="line.quantity >= maxQuantity"
                    aria-label="Una unidad más"
                    i18n-aria-label="@@staff.quickSale.quantity.upLabel"
                    pTooltip="Una unidad más"
                    i18n-pTooltip="@@staff.quickSale.quantity.up"
                    tooltipPosition="top"
                    (click)="store.changeQuantity(line.key, 1)"
                  >
                    <tabler-icon [icon]="icons.add" aria-hidden="true" />
                  </button>
                  <button
                    pButton
                    type="button"
                    severity="danger"
                    [attr.data-testid]="'remove-' + index"
                    aria-label="Quitar el producto"
                    i18n-aria-label="@@staff.quickSale.remove.label"
                    pTooltip="Quitar el producto de la venta"
                    i18n-pTooltip="@@staff.quickSale.remove.tooltip"
                    tooltipPosition="top"
                    (click)="store.remove(line.key)"
                  >
                    <tabler-icon [icon]="icons.remove" aria-hidden="true" />
                  </button>
                </div>
              </li>
            } @empty {
              <li class="muted" i18n="@@staff.quickSale.cart.empty">Elige productos para empezar la venta.</li>
            }
          </ul>
          @if (store.estimatedTotal(); as total) {
            <p class="total">
              <span i18n="@@staff.quickSale.estimated">Total estimado</span>
              <strong data-testid="cart-total">{{ money(total) }}</strong>
            </p>
          }

          <form (submit)="submit($event)" [attr.aria-labelledby]="'cart-heading'">
            <div class="field">
              <label for="customer-name" i18n="@@staff.quickSale.name">Nombre del cliente (opcional)</label>
              <input
                pInputText
                id="customer-name"
                data-testid="customer-name"
                [formControl]="customerName"
                maxlength="60"
                autocomplete="off"
                fluid
              />
            </div>
            <div class="field">
              <label for="qs-method" id="qs-method-label" i18n="@@staff.quickSale.method">Método de pago</label>
              <p-select
                inputId="qs-method"
                [ariaLabelledBy]="'qs-method-label'"
                [formControl]="method"
                optionLabel="label"
                optionValue="value"
                [options]="methods"
                [filter]="true"
                filterBy="label"
                [resetFilterOnHide]="true"
                filterPlaceholder="Buscar"
                i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
                [appendTo]="'body'"
                fluid
              />
            </div>
            @if (chosenMethod() === 'CASH') {
              <div class="field">
                <label for="qs-received" i18n="@@staff.quickSale.received">Efectivo recibido</label>
                <input
                  pInputText
                  id="qs-received"
                  data-testid="qs-received"
                  [formControl]="received"
                  [attr.inputmode]="'decimal'"
                  autocomplete="off"
                  fluid
                />
              </div>
            }
            <button
              pButton
              type="submit"
              data-testid="qs-submit"
              [loading]="selling()"
              [disabled]="!canSell() || selling()"
              pTooltip="Cobrar y registrar la venta"
              i18n-pTooltip="@@staff.quickSale.submit.tooltip"
              tooltipPosition="top"
            >
              @if (!selling()) {
                <tabler-icon [icon]="icons.pay" aria-hidden="true" />
              }
              <span i18n="@@staff.quickSale.submit">Cobrar</span>
            </button>
          </form>
        </section>
      </div>
    } @else if (!store.failed()) {
      <p class="muted" i18n="@@staff.quickSale.loading">Cargando…</p>
    }

    <p-dialog
      header="Elegir las opciones"
      i18n-header="@@staff.quickSale.configure.title"
      [modal]="true"
      [draggable]="false"
      [style]="{ width: 'min(40rem, calc(100vw - 1rem))' }"
      [visible]="configuring() !== null"
      (visibleChange)="closeConfigurator($event)"
    >
      @if (configuring(); as current) {
        <app-product-configurator
          [product]="current.product"
          (confirmed)="configured($event)"
          (cancelled)="configuring.set(null)"
        />
      }
    </p-dialog>
  `,
})
export class QuickSale {
  protected readonly store = inject(QuickSaleStore);

  protected readonly icons = {
    add: IconPlus,
    minus: IconMinus,
    remove: IconTrash,
    item: IconShoppingCartPlus,
    pay: IconCash,
    retry: IconRefresh,
    openShift: IconLockOpen,
  };
  protected readonly money = formatMoney;
  protected readonly maxQuantity = 99;
  protected readonly methods = PAYMENT_METHODS.map((value) => ({ value, label: methodLabel(value) }));
  protected readonly customerName = new FormControl('', { nonNullable: true });
  protected readonly method = new FormControl<PaymentMethod>('CASH', { nonNullable: true });
  protected readonly received = new FormControl('', { nonNullable: true });
  protected readonly configuring = signal<Configuring | null>(null);

  protected readonly chosenMethod = toSignal(this.method.valueChanges, { initialValue: 'CASH' as PaymentMethod });
  private readonly typedReceived = toSignal(this.received.valueChanges, { initialValue: '' });
  protected readonly selling = computed(() => this.store.pending.has('sell'));
  protected readonly canSell = computed(() => {
    const total = this.store.estimatedTotal();
    if (total === null || !this.store.hasShift()) {
      return false;
    }
    if (this.chosenMethod() !== 'CASH') {
      return true;
    }
    const received = parseAmount(this.typedReceived());
    return received !== null && toMinorUnits({ amount: received, currency: total.currency }) >= toMinorUnits(total);
  });

  constructor() {
    this.store.load();
  }

  protected changeOf(sold: { payments: { voided: boolean; change: { amount: string; currency: string } | null }[] }) {
    return sold.payments.find((payment) => !payment.voided)?.change ?? null;
  }

  protected choose(item: QuickSaleItem): void {
    const product = this.store.productOf(item);
    if (product && this.store.needsOptions(item)) {
      this.configuring.set({ item, product });
    } else {
      this.store.addPlain(item);
    }
  }

  protected configured(line: LineRequest): void {
    const current = this.configuring();
    if (current) {
      this.store.addConfigured(current.item, line);
      this.configuring.set(null);
    }
  }

  protected closeConfigurator(visible: boolean): void {
    if (!visible) {
      this.configuring.set(null);
    }
  }

  protected submit(event: Event): void {
    event.preventDefault();
    if (!this.canSell() || this.selling()) {
      return;
    }
    this.store.sell({
      customerName: this.customerName.value,
      method: this.method.value,
      received: this.method.value === 'CASH' ? parseAmount(this.received.value) : null,
    });
  }

  protected newSale(): void {
    this.store.newSale();
    this.customerName.reset();
    this.received.reset();
  }
}
