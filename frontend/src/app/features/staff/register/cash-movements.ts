import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { IconPlus, IconRefresh, TablerIconComponent } from '@tabler/icons-angular';
import { map } from 'rxjs';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import type { CashMovement, CashMovementRequest } from '../../../core/api/api-types';
import { formatMoneyOrDash, parseAmount, safeMinorUnits } from '../../../core/money/money';
import { reasonLabel, requiresNote } from '../../../core/reasons/reason-rules';
import { DataColumn, DataTable } from '../../../shared/ui/data-table/data-table';
import { FormDialog } from '../../../shared/ui/form-dialog/form-dialog';
import { FormField } from '../../../shared/ui/form-field/form-field';
import { MoneyInput } from '../../../shared/ui/money-input/money-input';
import { StatusTag } from '../../../shared/ui/status-tag/status-tag';
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
    DataTable,
    FormDialog,
    FormField,
    MoneyInput,
    StatusTag,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './register-panel.scss',
  template: `
    <div class="toolbar">
      <h2 i18n="@@staff.register.movements.title">Entradas y salidas de efectivo</h2>
      <button
        pButton
        type="button"
        data-testid="mv-open"
        [disabled]="recording()"
        pTooltip="Registrar una entrada o una salida de efectivo"
        i18n-pTooltip="@@staff.register.movements.new.tooltip"
        tooltipPosition="top"
        (click)="add()"
      >
        <tabler-icon [icon]="icons.add" aria-hidden="true" />
        <span i18n="@@staff.register.movements.new">Registrar un movimiento</span>
      </button>
    </div>

    <app-data-table
      label="Movimientos de efectivo"
      i18n-label="@@staff.register.movements.scroll"
      [value]="rowsView()"
      [columns]="columns"
      [emptyText]="emptyText"
    >
      <ng-template #row let-movement>
        <td>{{ movement.timeText }}</td>
        <td>
          <app-status-tag
            [value]="movement.typeLabel"
            [severity]="movement.type === 'IN' ? 'success' : 'secondary'"
          />
        </td>
        <td>{{ movement.amountText }}</td>
        <td>{{ movement.detail }}</td>
        <td>{{ movement.reference }}</td>
      </ng-template>
    </app-data-table>

    <app-form-dialog
      [header]="labels.header"
      [visible]="open()"
      [busy]="recording()"
      [saveDisabled]="!valid()"
      [saveLabel]="labels.save"
      [saveTooltip]="labels.saveTooltip"
      [disabledReason]="whyDisabled()"
      (saved)="submit()"
      (dismissed)="close()"
    >
      <div [formGroup]="form" class="dialog-fields">
        <app-form-field label="Tipo" i18n-label="@@staff.register.movements.typeLabel" controlId="mv-type">
          <p-select
            inputId="mv-type"
            [ariaLabel]="labels.type"
            formControlName="kind"
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
        </app-form-field>
        @if (chosenKind() === 'OUT') {
          <div class="dialog-fields" data-testid="mv-reason-box">
            <app-form-field
              label="Motivo"
              i18n-label="@@staff.register.movements.reason"
              controlId="mv-reason"
              [error]="store.reasonsLoadFailed() ? labels.reasonsFailed : null"
              [hint]="store.reasons()?.length === 0 ? labels.noReasons : null"
            >
              <p-select
                inputId="mv-reason"
                [ariaLabel]="labels.reason"
                formControlName="reason"
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
            </app-form-field>
            @if (store.reasonsLoadFailed()) {
              <div>
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
              </div>
            }
          </div>
        }
        <app-form-field
          label="Monto"
          i18n-label="@@staff.register.movements.amountLabel"
          controlId="mv-amount"
          [error]="amountError() ? labels.amountInvalid : null"
        >
          <app-money-input inputId="mv-amount" formControlName="amount" [currency]="store.currency()" />
        </app-form-field>
        <app-form-field
          label="Nota"
          i18n-label="@@staff.register.movements.noteLabel"
          controlId="mv-note"
          [hint]="noteRequired() ? labels.noteRequired : null"
        >
          <textarea
            pTextarea
            id="mv-note"
            data-testid="mv-note"
            formControlName="note"
            maxlength="280"
            fluid
          ></textarea>
        </app-form-field>
        <app-form-field
          label="Referencia (opcional)"
          i18n-label="@@staff.register.movements.referenceLabel"
          controlId="mv-reference"
        >
          <input
            pInputText
            id="mv-reference"
            data-testid="mv-reference"
            formControlName="reference"
            maxlength="60"
            autocomplete="off"
            fluid
          />
        </app-form-field>
      </div>
    </app-form-dialog>
  `,
})
export class CashMovements {
  protected readonly store = inject(ShiftStore);

