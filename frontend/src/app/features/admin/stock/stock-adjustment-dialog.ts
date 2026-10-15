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
import { IconDeviceFloppy, IconRefresh, IconX, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
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
    Dialog,
    InputNumber,
    Select,
    TablerIconComponent,
    Textarea,
    Tooltip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
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
    <p-dialog
      header="Ajustar stock"
      i18n-header="@@admin.stock.adjust.title"
      [modal]="true"
      [draggable]="false"
      [style]="{ width: 'min(30rem, calc(100vw - 1rem))' }"
      [visible]="visible()"
      (visibleChange)="visibleChange($event)"
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
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <div class="field">
            <label for="adjust-delta" i18n="@@admin.stock.adjust.delta">Cambio de unidades</label>
            <p-inputnumber
              inputId="adjust-delta"
              formControlName="quantityDelta"
              [useGrouping]="false"
              [invalid]="invalid('quantityDelta')"
              [pt]="numberAriaInvalid(invalid('quantityDelta'))"
            />
            <small class="muted" i18n="@@admin.stock.adjust.delta.hint">
              Un número negativo descuenta unidades (por ejemplo -3) y uno positivo las suma.
            </small>
            @if (invalid('quantityDelta')) {
              <small class="error" i18n="@@admin.stock.adjust.delta.invalid">
                Escribe un número entero distinto de cero.
              </small>
            }
          </div>
          <div class="field">
            <label for="adjust-reason" id="adjust-reason-label" i18n="@@admin.stock.adjust.reason">Motivo</label>
            <p-select
              inputId="adjust-reason"
              [ariaLabelledBy]="'adjust-reason-label'"
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
            @if (invalid('reason')) {
              <small class="error" i18n="@@admin.stock.adjust.reason.invalid">Elige un motivo.</small>
            }
          </div>
          <div class="field">
            <label for="adjust-note" i18n="@@admin.stock.adjust.note">Nota</label>
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
            @if (noteRequired()) {
              <small [class.error]="invalid('note')" data-testid="adjust-note-required" i18n="@@admin.stock.adjust.noteRequired">
                Para el motivo «Otro» la nota es obligatoria.
              </small>
            }
          </div>
          @if (available() !== null) {
            <small class="error" role="status" data-testid="adjust-available">
              <ng-container i18n="@@admin.stock.adjust.available">
                Disponible ahora: {{ available() }}
              </ng-container>
            </small>
          }
        </form>
      }
      <ng-template #footer>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="adjust-cancel"
          (click)="dismissed.emit()"
          pTooltip="Cerrar sin ajustar el stock"
          i18n-pTooltip="@@admin.stock.adjust.cancel.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.cancel" aria-hidden="true" />
          <span i18n="@@admin.stock.adjust.cancel">Cancelar</span>
        </button>
        <button
          pButton
          type="button"
          data-testid="adjust-save"
          [loading]="saving()"
          [disabled]="reasons() === null || reasons()!.length === 0"
          (click)="submit()"
          pTooltip="Aplicar este cambio de unidades al stock"
          i18n-pTooltip="@@admin.stock.adjust.save.tooltip"
          tooltipPosition="top"
        >
          @if (!saving()) {
            <tabler-icon [icon]="icons.save" aria-hidden="true" />
          }
          <span i18n="@@admin.stock.adjust.save">Ajustar stock</span>
        </button>
      </ng-template>
    </p-dialog>
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

  protected readonly icons = { save: IconDeviceFloppy, cancel: IconX, retry: IconRefresh };
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

  protected visibleChange(visible: boolean): void {
    if (!visible) {
      this.dismissed.emit();
    }
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
