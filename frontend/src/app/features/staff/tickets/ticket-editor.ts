import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { computed, effect, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  IconArrowLeft,
  IconArrowsExchange,
  IconBan,
  IconCircleOff,
  IconDeviceFloppy,
  IconCash,
  IconCheck,
  IconGift,
  IconGitMerge,
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
import { formatMoneyOrDash } from '../../../core/money/money';
import { reasonLabel } from '../../../core/reasons/reason-rules';
import { FormDialog } from '../../../shared/ui/form-dialog/form-dialog';
import { FormField } from '../../../shared/ui/form-field/form-field';
import { ProductConfigurator } from '../../../shared/ui/product-configurator/product-configurator';
import { VoidDialog, type VoidChoice } from '../../../shared/ui/void-dialog/void-dialog';
import { methodLabel } from '../register/payment-methods';
import { ShiftStore } from '../register/shift-store';
import { PaymentPanel } from './payment-panel';
import { MAX_LINE_QUANTITY, TicketEditorStore } from './ticket-editor-store';

type RegisterVoid = { readonly kind: 'payment'; readonly id: string } | { readonly kind: 'sale' };

@Component({
  selector: 'app-ticket-editor',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonDirective,
    Dialog,
    Select,
    Textarea,
    FormDialog,
    FormField,
    Tooltip,
    TablerIconComponent,
    ProductConfigurator,
    PaymentPanel,
    VoidDialog,
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
            <ng-container i18n="@@staff.tickets.counterTitle"
              >Mostrador: {{ ticket.counterName }}</ng-container
            >
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
      @if (ticket.status === 'MERGED') {
        <p class="closed" data-testid="merged-notice" i18n="@@staff.tickets.merged">
          La cuenta se unió a otra y ya no se puede modificar.
        </p>
      } @else if (ticket.status === 'CANCELLED') {
        <p class="closed" data-testid="cancelled-notice" i18n="@@staff.tickets.cancelled">
          La cuenta fue cancelada y ya no se puede modificar.
        </p>
      } @else if (!store.isOpen()) {
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
              <li
                [attr.data-testid]="'line-' + line.id"
                [class.voided]="line.state === 'VOIDED'"
                [class.comped]="line.state === 'COMPED'"
                [class.removing]="store.isRemoving(line.id)"
              >
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
                @if (store.isRemoving(line.id)) {
                  <p class="detail" role="status" data-testid="line-removing" i18n="@@staff.tickets.removing">
                    Se quitará de la cuenta; puedes deshacerlo.
                  </p>
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
                      @case ('COMPED') {
                        <ng-container i18n="@@staff.tickets.state.comped">Cortesía</ng-container>
                        @if (line.compReasonCode) {
                          <span class="reason">({{ label(line.compReasonCode) }})</span>
                        }
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
                    <span
                      class="detail warn"
                      [attr.data-testid]="'void-blocked-' + line.id"
                      i18n="@@staff.tickets.void.blocked"
                      >Ya está lista: pide a un cajero que la anule.</span
                    >
                  }
                  @if (store.isOpen() && store.mayComp(line)) {
                    <button
                      pButton
                      type="button"
                      severity="secondary"
                      [attr.data-testid]="'comp-' + line.id"
                      [disabled]="!store.enabled() || store.pending.has('comp:' + line.id)"
                      pTooltip="Dar esta línea de cortesía: no se cobra"
                      i18n-pTooltip="@@staff.tickets.comp.tooltip"
                      tooltipPosition="top"
                      (click)="startComp(line)"
                    >
                      <tabler-icon [icon]="icons.comp" aria-hidden="true" />
                      <span i18n="@@staff.tickets.comp">Cortesía</span>
                    </button>
                  } @else if (store.isOpen() && store.mustAskCashierToComp(line)) {
                    <span
                      class="detail warn"
                      [attr.data-testid]="'comp-blocked-' + line.id"
                      i18n="@@staff.tickets.comp.blocked"
                      >Para dar una cortesía, pide a un cajero.</span
                    >
                  }
                  @if (store.isOpen() && line.state === 'UNSENT') {
                    <span class="line-actions">
                      <button
                        pButton
                        type="button"
                        severity="secondary"
                        [attr.data-testid]="'quantity-down-' + line.id"
                        [disabled]="
                          !store.enabled() ||
                          store.pending.has('line:' + line.id) ||
                          store.isRemoving(line.id) ||
                          line.quantity <= 1
                        "
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
                        [disabled]="
                          !store.enabled() ||
                          store.pending.has('line:' + line.id) ||
                          store.isRemoving(line.id) ||
                          line.quantity >= maxQuantity
                        "
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
                        [disabled]="!store.enabled() || store.pending.has('line:' + line.id) || store.isRemoving(line.id)"
                        aria-label="Quitar la línea"
                        i18n-aria-label="@@staff.tickets.remove.label"
                        pTooltip="Quitar la línea de la cuenta"
                        i18n-pTooltip="@@staff.tickets.remove.tooltip"
                        tooltipPosition="top"
                        (click)="store.removeLine(line)"
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
                  } @else if (store.isOpen() && canPay()) {
                    <button
                      pButton
                      type="button"
                      severity="danger"
                      [attr.data-testid]="'void-payment-' + payment.id"
                      [disabled]="
                        !store.enabled() || store.pending.has('void-payment:' + payment.id)
                      "
                      pTooltip="Anular este pago con un motivo"
                      i18n-pTooltip="@@staff.tickets.voidPayment.tooltip"
                      tooltipPosition="top"
                      (click)="startRegisterVoid({ kind: 'payment', id: payment.id })"
                    >
                      <tabler-icon [icon]="icons.void" aria-hidden="true" />
                      <span i18n="@@staff.tickets.voidPayment">Anular pago</span>
                    </button>
                  }
                </li>
              }
            </ul>
          }

          @if (store.isOpen()) {
            <div class="field">
              <app-form-field
                label="Nota de la cuenta"
                i18n-label="@@staff.tickets.note"
                controlId="ticket-note"
              >
                <textarea
                  pTextarea
                  id="ticket-note"
                  data-testid="ticket-note"
                  [formControl]="noteControl"
                  maxlength="140"
                  rows="2"
                  fluid
                ></textarea>
              </app-form-field>
              <button
                pButton
                type="button"
                severity="secondary"
                data-testid="ticket-note-save"
                [disabled]="!store.enabled() || store.pending.has('note') || !noteChanged()"
                pTooltip="Guardar la nota de la cuenta"
                i18n-pTooltip="@@staff.tickets.note.save.tooltip"
                tooltipPosition="top"
                (click)="saveNote()"
              >
                <tabler-icon [icon]="icons.save" aria-hidden="true" />
                <span i18n="@@staff.tickets.note.save">Guardar nota</span>
              </button>
            </div>
            <div class="actions">
              <button
                pButton
                type="button"
                data-testid="send"
                [disabled]="
                  !store.enabled() || store.pending.has('send') || !store.canSend()
                "
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
                [disabled]="
                  !store.enabled() || store.pending.has('repeat') || store.lastRound().length === 0
                "
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
              <button
                pButton
                type="button"
                severity="secondary"
                data-testid="merge-open"
                [disabled]="!store.enabled()"
                pTooltip="Unir otra cuenta abierta a esta"
                i18n-pTooltip="@@staff.tickets.merge.tooltip"
                tooltipPosition="top"
                (click)="startMerge()"
              >
                <tabler-icon [icon]="icons.merge" aria-hidden="true" />
                <span i18n="@@staff.tickets.merge">Unir cuenta</span>
              </button>
              @if (store.canCancel()) {
                <button
                  pButton
                  type="button"
                  severity="danger"
                  data-testid="cancel-open"
                  [disabled]="!store.enabled()"
                  pTooltip="Descartar la cuenta si todavía no se cobró nada"
                  i18n-pTooltip="@@staff.tickets.cancel.open.tooltip"
                  tooltipPosition="top"
                  (click)="cancelling.set(true)"
                >
                  <tabler-icon [icon]="icons.cancelTicket" aria-hidden="true" />
                  <span i18n="@@staff.tickets.cancel.open">Cancelar cuenta</span>
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
          } @else {
            @if (ticket.saleVoid; as saleVoid) {
              <p class="closed" data-testid="sale-voided">
                <ng-container i18n="@@staff.tickets.saleVoided">Venta anulada:</ng-container>
                {{ label(saleVoid.reasonCode) }}
              </p>
            } @else if (canPay()) {
              <div class="actions">
                <button
                  pButton
                  type="button"
                  severity="danger"
                  data-testid="void-sale"
                  [disabled]="!store.enabled() || store.pending.has('void-sale')"
                  pTooltip="Anular toda la venta con un motivo"
                  i18n-pTooltip="@@staff.tickets.voidSale.tooltip"
                  tooltipPosition="top"
                  (click)="startRegisterVoid({ kind: 'sale' })"
                >
                  <tabler-icon [icon]="icons.void" aria-hidden="true" />
                  <span i18n="@@staff.tickets.voidSale">Anular venta</span>
                </button>
              </div>
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
      [closable]="!adding()"
      [closeOnEscape]="false"
      [dismissableMask]="false"
      [style]="{ width: 'min(40rem, calc(100vw - 1rem))' }"
      [visible]="configuring() !== null"
      (visibleChange)="closeAdd($event)"
    >
      <div class="add-body" tabindex="-1" [attr.aria-busy]="adding()" (keydown.escape)="escapeAdd($event)">
        @if (configuring(); as product) {
          <app-product-configurator
            [product]="product"
            [busy]="adding()"
            (confirmed)="add($event)"
            (cancelled)="closeAdd(false)"
          />
        }
      </div>
    </p-dialog>

    <app-form-dialog
      header="Cambiar de mesa"
      i18n-header="@@staff.tickets.transfer.title"
      width="30rem"
      [visible]="transferring()"
      [busy]="store.pending.has('transfer')"
      [saveDisabled]="!chosenTable()"
      [saveLabel]="labels.transfer"
      [saveTooltip]="labels.transferTooltip"
      [saveIcon]="icons.confirm"
      [disabledReason]="chosenTable() ? null : labels.transferWhy"
      (saved)="transfer()"
      (dismissed)="closeTransfer(false)"
    >
      <app-form-field
        label="Mesa de destino"
        i18n-label="@@staff.tickets.transfer.table"
        controlId="transfer-table"
      >
        <p-select
          inputId="transfer-table"
          [ariaLabel]="labels.transferAria"
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
      </app-form-field>
    </app-form-dialog>

    <app-form-dialog
      header="Unir cuenta"
      i18n-header="@@staff.tickets.merge.title"
      width="30rem"
      [visible]="merging()"
      [busy]="store.pending.has('merge')"
      [saveDisabled]="!chosenSource()"
      [saveLabel]="labels.merge"
      [saveTooltip]="labels.mergeTooltip"
      [saveIcon]="icons.merge"
      [disabledReason]="chosenSource() ? null : labels.mergeWhy"
      (saved)="merge()"
      (dismissed)="closeMerge(false)"
    >
      <div class="dialog-fields">
        <p i18n="@@staff.tickets.merge.explain">
          Las líneas de la cuenta elegida pasan a esta cuenta y esa cuenta queda unida a ella.
        </p>
        @if (store.mergeCandidatesFailed()) {
          <div data-testid="merge-failed" role="status">
            <p i18n="@@staff.tickets.merge.failed">No se pudieron cargar las cuentas abiertas.</p>
            <button
              pButton
              type="button"
              severity="secondary"
              data-testid="merge-retry"
              pTooltip="Volver a cargar las cuentas abiertas"
              i18n-pTooltip="@@staff.tickets.merge.retry.tooltip"
              tooltipPosition="top"
              (click)="store.loadMergeCandidates()"
            >
              <tabler-icon [icon]="icons.retry" aria-hidden="true" />
              <span i18n="@@staff.tickets.merge.retry">Reintentar</span>
            </button>
          </div>
        } @else if (!store.mergeCandidatesLoaded()) {
          <p class="muted" i18n="@@staff.tickets.merge.loading">Cargando las cuentas abiertas…</p>
        } @else {
          @if (store.mergeCandidates().length === 0) {
            <p data-testid="merge-empty" i18n="@@staff.tickets.merge.empty">
              No hay otras cuentas abiertas para unir.
            </p>
          }
          <app-form-field
            label="Cuenta a unir"
            i18n-label="@@staff.tickets.merge.source"
            controlId="merge-source"
          >
            <p-select
              inputId="merge-source"
              [ariaLabel]="labels.mergeAria"
              [formControl]="mergeSource"
              optionLabel="label"
              optionValue="id"
              [options]="mergeOptions()"
              [filter]="true"
              filterBy="label"
              [resetFilterOnHide]="true"
              filterPlaceholder="Buscar"
              i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
              placeholder="Elige una cuenta"
              i18n-placeholder="@@staff.tickets.merge.placeholder"
              [appendTo]="'body'"
              fluid
            />
          </app-form-field>
        }
      </div>
    </app-form-dialog>

    <app-form-dialog
      header="Cancelar cuenta"
      i18n-header="@@staff.tickets.cancel.title"
      width="30rem"
      [visible]="cancelling()"
      [busy]="store.pending.has('cancel')"
      [saveLabel]="labels.cancelTicket"
      [saveTooltip]="labels.cancelTicketTooltip"
      [saveIcon]="icons.cancelTicket"
      [cancelLabel]="labels.back"
      [cancelTooltip]="labels.backTooltip"
      [saveSeverity]="'danger'"
      (saved)="cancelTicket()"
      (dismissed)="closeCancel(false)"
    >
      <p data-testid="cancel-explain" i18n="@@staff.tickets.cancel.explain">
        Se descartan las líneas sin enviar y la cuenta queda cancelada. La mesa vuelve a quedar
        libre y esto no se puede deshacer.
      </p>
    </app-form-dialog>

    <app-void-dialog
      [header]="registerVoidTitle()"
      [confirmLabel]="registerVoidTitle()"
      [confirmTooltip]="registerVoidTooltip()"
      [visible]="registerVoid() !== null"
      [reasons]="store.reasons()"
      [failed]="store.reasonsLoadFailed()"
      [busy]="registerVoidBusy()"
      (confirmed)="confirmRegisterVoid($event)"
      (dismissed)="registerVoid.set(null)"
      (retried)="store.loadVoidReasons()"
    />

    <app-void-dialog
      header="Dar de cortesía"
      i18n-header="@@staff.tickets.comp.title"
      [confirmLabel]="compLabel"
      [confirmTooltip]="compTooltip"
      [confirmIcon]="icons.comp"
      [visible]="comping() !== null"
      [reasons]="store.reasons()"
      [failed]="store.reasonsLoadFailed()"
      [busy]="compBusy()"
      (confirmed)="confirmComp($event)"
      (dismissed)="comping.set(null)"
      (retried)="store.loadCompReasons()"
    >
      @if (comping(); as line) {
        <p class="detail">{{ line.quantity }} × {{ line.productName }}</p>
      }
    </app-void-dialog>

    <app-void-dialog
      header="Anular línea"
      i18n-header="@@staff.tickets.void.title"
      [confirmLabel]="labels.voidLine"
      [confirmTooltip]="labels.voidLineTooltip"
      [visible]="voiding() !== null"
      [reasons]="store.reasons()"
      [failed]="store.reasonsLoadFailed()"
      [busy]="voidBusy()"
      (confirmed)="confirmVoid($event)"
      (dismissed)="voiding.set(null)"
      (retried)="store.loadVoidReasons()"
    >
      @if (voiding(); as line) {
        <p class="detail">{{ line.quantity }} × {{ line.productName }}</p>
      }
    </app-void-dialog>
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
    save: IconDeviceFloppy,
    cancelTicket: IconCircleOff,
    comp: IconGift,
    merge: IconGitMerge,
  };
  protected readonly tips = {
    billOn: $localize`:@@staff.tickets.billOn.tooltip:Avisar que la mesa pidió la cuenta`,
    billOff: $localize`:@@staff.tickets.billOff.tooltip:Quitar el aviso de que la mesa pidió la cuenta`,
  };
  protected readonly maxQuantity = MAX_LINE_QUANTITY;
  protected readonly labels = {
    transfer: $localize`:@@staff.tickets.transfer.confirm:Mover`,
    transferTooltip: $localize`:@@staff.tickets.transfer.confirm.tooltip:Mover la cuenta a la mesa elegida`,
    transferWhy: $localize`:@@staff.tickets.transfer.why:Elige una mesa libre para mover la cuenta.`,
    transferAria: $localize`:@@staff.tickets.transfer.aria:Mesa de destino`,
    merge: $localize`:@@staff.tickets.merge.confirm:Unir`,
    mergeTooltip: $localize`:@@staff.tickets.merge.confirm.tooltip:Unir la cuenta elegida a esta`,
    mergeWhy: $localize`:@@staff.tickets.merge.why:Elige la cuenta que quieres unir a esta.`,
    mergeAria: $localize`:@@staff.tickets.merge.aria:Cuenta a unir`,
    cancelTicket: $localize`:@@staff.tickets.cancel.confirm:Cancelar cuenta`,
    cancelTicketTooltip: $localize`:@@staff.tickets.cancel.confirm.tooltip:Cancelar la cuenta definitivamente`,
    back: $localize`:@@staff.tickets.cancel.back:Volver`,
    backTooltip: $localize`:@@staff.tickets.cancel.back.tooltip:Volver sin cancelar la cuenta`,
    voidLine: $localize`:@@staff.tickets.void.confirm:Anular línea`,
    voidLineTooltip: $localize`:@@staff.tickets.void.confirm.tooltip:Anular la línea con este motivo`,
  };
  protected readonly adding = computed(() => this.store.pending.has('add'));

  protected readonly money = formatMoneyOrDash;
  protected readonly method = methodLabel;
  private readonly auth = inject(AuthStore);
  private readonly shifts = inject(ShiftStore);
  // Only once the shift was read (the pay panel was opened): then a shift refusal means that read is stale.
  private readonly rereadShift = effect(() => {
    if (this.store.shiftStateErrors() > 0) {
      untracked(() => {
        if (this.shifts.loaded()) {
          this.shifts.load('shift');
        }
      });
    }
  });
  private readonly router = inject(Router);
  protected readonly paying = signal(false);
  protected readonly cancelling = signal(false);
  protected readonly noteControl = new FormControl('', { nonNullable: true });
  private readonly typedTicketNote = toSignal(this.noteControl.valueChanges, { initialValue: '' });
  private readonly savedNote = computed(() => this.store.ticket()?.note ?? '');
  protected readonly noteChanged = computed(
    () => this.typedTicketNote().trim() !== this.savedNote(),
  );
  // The text follows the server's note, except while the user is typing a different one.
  private readonly syncNote = effect(() => {
    const saved = this.savedNote();
    untracked(() => {
      const typed = this.noteControl.value;
      if (typed.trim() === '' || typed === this.lastSyncedNote || typed.trim() === saved) {
        this.noteControl.setValue(saved);
      }
      this.lastSyncedNote = saved;
    });
  });
  private lastSyncedNote = '';
  protected readonly registerVoid = signal<RegisterVoid | null>(null);
  protected readonly registerVoidTitle = computed(() =>
    this.registerVoid()?.kind === 'sale'
      ? $localize`:@@staff.tickets.voidSale.title:Anular venta`
      : $localize`:@@staff.tickets.voidPayment.title:Anular pago`,
  );
  protected readonly registerVoidTooltip = computed(() =>
    this.registerVoid()?.kind === 'sale'
      ? $localize`:@@staff.tickets.voidSale.confirm.tooltip:Anular toda la venta con el motivo elegido`
      : $localize`:@@staff.tickets.voidPayment.confirm.tooltip:Anular este pago con el motivo elegido`,
  );
  protected readonly registerVoidBusy = computed(() => {
    const target = this.registerVoid();
    if (target === null) {
      return false;
    }
    return this.store.pending.has(
      target.kind === 'sale' ? 'void-sale' : `void-payment:${target.id}`,
    );
  });
  protected readonly compLabel = $localize`:@@staff.tickets.comp.confirm:Dar de cortesía`;
  protected readonly compTooltip = $localize`:@@staff.tickets.comp.confirm.tooltip:Dar la línea de cortesía con el motivo elegido`;
  protected readonly comping = signal<TicketLine | null>(null);
  protected readonly compBusy = computed(() =>
    this.store.pending.has(`comp:${this.comping()?.id}`),
  );
  protected readonly merging = signal(false);
  protected readonly mergeSource = new FormControl<string | null>(null);
  protected readonly chosenSource = toSignal(this.mergeSource.valueChanges, { initialValue: null });
  protected readonly mergeOptions = computed(() =>
    this.store.mergeCandidates().map((candidate) => ({
      id: candidate.id,
      label:
        candidate.type === 'TABLE'
          ? $localize`:@@staff.tickets.merge.option.table:${candidate.tableName}:name: · Cuenta ${candidate.number}:number:`
          : $localize`:@@staff.tickets.merge.option.counter:Mostrador: ${candidate.counterName}:name: · Cuenta ${candidate.number}:number:`,
    })),
  );
  protected readonly canPay = computed(
    () => this.auth.role() === 'CASHIER' || this.auth.role() === 'ADMIN',
  );
  protected readonly configuring = signal<MenuProduct | null>(null);
  protected readonly transferring = signal(false);
  protected readonly target = new FormControl<string | null>(null);
  protected readonly label = reasonLabel;
  protected readonly voiding = signal<TicketLine | null>(null);
  protected readonly voidBusy = computed(() =>
    this.store.pending.has(`void:${this.voiding()?.id}`),
  );
  protected readonly chosenTable = toSignal(this.target.valueChanges, { initialValue: null });

  protected modifiersOf(line: TicketLine): string {
    return line.modifiers.map((modifier) => modifier.name).join(', ');
  }

  protected add(line: LineRequest): void {
    this.store.addLine(line, () => this.configuring.set(null));
  }

  protected closeAdd(visible: boolean): void {
    if (!visible && !this.adding()) {
      this.configuring.set(null);
    }
  }

  // p-dialog reads closeOnEscape once, so Escape is handled here and ignored while the line is added.
  protected escapeAdd(event: Event): void {
    if (!event.defaultPrevented) {
      this.closeAdd(false);
    }
  }

  protected saveNote(): void {
    const note = this.noteControl.value.trim();
    this.store.changeNote(note === '' ? null : note);
  }

  protected closeCancel(visible: boolean): void {
    if (!visible) {
      this.cancelling.set(false);
    }
  }

  protected cancelTicket(): void {
    this.store.cancel(() => {
      this.cancelling.set(false);
      void this.router.navigateByUrl('/staff/tables');
    });
  }

  protected startComp(line: TicketLine): void {
    this.store.loadCompReasons();
    this.comping.set(line);
  }

  protected confirmComp(choice: VoidChoice): void {
    const line = this.comping();
    if (line) {
      this.store.compLine(line, choice, () => this.comping.set(null));
    }
  }

  protected startMerge(): void {
    this.mergeSource.reset();
    this.store.loadMergeCandidates();
    this.merging.set(true);
  }

  protected closeMerge(visible: boolean): void {
    if (!visible) {
      this.merging.set(false);
    }
  }

  protected merge(): void {
    const source = this.mergeSource.value;
    if (source) {
      this.store.merge(source, () => this.merging.set(false));
    }
  }

  protected startRegisterVoid(target: RegisterVoid): void {
    this.store.loadVoidReasons();
    this.registerVoid.set(target);
  }

  protected confirmRegisterVoid(choice: VoidChoice): void {
    const target = this.registerVoid();
    const close = () => this.registerVoid.set(null);
    if (target?.kind === 'payment') {
      this.store.voidPayment(target.id, choice, close);
    } else if (target?.kind === 'sale') {
      this.store.voidSale(choice, close);
    }
  }

  protected startVoid(line: TicketLine): void {
    this.store.loadVoidReasons();
    this.voiding.set(line);
  }

  protected confirmVoid(choice: VoidChoice): void {
    const line = this.voiding();
    if (line) {
      this.store.voidLine(line, choice, () => this.voiding.set(null));
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
