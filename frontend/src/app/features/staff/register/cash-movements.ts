import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { IconCheck, IconRefresh, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import type { CashMovementRequest } from '../../../core/api/api-types';
import { formatMoney, parseAmount } from '../../../core/money/money';
import { reasonLabel, requiresNote } from '../../../core/reasons/reason-rules';
import { TableScroll } from '../../../shared/ui/table-scroll/table-scroll';
import { ShiftStore } from './shift-store';

type MovementType = CashMovementRequest['type'];

// Container: it uses the shift store of the screen that hosts it.
@Component({
  selector: 'app-cash-movements',
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    InputText,
    Select,
    Textarea,
    Tooltip,
    TablerIconComponent,
    TableScroll,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './register-panel.scss',
  template: `
    <h2 i18n="@@staff.register.movements.title">Entradas y salidas de efectivo</h2>

    @if (store.movements().length === 0) {
      <p class="muted" data-testid="no-movements" i18n="@@staff.register.movements.empty">
        Este turno todavía no tiene movimientos de efectivo.
      </p>
    } @else {
      <app-table-scroll label="Movimientos de efectivo" i18n-label="@@staff.register.movements.scroll">
        <table>
          <thead>
            <tr>
              <th i18n="@@staff.register.movements.time">Hora</th>
              <th i18n="@@staff.register.movements.type">Tipo</th>
              <th i18n="@@staff.register.movements.amount">Monto</th>
              <th i18n="@@staff.register.movements.detail">Detalle</th>
              <th i18n="@@staff.register.movements.reference">Referencia</th>
            </tr>
          </thead>
          <tbody>
            @for (movement of store.movements(); track movement.id) {
              <tr>
                <td>{{ store.formatTime(movement.recordedAt) }}</td>
                <td>
                  @if (movement.type === 'IN') {
                    <ng-container i18n="@@staff.register.movements.in">Entrada</ng-container>
                  } @else {
                    <ng-container i18n="@@staff.register.movements.out">Salida</ng-container>
                  }
                </td>
                <td>{{ money(movement.amount) }}</td>
                <td>
                  @if (movement.reasonCode) {
                    {{ label(movement.reasonCode) }}
                  }
                  @if (movement.note) {
                    {{ movement.note }}
                  }
                </td>
                <td>{{ movement.reference }}</td>
              </tr>
            }
          </tbody>
        </table>
      </app-table-scroll>
    }

    <form (submit)="submit($event)" [attr.aria-labelledby]="'movement-heading'">
      <h3 id="movement-heading" i18n="@@staff.register.movements.new">Registrar un movimiento</h3>
      <div class="field">
        <label for="mv-type" id="mv-type-label" i18n="@@staff.register.movements.typeLabel">Tipo</label>
        <p-select
          inputId="mv-type"
          [ariaLabelledBy]="'mv-type-label'"
          [formControl]="kind"
          optionLabel="label"
          optionValue="value"
          [options]="kinds"
          [filter]="true"
          filterBy="label"
          [resetFilterOnHide]="true"
          filterPlaceholder="Buscar"
          i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
          [appendTo]="'body'"
          fluid
        />
      </div>
      @if (chosenKind() === 'OUT') {
        <div class="field" data-testid="mv-reason-box">
          <label for="mv-reason" id="mv-reason-label" i18n="@@staff.register.movements.reason">Motivo</label>
          <p-select
            inputId="mv-reason"
            [ariaLabelledBy]="'mv-reason-label'"
            [formControl]="reason"
            optionLabel="label"
            optionValue="code"
            [options]="reasonOptions()"
            [filter]="true"
            filterBy="label"
            [resetFilterOnHide]="true"
            filterPlaceholder="Buscar"
            i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
            placeholder="Elige un motivo"
            i18n-placeholder="@@staff.register.movements.reason.placeholder"
            [appendTo]="'body'"
            fluid
          />
          @if (store.reasonsLoadFailed()) {
            <small class="error" data-testid="reasons-failed" i18n="@@staff.register.movements.reasonsFailed"
              >No se pudieron cargar los motivos.</small
            >
            <button
              pButton
              type="button"
              severity="secondary"
              data-testid="reasons-retry"
              pTooltip="Volver a cargar los motivos de salida"
              i18n-pTooltip="@@staff.register.movements.reasonsRetry.tooltip"
              tooltipPosition="top"
              (click)="store.loadReasons()"
            >
              <tabler-icon [icon]="icons.retry" aria-hidden="true" />
              <span i18n="@@staff.register.movements.reasonsRetry">Reintentar</span>
            </button>
          } @else if (store.reasons()?.length === 0) {
            <small class="muted" data-testid="no-reasons" i18n="@@staff.register.movements.noReasons"
              >No hay motivos de salida configurados. Pide a un administrador que los agregue.</small
            >
          }
        </div>
      }
      <div class="field">
        <label for="mv-amount" i18n="@@staff.register.movements.amountLabel">Monto</label>
        <input
          pInputText
          id="mv-amount"
          data-testid="mv-amount"
          [formControl]="amount"
          [attr.inputmode]="'decimal'"
          autocomplete="off"
          fluid
        />
      </div>
      <div class="field">
        <label for="mv-note" i18n="@@staff.register.movements.noteLabel">Nota</label>
        <textarea
          pTextarea
          id="mv-note"
          data-testid="mv-note"
          [formControl]="note"
          maxlength="280"
          rows="2"
          fluid
        ></textarea>
        @if (noteRequired()) {
          <small data-testid="note-required" i18n="@@staff.register.movements.noteRequired"
            >La nota es obligatoria para una entrada o para el motivo «Otro».</small
          >
        }
      </div>
      <div class="field">
        <label for="mv-reference" i18n="@@staff.register.movements.referenceLabel">Referencia (opcional)</label>
        <input
          pInputText
          id="mv-reference"
          data-testid="mv-reference"
          [formControl]="reference"
          maxlength="60"
          autocomplete="off"
          fluid
        />
      </div>
      <button
        pButton
        type="submit"
        data-testid="mv-submit"
        [loading]="recording()"
        [disabled]="!valid() || recording()"
        pTooltip="Registrar el movimiento de efectivo"
        i18n-pTooltip="@@staff.register.movements.submit.tooltip"
        tooltipPosition="top"
      >
        @if (!recording()) {
          <tabler-icon [icon]="icons.confirm" aria-hidden="true" />
        }
        <span i18n="@@staff.register.movements.submit">Registrar</span>
      </button>
    </form>
  `,
})
export class CashMovements {
  protected readonly store = inject(ShiftStore);

  protected readonly icons = { confirm: IconCheck, retry: IconRefresh };
  protected readonly money = formatMoney;
  protected readonly label = reasonLabel;
  protected readonly kinds: { value: MovementType; label: string }[] = [
    { value: 'OUT', label: $localize`:@@staff.register.movements.kind.out:Salida` },
    { value: 'IN', label: $localize`:@@staff.register.movements.kind.in:Entrada` },
  ];
  protected readonly kind = new FormControl<MovementType>('OUT', { nonNullable: true });
  protected readonly reason = new FormControl<string | null>(null);
  protected readonly amount = new FormControl('', { nonNullable: true });
  protected readonly note = new FormControl('', { nonNullable: true });
  protected readonly reference = new FormControl('', { nonNullable: true });

  protected readonly chosenKind = toSignal(this.kind.valueChanges, { initialValue: 'OUT' as MovementType });
  private readonly chosenReason = toSignal(this.reason.valueChanges, { initialValue: null });
  private readonly typedAmount = toSignal(this.amount.valueChanges, { initialValue: '' });
  private readonly typedNote = toSignal(this.note.valueChanges, { initialValue: '' });
  protected readonly reasonOptions = computed(() =>
    (this.store.reasons() ?? []).map((reason) => ({ code: reason.code, label: reasonLabel(reason.code) })),
  );
  protected readonly noteRequired = computed(
    () => this.chosenKind() === 'IN' || requiresNote(this.chosenReason()),
  );
  protected readonly recording = computed(() => this.store.pending.has('movement'));
  protected readonly valid = computed(() => {
    const amount = parseAmount(this.typedAmount());
    return (
      amount !== null &&
      amount !== '0.00' &&
      (this.chosenKind() === 'IN' || this.chosenReason() !== null) &&
      (!this.noteRequired() || this.typedNote().trim() !== '')
    );
  });

  constructor() {
    this.store.loadMovements();
    this.store.loadReasons();
  }

  protected submit(event: Event): void {
    event.preventDefault();
    const amount = parseAmount(this.amount.value);
    const currency = this.store.currency();
    if (!this.valid() || amount === null || currency === null || this.recording()) {
      return;
    }
    const note = this.note.value.trim();
    const reference = this.reference.value.trim();
    this.store.recordMovement(
      {
        type: this.kind.value,
        amount: { amount, currency },
        reasonCode: this.kind.value === 'OUT' ? this.reason.value : null,
        note: note === '' ? null : note,
        reference: reference === '' ? null : reference,
      },
      () => {
        this.reason.reset();
        this.amount.reset();
        this.note.reset();
        this.reference.reset();
      },
    );
  }
}
