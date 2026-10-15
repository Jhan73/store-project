import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { computed } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  IconArrowLeft,
  IconArrowsExchange,
  IconBan,
  IconCash,
  IconCheck,
  IconMinus,
  IconPlus,
  IconReceipt,
  IconRefresh,
  IconRepeat,
  IconSend,
  IconTrash,
  IconX,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Select } from 'primeng/select';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import type { LineRequest, MenuProduct, TicketLine } from '../../../core/api/api-types';
import { AuthStore } from '../../../core/auth/auth-store';
import { formatMoney } from '../../../core/money/money';
import { reasonLabel, requiresNote } from '../../../core/reasons/reason-rules';
import { ProductConfigurator } from '../../../shared/ui/product-configurator/product-configurator';
import { methodLabel } from '../register/payment-methods';
import { ShiftStore } from '../register/shift-store';
import { PaymentPanel } from './payment-panel';
import { MAX_LINE_QUANTITY, TicketEditorStore } from './ticket-editor-store';

@Component({
  selector: 'app-ticket-editor',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonDirective,
    Dialog,
    Select,
    Textarea,
    Tooltip,
    TablerIconComponent,
    ProductConfigurator,
    PaymentPanel,
  ],
  providers: [TicketEditorStore, ShiftStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './ticket-editor.scss',
  template: `
    <header class="head">
      <a
        pButton
        severity="secondary"
        routerLink="/staff/tables"
        pTooltip="Volver al salón"
        i18n-pTooltip="@@staff.tickets.back.tooltip"
        tooltipPosition="top"
      >
        <tabler-icon [icon]="icons.back" aria-hidden="true" />
        <span i18n="@@staff.tickets.back">Salón</span>
      </a>
      @if (store.ticket(); as ticket) {
        <h1 data-testid="ticket-title">
          @if (ticket.type === 'TABLE') {
            {{ ticket.tableName }}
          } @else {
            <ng-container i18n="@@staff.tickets.counterTitle">Mostrador: {{ ticket.counterName }}</ng-container>
          }
        </h1>
        <p class="muted" i18n="@@staff.tickets.number">Cuenta {{ ticket.number }}</p>
      }
    </header>

    @if (store.failed()) {
      <div class="offline" role="status" data-testid="offline-banner">
        <ng-container i18n="@@staff.tickets.failed"
          >No se pudo cargar la cuenta. Los cambios están deshabilitados.</ng-container
        >
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="retry"
          (click)="store.load()"
          pTooltip="Volver a cargar la cuenta"
          i18n-pTooltip="@@staff.tickets.retry.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.retry" aria-hidden="true" />
          <span i18n="@@staff.tickets.retry">Reintentar</span>
        </button>
      </div>
    } @else if (!store.connected()) {
      <div class="offline" role="status" data-testid="offline-banner">
        <ng-container i18n="@@staff.tickets.offline"
          >Sin conexión en tiempo real. Los cambios se habilitan al reconectar.</ng-container
        >
      </div>
    } @else if (!store.enabled()) {
      <div class="offline" role="status" data-testid="offline-banner">
        <ng-container i18n="@@staff.tickets.updating"
          >Actualizando la cuenta. Los cambios se habilitan en unos segundos.</ng-container
        >
      </div>
    }

    @if (store.missing()) {
      <p data-testid="missing-notice" i18n="@@staff.tickets.missing">La cuenta no existe.</p>
    } @else if (store.ticket(); as ticket) {
      @if (!store.isOpen()) {
        <p class="closed" data-testid="closed-notice" i18n="@@staff.tickets.closed">
          La cuenta está cerrada y ya no se puede modificar.
        </p>
      }

      <div class="layout">
        @if (store.isOpen()) {
          <section class="menu" [attr.aria-labelledby]="'menu-heading'">
            <h2 id="menu-heading" i18n="@@staff.tickets.menu">Productos</h2>
            @for (category of store.menu()?.categories ?? []; track category.id) {
              <h3>{{ category.name }}</h3>
              <ul class="products">
                @for (product of category.products; track product.id) {
                  <li>
                    <button
                      pButton
                      type="button"
                      severity="secondary"
                      class="product"
                      [attr.data-testid]="'product-' + product.id"
                      [disabled]="!product.available || !store.enabled()"
                      pTooltip="Elegir las opciones de este producto"
                      i18n-pTooltip="@@staff.tickets.product.tooltip"
                      tooltipPosition="top"
                      (click)="configuring.set(product)"
                    >
                      <tabler-icon [icon]="icons.add" aria-hidden="true" />
                      <span class="body">
                        <span class="name">{{ product.name }}</span>
                        @if (product.available) {
                          <span class="price">{{ money(product.price) }}</span>
                        } @else {
                          <span class="price" i18n="@@staff.tickets.soldOut">Agotado</span>
                        }
                      </span>
                    </button>
                  </li>
                }
              </ul>
            }
          </section>
        }

        <section class="ticket" [attr.aria-labelledby]="'ticket-heading'">
          <h2 id="ticket-heading" i18n="@@staff.tickets.lines">Cuenta</h2>
          <ul class="lines">
            @for (line of ticket.lines; track line.id) {
              <li [attr.data-testid]="'line-' + line.id" [class.voided]="line.state === 'VOIDED'">
                <div class="line-main">
                  <span class="quantity">{{ line.quantity }} ×</span>
                  <span class="name">{{ line.productName }}</span>
                  <span class="amount">{{ money(line.lineTotal) }}</span>
                </div>
                @if (line.modifiers.length > 0) {
                  <p class="detail">{{ modifiersOf(line) }}</p>
                }
                @if (line.note) {
                  <p class="detail">{{ line.note }}</p>
                }
                @if (line.allergy) {
                  <p class="detail warn" i18n="@@staff.tickets.allergy">Alergia</p>
                }
                <div class="line-foot">
                  <span class="state" [attr.data-testid]="'line-status-' + line.id">
                    @switch (store.stateOf(line)) {
                      @case ('UNSENT') {
                        <ng-container i18n="@@staff.tickets.state.unsent">Sin enviar</ng-container>
                      }
                      @case ('SENT') {
                        <ng-container i18n="@@staff.tickets.state.sent">Enviada</ng-container>
                      }
                      @case ('READY') {
                        <ng-container i18n="@@staff.tickets.state.ready">Lista</ng-container>
                      }
                      @case ('VOIDED') {
                        <ng-container i18n="@@staff.tickets.state.voided">Anulada</ng-container>
                        @if (line.voidReasonCode) {
                          <span class="reason">({{ label(line.voidReasonCode) }})</span>
                        }
                      }
                    }
                  </span>
                  @if (store.isOpen() && store.mayVoid(line)) {
                    <button
                      pButton
                      type="button"
                      severity="danger"
                      [attr.data-testid]="'void-' + line.id"
                      [disabled]="!store.enabled() || store.pending.has('void:' + line.id)"
                      pTooltip="Anular esta línea con un motivo"
                      i18n-pTooltip="@@staff.tickets.void.tooltip"
                      tooltipPosition="top"
                      (click)="startVoid(line)"
                    >
                      <tabler-icon [icon]="icons.void" aria-hidden="true" />
                      <span i18n="@@staff.tickets.void">Anular</span>
                    </button>
                  } @else if (store.isOpen() && store.mustAskCashier(line)) {
                    <span class="detail warn" [attr.data-testid]="'void-blocked-' + line.id" i18n="@@staff.tickets.void.blocked"
                      >Ya está lista: pide a un cajero que la anule.</span
                    >
                  }
                  @if (store.isOpen() && line.state === 'UNSENT') {
                    <span class="line-actions">
                      <button
                        pButton
                        type="button"
                        severity="secondary"
                        [attr.data-testid]="'quantity-down-' + line.id"
                        [disabled]="!store.enabled() || store.pending.has('line:' + line.id) || line.quantity <= 1"
                        aria-label="Una unidad menos"
                        i18n-aria-label="@@staff.tickets.quantity.downLabel"
                        pTooltip="Una unidad menos"
                        i18n-pTooltip="@@staff.tickets.quantity.down"
                        tooltipPosition="top"
                        (click)="store.changeQuantity(line, line.quantity - 1)"
                      >
                        <tabler-icon [icon]="icons.minus" aria-hidden="true" />
                      </button>
                      <button
                        pButton
                        type="button"
                        severity="secondary"
                        [attr.data-testid]="'quantity-up-' + line.id"
                        [disabled]="!store.enabled() || store.pending.has('line:' + line.id) || line.quantity >= maxQuantity"
                        aria-label="Una unidad más"
                        i18n-aria-label="@@staff.tickets.quantity.upLabel"
                        pTooltip="Una unidad más"
                        i18n-pTooltip="@@staff.tickets.quantity.up"
                        tooltipPosition="top"
                        (click)="store.changeQuantity(line, line.quantity + 1)"
                      >
                        <tabler-icon [icon]="icons.add" aria-hidden="true" />
                      </button>
                      <button
                        pButton
                        type="button"
                        severity="danger"
                        [attr.data-testid]="'remove-' + line.id"
                        [disabled]="!store.enabled() || store.pending.has('line:' + line.id)"
                        aria-label="Quitar la línea"
                        i18n-aria-label="@@staff.tickets.remove.label"
                        pTooltip="Quitar la línea de la cuenta"
                        i18n-pTooltip="@@staff.tickets.remove.tooltip"
                        tooltipPosition="top"
                        (click)="store.removeLine(line.id)"
                      >
                        <tabler-icon [icon]="icons.remove" aria-hidden="true" />
                      </button>
                    </span>
                  }
                </div>
              </li>
            } @empty {
              <li class="muted" i18n="@@staff.tickets.empty">La cuenta todavía no tiene líneas.</li>
            }
          </ul>

          <p class="total">
            <span i18n="@@staff.tickets.total">Total</span>
            <strong data-testid="ticket-total">{{ money(ticket.total) }}</strong>
          </p>

          @if (ticket.payments.length > 0) {
            <p class="total">
              <span i18n="@@staff.tickets.paid">Pagado</span>
              <strong data-testid="ticket-paid">{{ money(ticket.paid) }}</strong>
            </p>
            <p class="total">
              <span i18n="@@staff.tickets.balance">Falta cobrar</span>
              <strong data-testid="ticket-balance">{{ money(ticket.balance) }}</strong>
            </p>
            <ul class="payments">
              @for (payment of ticket.payments; track payment.id) {
                <li [attr.data-testid]="'payment-' + payment.id" [class.voided]="payment.voided">
                  <span class="name">{{ method(payment.method) }}</span>
                  <span>{{ money(payment.paid) }}</span>
                  @if (payment.received && payment.change) {
                    <span class="detail">
                      <ng-container i18n="@@staff.tickets.payment.received">Recibido</ng-container>
                      {{ money(payment.received) }} ·
                      <ng-container i18n="@@staff.tickets.payment.change">Vuelto</ng-container>
                      {{ money(payment.change) }}
                    </span>
                  }
                  @if (payment.voided) {
                    <span class="detail" i18n="@@staff.tickets.payment.voided">Anulado</span>
                  }
                </li>
              }
            </ul>
          }

          @if (store.isOpen()) {
            <div class="actions">
              <button
                pButton
                type="button"
                data-testid="send"
                [disabled]="!store.enabled() || store.pending.has('send') || store.unsent().length === 0"
                pTooltip="Enviar las líneas nuevas a cocina"
                i18n-pTooltip="@@staff.tickets.send.tooltip"
                tooltipPosition="top"
                (click)="store.send()"
              >
                <tabler-icon [icon]="icons.send" aria-hidden="true" />
                <span i18n="@@staff.tickets.send">Enviar a cocina</span>
              </button>
              <button
                pButton
                type="button"
                severity="secondary"
                data-testid="repeat"
                [disabled]="!store.enabled() || store.pending.has('repeat') || store.lastRound().length === 0"
                pTooltip="Agregar de nuevo las líneas de la última ronda enviada"
                i18n-pTooltip="@@staff.tickets.repeat.tooltip"
                tooltipPosition="top"
                (click)="store.repeatLastRound()"
              >
                <tabler-icon [icon]="icons.repeat" aria-hidden="true" />
                <span i18n="@@staff.tickets.repeat">Repetir última ronda</span>
              </button>
              @if (store.isTable()) {
                <button
                  pButton
                  type="button"
                  severity="secondary"
                  data-testid="bill"
                  [disabled]="!store.enabled() || store.pending.has('bill')"
                  [pTooltip]="ticket.billRequested ? tips.billOff : tips.billOn"
                  tooltipPosition="top"
                  (click)="store.setBillRequested(!ticket.billRequested)"
                >
                  <tabler-icon [icon]="icons.bill" aria-hidden="true" />
                  @if (ticket.billRequested) {
                    <span i18n="@@staff.tickets.billOff">Quitar el pedido de cuenta</span>
                  } @else {
                    <span i18n="@@staff.tickets.billOn">Pedir la cuenta</span>
                  }
                </button>
                <button
                  pButton
                  type="button"
                  severity="secondary"
                  data-testid="transfer-open"
                  [disabled]="!store.enabled()"
                  pTooltip="Mover la cuenta a una mesa libre"
                  i18n-pTooltip="@@staff.tickets.transfer.tooltip"
                  tooltipPosition="top"
                  (click)="startTransfer()"
                >
                  <tabler-icon [icon]="icons.transfer" aria-hidden="true" />
                  <span i18n="@@staff.tickets.transfer">Cambiar de mesa</span>
                </button>
              }
              @if (canPay()) {
                <button
                  pButton
                  type="button"
                  data-testid="pay-open"
                  [attr.aria-expanded]="paying()"
                  pTooltip="Mostrar u ocultar el cobro de esta cuenta"
                  i18n-pTooltip="@@staff.tickets.pay.open.tooltip"
                  tooltipPosition="top"
                  (click)="paying.set(!paying())"
                >
                  <tabler-icon [icon]="icons.pay" aria-hidden="true" />
                  <span i18n="@@staff.tickets.pay.open">Cobrar</span>
                </button>
              }
            </div>
            @if (paying() && canPay()) {
              <app-payment-panel [ticket]="ticket" />
            }
          }
        </section>
      </div>
    }

    <p-dialog
      header="Agregar producto"
      i18n-header="@@staff.tickets.add.title"
      [modal]="true"
      [draggable]="false"
      [style]="{ width: 'min(40rem, calc(100vw - 1rem))' }"
      [visible]="configuring() !== null"
      (visibleChange)="closeAdd($event)"
    >
      @if (configuring(); as product) {
        <app-product-configurator
          [product]="product"
          [busy]="store.pending.has('add')"
          (confirmed)="add($event)"
          (cancelled)="configuring.set(null)"
        />
      }
    </p-dialog>

    <p-dialog
      header="Cambiar de mesa"
      i18n-header="@@staff.tickets.transfer.title"
      [modal]="true"
      [draggable]="false"
      [style]="{ width: 'min(30rem, calc(100vw - 1rem))' }"
      [visible]="transferring()"
      (visibleChange)="closeTransfer($event)"
    >
      <div class="field">
        <label for="transfer-table" id="transfer-table-label" i18n="@@staff.tickets.transfer.table"
          >Mesa de destino</label
        >
        <p-select
          inputId="transfer-table"
          [ariaLabelledBy]="'transfer-table-label'"
          [formControl]="target"
          optionLabel="name"
          optionValue="id"
          [options]="store.freeTables()"
          [filter]="true"
          filterBy="name"
          [resetFilterOnHide]="true"
          filterPlaceholder="Buscar"
          i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
          placeholder="Elige una mesa libre"
          i18n-placeholder="@@staff.tickets.transfer.placeholder"
          [appendTo]="'body'"
          fluid
        />
      </div>
      <ng-template #footer>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="transfer-cancel"
          pTooltip="Cerrar sin mover la cuenta"
          i18n-pTooltip="@@staff.tickets.transfer.cancel.tooltip"
          tooltipPosition="top"
          (click)="closeTransfer(false)"
        >
          <tabler-icon [icon]="icons.cancel" aria-hidden="true" />
          <span i18n="@@staff.tickets.transfer.cancel">Cancelar</span>
        </button>
        <button
          pButton
          type="button"
          data-testid="transfer-confirm"
          [disabled]="!chosenTable() || store.pending.has('transfer')"
          pTooltip="Mover la cuenta a la mesa elegida"
          i18n-pTooltip="@@staff.tickets.transfer.confirm.tooltip"
          tooltipPosition="top"
          (click)="transfer()"
        >
          <tabler-icon [icon]="icons.confirm" aria-hidden="true" />
          <span i18n="@@staff.tickets.transfer.confirm">Mover</span>
        </button>
      </ng-template>
    </p-dialog>

    <p-dialog
      header="Anular línea"
      i18n-header="@@staff.tickets.void.title"
      [modal]="true"
      [draggable]="false"
      [style]="{ width: 'min(30rem, calc(100vw - 1rem))' }"
      [visible]="voiding() !== null"
      (visibleChange)="closeVoid($event)"
    >
      @if (voiding(); as line) {
        <p class="detail">{{ line.quantity }} × {{ line.productName }}</p>
        @if (store.reasonsLoadFailed()) {
          <div data-testid="void-reasons-failed" role="status">
            <p i18n="@@staff.tickets.void.failed">No se pudieron cargar los motivos.</p>
            <button
              pButton
              type="button"
              severity="secondary"
              data-testid="void-retry"
              pTooltip="Volver a cargar los motivos de anulación"
              i18n-pTooltip="@@staff.tickets.void.retry.tooltip"
              tooltipPosition="top"
              (click)="store.loadVoidReasons()"
            >
              <tabler-icon [icon]="icons.retry" aria-hidden="true" />
              <span i18n="@@staff.tickets.void.retry">Reintentar</span>
            </button>
          </div>
        } @else if (store.reasons(); as reasons) {
          @if (reasons.length === 0) {
            <p data-testid="void-no-reasons" i18n="@@staff.tickets.void.noReasons">
              No hay motivos de anulación configurados. Pide a un administrador que los agregue.
            </p>
          }
          <div class="field" data-testid="void-reason-box">
            <label for="void-reason" id="void-reason-label" i18n="@@staff.tickets.void.reason">Motivo</label>
            <p-select
              inputId="void-reason"
              [ariaLabelledBy]="'void-reason-label'"
              [formControl]="voidReason"
              optionLabel="label"
              optionValue="code"
              [options]="reasonOptions()"
              [filter]="true"
              filterBy="label"
              [resetFilterOnHide]="true"
              filterPlaceholder="Buscar"
              i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
              placeholder="Elige un motivo"
              i18n-placeholder="@@staff.tickets.void.reason.placeholder"
              [appendTo]="'body'"
              fluid
            />
          </div>
          <div class="field">
            <label for="void-note" i18n="@@staff.tickets.void.note">Nota</label>
            <textarea
              pTextarea
              id="void-note"
              data-testid="void-note"
              [formControl]="voidNote"
              maxlength="280"
              rows="3"
              fluid
            ></textarea>
            @if (noteRequired()) {
              <small i18n="@@staff.tickets.void.noteRequired">Para el motivo «Otro» la nota es obligatoria.</small>
            }
          </div>
        } @else {
          <p class="muted" i18n="@@staff.tickets.void.loading">Cargando los motivos…</p>
        }
      }
      <ng-template #footer>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="void-cancel"
          pTooltip="Cerrar sin anular la línea"
          i18n-pTooltip="@@staff.tickets.void.cancel.tooltip"
          tooltipPosition="top"
          (click)="closeVoid(false)"
        >
          <tabler-icon [icon]="icons.cancel" aria-hidden="true" />
          <span i18n="@@staff.tickets.void.cancel">Cancelar</span>
        </button>
        <button
          pButton
          type="button"
          severity="danger"
          data-testid="void-confirm"
          [disabled]="!canConfirmVoid()"
          pTooltip="Anular la línea con este motivo"
          i18n-pTooltip="@@staff.tickets.void.confirm.tooltip"
          tooltipPosition="top"
          (click)="confirmVoid()"
        >
          <tabler-icon [icon]="icons.void" aria-hidden="true" />
          <span i18n="@@staff.tickets.void.confirm">Anular línea</span>
        </button>
      </ng-template>
    </p-dialog>
  `,
})
export class TicketEditor {
  protected readonly store = inject(TicketEditorStore);