  protected readonly icons = { add: IconPlus, retry: IconRefresh };
  protected readonly labels = {
    header: $localize`:@@staff.register.movements.dialog:Registrar un movimiento`,
    save: $localize`:@@staff.register.movements.submit:Registrar`,
    saveTooltip: $localize`:@@staff.register.movements.submit.tooltip:Registrar el movimiento de efectivo`,
    type: $localize`:@@staff.register.movements.typeAria:Tipo de movimiento`,
    reason: $localize`:@@staff.register.movements.reasonAria:Motivo de la salida`,
    reasonsFailed: $localize`:@@staff.register.movements.reasonsFailed:No se pudieron cargar los motivos.`,
    noReasons: $localize`:@@staff.register.movements.noReasons:No hay motivos de salida configurados. Pide a un administrador que los agregue.`,
    amountInvalid: $localize`:@@staff.register.movements.amountInvalid:Escribe un monto, como 5 o 5.00.`,
    noteRequired: $localize`:@@staff.register.movements.noteRequired:La nota es obligatoria para una entrada o para el motivo «Otro».`,
    in: $localize`:@@staff.register.movements.in:Entrada`,
    out: $localize`:@@staff.register.movements.out:Salida`,
  };
  protected readonly emptyText = $localize`:@@staff.register.movements.empty:Este turno todavía no tiene movimientos de efectivo.`;
  protected readonly columns: readonly DataColumn[] = [
    {
      field: 'recordedAt',
      header: $localize`:@@staff.register.movements.time:Hora`,
      sortable: true,
      searchable: false,
    },
    { field: 'typeLabel', header: $localize`:@@staff.register.movements.type:Tipo`, sortable: true },
    {
      field: 'amountMinor',
      header: $localize`:@@staff.register.movements.amount:Monto`,
      sortable: true,
      searchable: false,
    },
    { field: 'detail', header: $localize`:@@staff.register.movements.detail:Detalle` },
    { field: 'reference', header: $localize`:@@staff.register.movements.reference:Referencia` },
  ];
  protected readonly kinds: { value: MovementType; label: string }[] = [
    { value: 'OUT', label: $localize`:@@staff.register.movements.kind.out:Salida` },
    { value: 'IN', label: $localize`:@@staff.register.movements.kind.in:Entrada` },
  ];
  protected readonly form = new FormGroup({
    kind: new FormControl<MovementType>('OUT', { nonNullable: true }),
    reason: new FormControl<string | null>(null),
    amount: new FormControl('', { nonNullable: true }),
    note: new FormControl('', { nonNullable: true }),
    reference: new FormControl('', { nonNullable: true }),
  });
  protected readonly open = signal(false);

  private readonly values = toSignal(
    this.form.valueChanges.pipe(map(() => this.form.getRawValue())),
    { initialValue: this.form.getRawValue() },
  );
  protected readonly chosenKind = computed(() => this.values().kind);
  protected readonly amountError = toSignal(
    this.form.controls.amount.events.pipe(
      map(() => this.form.controls.amount.invalid && this.form.controls.amount.touched),
    ),
    { initialValue: false },
  );
  protected readonly reasonOptions = computed(() =>
    (this.store.reasons() ?? []).map((reason) => ({ code: reason.code, label: reasonLabel(reason.code) })),
  );
  protected readonly noteRequired = computed(
    () => this.values().kind === 'IN' || requiresNote(this.values().reason),
  );
  protected readonly rowsView = computed(() =>
    this.store.movements().map((movement: CashMovement) => ({
      ...movement,
      timeText: this.store.formatTime(movement.recordedAt),
      typeLabel: movement.type === 'IN' ? this.labels.in : this.labels.out,
      amountText: formatMoneyOrDash(movement.amount),
      amountMinor: safeMinorUnits(movement.amount) ?? 0,
      detail: [movement.reasonCode ? reasonLabel(movement.reasonCode) : '', movement.note ?? '']
        .filter((part) => part !== '')
        .join(' '),
      reference: movement.reference ?? '',
    })),
  );
  protected readonly recording = computed(() => this.store.pending.has('movement'));
  protected readonly whyDisabled = computed(() => {
    const value = this.values();
    const amount = parseAmount(value.amount);
    if (amount === null || amount === '0.00') {
      return $localize`:@@staff.register.movements.why.amount:Escribe un monto mayor que cero.`;
    }
    if (value.kind === 'OUT' && value.reason === null) {
      return $localize`:@@staff.register.movements.why.reason:Elige el motivo de la salida.`;
    }
    if (this.noteRequired() && value.note.trim() === '') {
      return $localize`:@@staff.register.movements.why.note:Escribe la nota para registrar el movimiento.`;
    }
    return this.store.currency() === null
      ? $localize`:@@staff.register.movements.why.currency:Todavía no se conoce la moneda de la tienda; vuelve a abrir la pantalla.`
      : null;
  });
  protected readonly valid = computed(() => this.whyDisabled() === null);

  constructor() {
    this.store.loadMovements();
    this.store.loadReasons();
  }

  protected add(): void {
    this.reset();
    this.open.set(true);
  }

  protected close(): void {
    this.open.set(false);
    this.reset();
  }

  protected submit(): void {
    const value = this.form.getRawValue();
    const amount = parseAmount(value.amount);
    const currency = this.store.currency();
    if (!this.valid() || amount === null || currency === null || this.recording()) {
      this.form.markAllAsTouched();
      return;
    }
    const note = value.note.trim();
    const reference = value.reference.trim();
    this.store.recordMovement(
      {
        type: value.kind,
        amount: { amount, currency },
        reasonCode: value.kind === 'OUT' ? value.reason : null,
        note: note === '' ? null : note,
        reference: reference === '' ? null : reference,
      },
      () => this.close(),
    );
  }

  private reset(): void {
    this.form.reset({ kind: 'OUT', reason: null, amount: '', note: '', reference: '' });
  }
}
