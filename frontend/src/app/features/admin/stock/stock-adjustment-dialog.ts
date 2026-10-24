import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { IconRefresh, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { InputNumber } from 'primeng/inputnumber';
import { Select } from 'primeng/select';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import type { ActiveReason, StockItem } from '../../../core/api/api-types';
import { ApiError } from '../../../core/errors/api-error';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { reasonLabel, requiresNote } from '../../../core/reasons/reason-rules';
import { ReasonsApi } from '../../../core/reasons/reasons-api';
import { numberAriaInvalid, selectAriaInvalid } from '../../../shared/forms/aria-invalid';
import { IntentKeys } from '../../../shared/state/intent-keys';
import { FormDialog } from '../../../shared/ui/form-dialog/form-dialog';
import { FormField } from '../../../shared/ui/form-field/form-field';
import { reportFailure } from '../admin-errors';
import { StockApi } from './stock-api';

const MAX_QUANTITY = 1_000_000;
const ADJUST_INTENT = 'adjustment';

function wholeNonZero(control: AbstractControl<number | null>): ValidationErrors | null {
  const value = control.value;
  return value !== null && Number.isInteger(value) && value !== 0 ? null : { nonZero: true };
}

// Corrects a tracked product's quantity by a signed amount, for a reason from the store's list.
@Component({
  selector: 'app-stock-adjustment-dialog',
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    FormDialog,
    FormField,
    InputNumber,
    Select,
    TablerIconComponent,
    Textarea,
    Tooltip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .dialog-fields {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
      margin-top: var(--space-3);
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: var(--space-1);
      margin-top: var(--space-3);
    }

    p {
      margin: 0;
    }

    .muted {
      color: var(--muted-foreground);
    }

    .error {
      color: var(--destructive);
    }
  `,
  template: `
    <app-form-dialog
      header="Ajustar stock"
      i18n-header="@@admin.stock.adjust.title"
      width="30rem"
      [visible]="visible()"
      [busy]="saving()"
      [saveDisabled]="reasons() === null || reasons()!.length === 0"
      (saved)="submit()"
      (dismissed)="dismissed.emit()"
    >
      <strong data-testid="adjust-product">{{ productName() }}</strong>
      @if (failed()) {
        <div class="field" role="alert" data-testid="adjust-failed">
          <p i18n="@@admin.stock.adjust.failed">No se pudieron cargar los motivos.</p>
          <button
            pButton
            type="button"
            severity="secondary"
            data-testid="adjust-retry"
            (click)="loadReasons()"
            pTooltip="Volver a cargar los motivos de ajuste"
            i18n-pTooltip="@@admin.stock.adjust.retry.tooltip"
            tooltipPosition="top"
          >
            <tabler-icon [icon]="icons.retry" aria-hidden="true" />
            <span i18n="@@admin.stock.adjust.retry">Reintentar</span>
          </button>
        </div>
      } @else if (reasons() === null) {
        <p class="muted" i18n="@@admin.stock.adjust.loading">Cargando los motivos…</p>
      } @else {
        @if (reasons()!.length === 0) {
          <p data-testid="adjust-no-reasons" i18n="@@admin.stock.adjust.noReasons">
            No hay motivos de ajuste configurados. Agrégalos en Configuración, en motivos.
          </p>
        }
        <div [formGroup]="form" class="dialog-fields">
          <app-form-field
            label="Cambio de unidades"
            i18n-label="@@admin.stock.adjust.delta"
            controlId="adjust-delta"
            [error]="invalid('quantityDelta') ? labels.deltaInvalid : null"
            [hint]="labels.deltaHint"
          >
            <p-inputnumber
              inputId="adjust-delta"
              formControlName="quantityDelta"
              [useGrouping]="false"
              [invalid]="invalid('quantityDelta')"
              [pt]="numberAriaInvalid(invalid('quantityDelta'))"
              fluid
            />
          </app-form-field>
          <app-form-field
            label="Motivo"
            i18n-label="@@admin.stock.adjust.reason"
            controlId="adjust-reason"
            [error]="invalid('reason') ? labels.reasonInvalid : null"
          >
            <p-select
              inputId="adjust-reason"
              [ariaLabel]="labels.reason"
              formControlName="reason"
              optionLabel="label"
              optionValue="code"
              [options]="options()"
              [filter]="true"
              filterBy="label"
              [resetFilterOnHide]="true"
              filterPlaceholder="Buscar"
              i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
              placeholder="Elige un motivo"
              i18n-placeholder="@@admin.stock.adjust.reason.placeholder"
              [invalid]="invalid('reason')"
              [pt]="selectAriaInvalid(invalid('reason'))"
              [appendTo]="'body'"
              fluid
            />
          </app-form-field>
          <app-form-field
            label="Nota"
            i18n-label="@@admin.stock.adjust.note"
            controlId="adjust-note"
            [error]="invalid('note') ? labels.noteRequired : null"
            [hint]="noteRequired() ? labels.noteRequired : null"
          >
            <textarea
              pTextarea
              id="adjust-note"
              data-testid="adjust-note"
              formControlName="note"
              maxlength="300"
              rows="3"
              [attr.aria-invalid]="invalid('note') ? 'true' : null"
              fluid
            ></textarea>
          </app-form-field>
          @if (shownAvailable() !== null) {
            <small [class.error]="available() !== null" role="status" data-testid="adjust-available">
              <ng-container i18n="@@admin.stock.adjust.available">
                Disponible ahora: {{ shownAvailable() }}
              </ng-container>
            </small>
          }
          @if (change(); as current) {
            <small class="muted" role="status" data-testid="adjust-effect">
              @if (current.adds) {
                <ng-container i18n="@@admin.stock.adjust.effect.add">
                  {current.units, plural, =1 {Se sumará 1 unidad} other {Se sumarán {{ current.units }} unidades}}
                </ng-container>
              } @else {
                <ng-container i18n="@@admin.stock.adjust.effect.remove">
                  {current.units, plural, =1 {Se descontará 1 unidad} other {Se descontarán {{ current.units }} unidades}}
                </ng-container>
              }
            </small>
          }
        </div>
      }
    </app-form-dialog>
  `,
})
export class StockAdjustmentDialog {
  readonly visible = input.required<boolean>();
  readonly item = input<StockItem | null>(null);
  readonly productName = input('');

  readonly saved = output<StockItem>();
  // The stock moved under the dialog: the owner should re-read it.
  readonly stale = output<void>();
  readonly dismissed = output<void>();

  private readonly api = inject(StockApi);
  private readonly reasonsApi = inject(ReasonsApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly destroyRef = inject(DestroyRef);
  private readonly keys = new IntentKeys();

  protected readonly icons = { retry: IconRefresh };
  protected readonly labels = {
    deltaHint: $localize`:@@admin.stock.adjust.delta.hint:Un número negativo descuenta unidades (por ejemplo -3) y uno positivo las suma.`,
    deltaInvalid: $localize`:@@admin.stock.adjust.delta.invalid:Escribe un número entero distinto de cero.`,
    reason: $localize`:@@admin.stock.adjust.reason:Motivo`,
    reasonInvalid: $localize`:@@admin.stock.adjust.reason.invalid:Elige un motivo.`,
    noteRequired: $localize`:@@admin.stock.adjust.noteRequired:Para el motivo «Otro» la nota es obligatoria.`,
  };
  protected readonly selectAriaInvalid = selectAriaInvalid;
  protected readonly numberAriaInvalid = numberAriaInvalid;
  protected readonly saving = signal(false);
  protected readonly failed = signal(false);
  protected readonly reasons = signal<readonly ActiveReason[] | null>(null);
  protected readonly available = signal<number | null>(null);

  private readonly reason = new FormControl<string | null>(null, Validators.required);
  protected readonly form = new FormGroup({
    quantityDelta: new FormControl<number | null>(null, [wholeNonZero, Validators.min(-MAX_QUANTITY), Validators.max(MAX_QUANTITY)]),
    reason: this.reason,
    note: new FormControl('', {
      nonNullable: true,
      validators: (control) =>
        requiresNote(this.reason.value) && control.value.trim() === '' ? { required: true } : null,
    }),
  });
  private readonly chosen = toSignal(this.reason.valueChanges, { initialValue: null });
  private readonly typedDelta = toSignal(this.form.controls.quantityDelta.valueChanges, {
    initialValue: null,
  });
  // Display aid only: the API decides whether the change is allowed.
  protected readonly change = computed(() => {
    const delta = this.typedDelta();
    const valid = delta !== null && Number.isInteger(delta) && delta !== 0 && Math.abs(delta) <= MAX_QUANTITY;
    return valid ? { adds: delta > 0, units: Math.abs(delta) } : null;
  });
  protected readonly shownAvailable = computed(() => this.available() ?? this.item()?.available ?? null);
  protected readonly noteRequired = computed(() => requiresNote(this.chosen()));
  protected readonly options = computed(() =>
    (this.reasons() ?? []).map((reason) => ({ code: reason.code, label: reasonLabel(reason.code) })),
  );

  constructor() {
    // An edited adjustment is a new intent; only an unchanged retry reuses the key.
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.keys.release(ADJUST_INTENT));
    this.reason.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.form.controls.note.updateValueAndValidity({ emitEvent: false }));
    effect(() => {
      if (this.visible()) {
        untracked(() => {
          this.saving.set(false);
          this.available.set(null);
          this.form.reset({ quantityDelta: null, reason: null, note: '' });
          this.loadReasons();
        });
      }
    });
  }

  protected invalid(name: string): boolean {
    const control = this.form.get(name);
    return !!control && control.invalid && control.touched;
  }

  protected loadReasons(): void {
    this.failed.set(false);
    this.reasonsApi
      .active('STOCK_ADJUSTMENT')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (list) => this.reasons.set(list),
        error: (error: unknown) => {
          this.failed.set(true);
          this.notifier.show(error);
        },
      });
  }

  protected submit(): void {
    const target = this.item();
    if (this.saving() || !target) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { quantityDelta, reason, note } = this.form.getRawValue();
    const text = note.trim();
    this.saving.set(true);
    this.available.set(null);
    this.api
      .adjust(
        target.productId,
        { quantityDelta: quantityDelta ?? 0, reason: reason ?? '', note: text === '' ? null : text },
        this.keys.keyFor(ADJUST_INTENT),
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (saved) => {
          this.keys.release(ADJUST_INTENT);
          this.saving.set(false);
          this.saved.emit(saved);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          reportFailure(this.notifier, error, this.form);
          if (error instanceof ApiError && error.code === 'inventory.insufficient-stock') {
            const available = error.properties['available'];
            this.available.set(typeof available === 'number' ? available : null);
            this.stale.emit();
          }
        },
      });
  }
}