  protected readonly icons = {
    back: IconArrowLeft,
    add: IconPlus,
    minus: IconMinus,
    remove: IconTrash,
    send: IconSend,
    repeat: IconRepeat,
    bill: IconReceipt,
    transfer: IconArrowsExchange,
    void: IconBan,
    retry: IconRefresh,
    cancel: IconX,
    confirm: IconCheck,
    pay: IconCash,
  };
  protected readonly tips = {
    billOn: $localize`:@@staff.tickets.billOn.tooltip:Avisar que la mesa pidió la cuenta`,
    billOff: $localize`:@@staff.tickets.billOff.tooltip:Quitar el aviso de que la mesa pidió la cuenta`,
  };
  protected readonly maxQuantity = MAX_LINE_QUANTITY;
  protected readonly money = formatMoney;
  protected readonly method = methodLabel;
  private readonly auth = inject(AuthStore);
  protected readonly paying = signal(false);
  protected readonly canPay = computed(() => this.auth.role() === 'CASHIER' || this.auth.role() === 'ADMIN');
  protected readonly configuring = signal<MenuProduct | null>(null);
  protected readonly transferring = signal(false);
  protected readonly target = new FormControl<string | null>(null);
  protected readonly label = reasonLabel;
  protected readonly reasonOptions = computed(() =>
    (this.store.reasons() ?? []).map((reason) => ({ code: reason.code, label: reasonLabel(reason.code) })),
  );
  protected readonly voiding = signal<TicketLine | null>(null);
  protected readonly voidReason = new FormControl<string | null>(null);
  protected readonly voidNote = new FormControl('', { nonNullable: true });
  private readonly chosenReason = toSignal(this.voidReason.valueChanges, { initialValue: null });
  private readonly typedNote = toSignal(this.voidNote.valueChanges, { initialValue: '' });
  protected readonly noteRequired = computed(() => requiresNote(this.chosenReason()));
  protected readonly canConfirmVoid = computed(
    () =>
      this.chosenReason() !== null &&
      (!this.noteRequired() || this.typedNote().trim() !== '') &&
      !this.store.pending.has(`void:${this.voiding()?.id}`),
  );
  protected readonly chosenTable = toSignal(this.target.valueChanges, { initialValue: null });

