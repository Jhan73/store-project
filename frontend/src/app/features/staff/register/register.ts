import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { IconLock, IconLockOpen, IconRefresh, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Tooltip } from 'primeng/tooltip';
import { formatMoney, parseAmount } from '../../../core/money/money';
import { amountValidator } from '../../admin/amount-validator';
import { CashMovements } from './cash-movements';
import { CloseShift } from './close-shift';
import { ShiftResult } from './shift-result';
import { ShiftStore } from './shift-store';

@Component({
  selector: 'app-register',
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    InputText,
    Tooltip,
    TablerIconComponent,
    CashMovements,
    CloseShift,
    ShiftResult,
  ],
  providers: [ShiftStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './register.scss',
  template: `
    <h1 i18n="@@staff.register.title">Caja</h1>

    @if (store.failed()) {
      <div class="offline" role="status" data-testid="load-failed">
        <ng-container i18n="@@staff.register.failed">No se pudo cargar el turno de caja.</ng-container>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="retry"
          (click)="store.load()"
          pTooltip="Volver a cargar el turno de caja"
          i18n-pTooltip="@@staff.register.retry.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.retry" aria-hidden="true" />
          <span i18n="@@staff.register.retry">Reintentar</span>
        </button>
      </div>
    } @else if (!store.loaded()) {
      <p class="muted" i18n="@@staff.register.loading">Cargando el turno…</p>
    } @else if (store.shift(); as shift) {
      <section data-testid="shift-summary" [attr.aria-labelledby]="'shift-heading'">
        <h2 id="shift-heading" i18n="@@staff.register.open.title">Turno abierto</h2>
        <dl>
          <div>
            <dt i18n="@@staff.register.open.since">Desde</dt>
            <dd>{{ store.formatTime(shift.openedAt) }}</dd>
          </div>
          <div>
            <dt i18n="@@staff.register.open.float">Fondo inicial</dt>
            <dd data-testid="opening-float">{{ money(shift.openingFloat) }}</dd>
          </div>
        </dl>
        <p class="muted" i18n="@@staff.register.open.blind">
          El efectivo esperado se muestra al cerrar el turno, después de contar la caja.
        </p>
        <div class="actions">
          <button
            pButton
            type="button"
            severity="danger"
            data-testid="close-open"
            pTooltip="Contar la caja y cerrar el turno"
            i18n-pTooltip="@@staff.register.open.close.tooltip"
            tooltipPosition="top"
            (click)="closing.set(true)"
          >
            <tabler-icon [icon]="icons.close" aria-hidden="true" />
            <span i18n="@@staff.register.open.close">Cerrar turno</span>
          </button>
        </div>
      </section>
      <app-cash-movements />
    } @else if (store.closedShift(); as closed) {
      <section data-testid="closed-summary" [attr.aria-labelledby]="'closed-heading'">
        <h2 id="closed-heading" i18n="@@staff.register.closed.title">Turno cerrado</h2>
        <app-shift-result [shift]="closed" [timeZone]="store.timeZone()" />
        <div class="actions">
          <button
            pButton
            type="button"
            data-testid="new-shift"
            pTooltip="Volver a abrir un turno de caja"
            i18n-pTooltip="@@staff.register.closed.new.tooltip"
            tooltipPosition="top"
            (click)="store.dismissClosed()"
          >
            <tabler-icon [icon]="icons.open" aria-hidden="true" />
            <span i18n="@@staff.register.closed.new">Abrir un turno nuevo</span>
          </button>
        </div>
      </section>
    } @else {
      <form data-testid="open-form" (submit)="open($event)" [attr.aria-labelledby]="'open-heading'">
        <h2 id="open-heading" i18n="@@staff.register.none.title">No hay un turno abierto</h2>
        <p class="muted" i18n="@@staff.register.none.hint">
          Abre un turno con el efectivo que dejas en la caja para cobrar.
        </p>
        <div class="field">
          <label for="float" i18n="@@staff.register.none.float">Fondo inicial</label>
          <input
            pInputText
            id="float"
            data-testid="float"
            [formControl]="float"
            [attr.inputmode]="'decimal'"
            autocomplete="off"
            [attr.aria-invalid]="showError() ? 'true' : null"
          />
          @if (showError()) {
            <small class="error" data-testid="float-error" i18n="@@staff.register.none.floatInvalid"
              >Escribe un monto, como 50 o 50.00.</small
            >
          }
        </div>
        @if (store.currencyFailed()) {
          <div class="offline" role="status" data-testid="currency-failed">
            <ng-container i18n="@@staff.register.none.currencyFailed"
              >No se pudo leer la moneda de la tienda, así que todavía no se puede abrir un turno.</ng-container
            >
            <button
              pButton
              type="button"
              severity="secondary"
              data-testid="currency-retry"
              (click)="store.retryCurrency()"
              pTooltip="Volver a leer la moneda de la tienda"
              i18n-pTooltip="@@staff.register.none.currencyRetry.tooltip"
              tooltipPosition="top"
            >
              <tabler-icon [icon]="icons.retry" aria-hidden="true" />
              <span i18n="@@staff.register.none.currencyRetry">Reintentar</span>
            </button>
          </div>
        } @else if (store.currency() === null) {
          <p class="muted" data-testid="no-currency" i18n="@@staff.register.none.noCurrency">
            Todavía no hay productos en el menú, así que no se puede abrir un turno.
          </p>
        }
        <button
          pButton
          type="submit"
          data-testid="open-submit"
          [loading]="opening()"
          [disabled]="opening() || store.currency() === null"
          pTooltip="Abrir el turno con este fondo inicial"
          i18n-pTooltip="@@staff.register.none.submit.tooltip"
          tooltipPosition="top"
        >
          @if (!opening()) {
            <tabler-icon [icon]="icons.open" aria-hidden="true" />
          }
          <span i18n="@@staff.register.none.submit">Abrir turno</span>
        </button>
      </form>
    }

    <app-close-shift [visible]="closing()" (dismissed)="closing.set(false)" />
  `,
})
export class Register {
  protected readonly store = inject(ShiftStore);

  protected readonly icons = { open: IconLockOpen, close: IconLock, retry: IconRefresh };
  protected readonly closing = signal(false);
  protected readonly money = formatMoney;
  protected readonly float = new FormControl('', { nonNullable: true, validators: [amountValidator] });
  private readonly attempted = signal(false);
  protected readonly showError = computed(() => this.attempted() && this.float.invalid);
  protected readonly opening = computed(() => this.store.pending.has('open'));

  constructor() {
    this.store.load();
  }

  protected open(event: Event): void {
    event.preventDefault();
    this.attempted.set(true);
    const amount = parseAmount(this.float.value);
    if (amount !== null && !this.opening()) {
      this.store.open(amount);
    }
  }
}
