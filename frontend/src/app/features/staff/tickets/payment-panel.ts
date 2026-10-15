import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  IconCash,
  IconDivide,
  IconLockOpen,
  IconRefresh,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Checkbox } from 'primeng/checkbox';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Tooltip } from 'primeng/tooltip';
import type { PaymentMethod, RecordPaymentRequest, Ticket, TicketLine } from '../../../core/api/api-types';
import { addMoney, formatMoney, parseAmount, toMinorUnits } from '../../../core/money/money';
import { methodLabel, PAYMENT_METHODS } from '../register/payment-methods';
import { ShiftStore } from '../register/shift-store';
import { TicketEditorStore } from './ticket-editor-store';

const SPLIT_PARTS = Array.from({ length: 19 }, (_, index) => index + 2);

// Container inside the ticket editor: the amounts come from the ticket and the split, only the lines' sum is added here.
@Component({
  selector: 'app-payment-panel',
  imports: [
    ReactiveFormsModule,
    FormsModule,
    RouterLink,
    ButtonDirective,
    Checkbox,
    InputText,
    Select,
    Tooltip,
    TablerIconComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './payment-panel.scss',
  template: `
    <h3 id="pay-heading" i18n="@@staff.tickets.pay.title">Cobrar</h3>

    @if (shifts.failed()) {
      <div class="notice" role="status" data-testid="shift-failed">
        <ng-container i18n="@@staff.tickets.pay.shiftFailed">No se pudo leer el turno de caja.</ng-container>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="shift-retry"
          pTooltip="Volver a leer el turno de caja"
          i18n-pTooltip="@@staff.tickets.pay.shiftRetry.tooltip"
          tooltipPosition="top"
          (click)="shifts.load('shift')"
        >
          <tabler-icon [icon]="icons.retry" aria-hidden="true" />
          <span i18n="@@staff.tickets.pay.shiftRetry">Reintentar</span>
        </button>
      </div>
    } @else if (shifts.loaded() && shifts.shift() === null) {
      <div class="notice" role="status" data-testid="no-shift">
        <ng-container i18n="@@staff.tickets.pay.noShift">Necesitas un turno de caja abierto para cobrar.</ng-container>
        <a
          pButton
          severity="secondary"
          routerLink="/staff/register"
          pTooltip="Ir a Caja para abrir un turno"
          i18n-pTooltip="@@staff.tickets.pay.openShift.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.openShift" aria-hidden="true" />
          <span i18n="@@staff.tickets.pay.openShift">Abrir turno</span>
        </a>
      </div>
    }
    @if (store.unsent().length > 0) {
      <p class="notice" role="status" data-testid="unsent-lines" i18n="@@staff.tickets.pay.unsent">
        Envía o elimina las líneas pendientes antes de cobrar.
      </p>
    }

    <form (submit)="submit($event)" [attr.aria-labelledby]="'pay-heading'">
      <div class="field">
        <label for="pay-method" id="pay-method-label" i18n="@@staff.tickets.pay.method">Método de pago</label>
        <p-select
          inputId="pay-method"
          [ariaLabelledBy]="'pay-method-label'"
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

      @if (payableLines().length > 0) {
        <fieldset>
          <legend i18n="@@staff.tickets.pay.byItems">Cobrar solo estas líneas (opcional)</legend>
          @for (line of payableLines(); track line.id) {
            <label class="choice" [for]="'pay-line-input-' + line.id">
              <p-checkbox
                [inputId]="'pay-line-input-' + line.id"
                [binary]="true"
                [ngModel]="selected().has(line.id)"
                [ngModelOptions]="{ standalone: true }"
                [attr.data-testid]="'pay-line-' + line.id"
                (ngModelChange)="toggleLine(line.id)"
              />
              <span class="name">{{ line.quantity }} × {{ line.productName }}</span>
              <span>{{ money(line.lineTotal) }}</span>
            </label>
          }
        </fieldset>
      }

      <div class="field">
        <label for="pay-amount" i18n="@@staff.tickets.pay.amount">Monto a cobrar</label>
        <input
          pInputText
          id="pay-amount"
          data-testid="pay-amount"
          [formControl]="amount"
          [attr.inputmode]="'decimal'"
          autocomplete="off"
          (input)="partIndex.set(null)"
          fluid
        />
      </div>

      @if (chosenMethod() === 'CASH') {
        <div class="field">
          <label for="pay-received" i18n="@@staff.tickets.pay.received">Efectivo recibido</label>
          <input
            pInputText
            id="pay-received"
            data-testid="pay-received"
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
        data-testid="pay-submit"
        [loading]="paying()"
        [disabled]="!canPay() || paying()"
        pTooltip="Registrar el cobro de esta cuenta"
        i18n-pTooltip="@@staff.tickets.pay.submit.tooltip"
        tooltipPosition="top"
      >
        @if (!paying()) {
          <tabler-icon [icon]="icons.pay" aria-hidden="true" />
        }
        <span i18n="@@staff.tickets.pay.submit">Registrar cobro</span>
      </button>
    </form>

    <div class="split">
      <h4 i18n="@@staff.tickets.pay.split">Dividir la cuenta</h4>
      <div class="split-controls">
        <div class="field">
          <label for="pay-parts" id="pay-parts-label" i18n="@@staff.tickets.pay.parts">Partes iguales</label>
          <p-select
            inputId="pay-parts"
            [ariaLabelledBy]="'pay-parts-label'"
            [formControl]="parts"
            optionLabel="label"
            optionValue="value"
            [options]="partOptions"
            [filter]="true"
            filterBy="label"
            [resetFilterOnHide]="true"
            filterPlaceholder="Buscar"
            i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
            placeholder="Elige cuántas"
            i18n-placeholder="@@staff.tickets.pay.parts.placeholder"
            [appendTo]="'body'"
            fluid
          />
        </div>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="split-load"
          [disabled]="chosenParts() === null || store.pending.has('split')"
          pTooltip="Calcular las partes de lo que falta cobrar"
          i18n-pTooltip="@@staff.tickets.pay.splitLoad.tooltip"
          tooltipPosition="top"
          (click)="loadSplit()"
        >
          <tabler-icon [icon]="icons.split" aria-hidden="true" />
          <span i18n="@@staff.tickets.pay.splitLoad">Calcular</span>
        </button>
      </div>
      @if (store.splitParts(); as parts) {
        <ul class="parts">
          @for (part of parts; track $index) {
            <li>
              <button
                pButton
                type="button"
                severity="secondary"
                [attr.data-testid]="'split-part-' + $index"
                [disabled]="paidParts().has($index)"
                pTooltip="Usar esta parte como el monto a cobrar"
                i18n-pTooltip="@@staff.tickets.pay.part.tooltip"
                tooltipPosition="top"
                (click)="usePart($index)"
              >
                <tabler-icon [icon]="icons.pay" aria-hidden="true" />
                <span>{{ money(part) }}</span>
              </button>
            </li>
          }
        </ul>
      }
    </div>
  `,
})
export class PaymentPanel {
  protected readonly store = inject(TicketEditorStore);
  protected readonly shifts = inject(ShiftStore);

  readonly ticket = input.required<Ticket>();

  protected readonly icons = { pay: IconCash, split: IconDivide, retry: IconRefresh, openShift: IconLockOpen };
  protected readonly money = formatMoney;
  protected readonly methods = PAYMENT_METHODS.map((value) => ({ value, label: methodLabel(value) }));
  protected readonly partOptions = SPLIT_PARTS.map((value) => ({ value, label: String(value) }));
  protected readonly method = new FormControl<PaymentMethod>('CASH', { nonNullable: true });
  protected readonly amount = new FormControl('', { nonNullable: true });
  protected readonly received = new FormControl('', { nonNullable: true });
  protected readonly parts = new FormControl<number | null>(null);
  protected readonly selected = signal<ReadonlySet<string>>(new Set());
  protected readonly partIndex = signal<number | null>(null);
  protected readonly paidParts = signal<ReadonlySet<number>>(new Set());

  protected readonly chosenMethod = toSignal(this.method.valueChanges, { initialValue: 'CASH' as PaymentMethod });
  protected readonly chosenParts = toSignal(this.parts.valueChanges, { initialValue: null });
  private readonly typedAmount = toSignal(this.amount.valueChanges, { initialValue: '' });
  private readonly typedReceived = toSignal(this.received.valueChanges, { initialValue: '' });
  private readonly balance = computed(() => this.ticket().balance.amount);

  protected readonly payableLines = computed<TicketLine[]>(() => {
    const ticket = this.ticket();
    const covered = new Set(ticket.payments.filter((payment) => !payment.voided).flatMap((p) => p.lineIds));
    return ticket.lines.filter((line) => line.state === 'SENT' && !covered.has(line.id));
  });
  // Only picked lines that can still be paid count: a re-read may have covered one since.
  private readonly chosenLines = computed(() =>
    this.payableLines().filter((line) => this.selected().has(line.id)),
  );
  // A payment by items must equal the sum of its lines, which the API checks.
  private readonly itemsAmount = computed<string | null>(() => {
    const chosen = this.chosenLines();
    if (chosen.length === 0) {
      return null;
    }
    const currency = this.ticket().total.currency;
    return chosen.reduce((sum, line) => addMoney(sum, line.lineTotal), { amount: '0.00', currency }).amount;
  });
  protected readonly paying = computed(() => this.store.pending.has('pay'));
  protected readonly canPay = computed(() => {
    const currency = this.ticket().total.currency;
    const amount = parseAmount(this.typedAmount());
    if (amount === null || amount === '0.00' || !this.store.enabled()) {
      return false;
    }
    if (this.shifts.shift() === null || this.store.unsent().length > 0) {
      return false;
    }
    const minor = toMinorUnits({ amount, currency });
    if (minor > toMinorUnits(this.ticket().balance)) {
      return false;
    }
    if (this.chosenMethod() === 'CASH') {
      const received = parseAmount(this.typedReceived());
      return received !== null && toMinorUnits({ amount: received, currency }) >= minor;
    }
    return true;
  });

  constructor() {
    this.shifts.load('shift');
    inject(DestroyRef).onDestroy(() => this.store.clearSplit());
    effect(() => {
      const payable = new Set(this.payableLines().map((line) => line.id));
      untracked(() => {
        const current = this.selected();
        if ([...current].some((id) => !payable.has(id))) {
          this.selected.set(new Set([...current].filter((id) => payable.has(id))));
        }
      });
    });
    effect(() => {
      const items = this.itemsAmount();
      const balance = this.balance();
      untracked(() => {
        if (items === null) {
          this.amount.enable();
          this.amount.setValue(balance);
        } else {
          this.amount.setValue(items);
          this.amount.disable();
        }
      });
    });
  }

  protected toggleLine(lineId: string): void {
    this.partIndex.set(null);
    this.selected.update((current) => {
      const next = new Set(current);
      if (!next.delete(lineId)) {
        next.add(lineId);
      }
      return next;
    });
  }

  protected loadSplit(): void {
    const parts = this.parts.value;
    if (parts !== null) {
      this.paidParts.set(new Set());
      this.partIndex.set(null);
      this.store.loadSplit(parts);
    }
  }

  protected usePart(index: number): void {
    const part = this.store.splitParts()?.[index];
    if (part) {
      this.selected.set(new Set());
      this.amount.enable();
      this.amount.setValue(part.amount);
      this.partIndex.set(index);
    }
  }

  protected submit(event: Event): void {
    event.preventDefault();
    const currency = this.ticket().total.currency;
    const amount = parseAmount(this.amount.getRawValue());
    if (amount === null || !this.canPay() || this.paying()) {
      return;
    }
    const request: RecordPaymentRequest = { method: this.method.value, amount: { amount, currency } };
    if (this.method.value === 'CASH') {
      request.receivedAmount = { amount: parseAmount(this.received.value) ?? amount, currency };
    }
    const chosen = this.chosenLines();
    if (chosen.length > 0) {
      request.lineIds = chosen.map((line) => line.id);
    }
    const part = this.partIndex();
    this.store.pay(request, () => {
      this.selected.set(new Set());
      this.received.reset();
      if (part !== null) {
        this.paidParts.update((current) => new Set(current).add(part));
        this.partIndex.set(null);
      }
    });
  }
}