  protected modifiersOf(line: TicketLine): string {
    return line.modifiers.map((modifier) => modifier.name).join(', ');
  }

  protected add(line: LineRequest): void {
    this.store.addLine(line, () => this.configuring.set(null));
  }

  protected closeAdd(visible: boolean): void {
    if (!visible) {
      this.configuring.set(null);
    }
  }

  protected startVoid(line: TicketLine): void {
    this.voidReason.reset();
    this.voidNote.reset();
    this.store.loadVoidReasons();
    this.voiding.set(line);
  }

  protected closeVoid(visible: boolean): void {
    if (!visible) {
      this.voiding.set(null);
    }
  }

  protected confirmVoid(): void {
    const line = this.voiding();
    const reason = this.voidReason.value;
    if (line && reason !== null && this.canConfirmVoid()) {
      const note = this.voidNote.value.trim();
      this.store.voidLine(line, { reason, note: note === '' ? null : note }, () => this.voiding.set(null));
    }
  }

  protected startTransfer(): void {
    this.target.reset();
    this.store.loadTables();
    this.transferring.set(true);
  }

  protected closeTransfer(visible: boolean): void {
    if (!visible) {
      this.transferring.set(false);
    }
  }

  protected transfer(): void {
    const tableId = this.target.value;
    if (tableId) {
      this.store.transfer(tableId, () => this.transferring.set(false));
    }
  }
}
