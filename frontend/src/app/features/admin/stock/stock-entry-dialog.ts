import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { IconDeviceFloppy, IconX, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputNumber } from 'primeng/inputnumber';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import type { StockItem } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { numberAriaInvalid } from '../../../shared/forms/aria-invalid';
import { IntentKeys } from '../../../shared/state/intent-keys';
import { reportFailure } from '../admin-errors';
import { wholeNumber } from '../admin-validators';
import { StockApi } from './stock-api';

const MAX_QUANTITY = 1_000_000;
const ENTRY_INTENT = 'entry';

// Adds purchased units to a tracked product.
@Component({
  selector: 'app-stock-entry-dialog',
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    Dialog,
    InputNumber,
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

    .error {
      color: var(--destructive);
    }
  `,
  template: `
    <p-dialog
      header="Registrar entrada"
      i18n-header="@@admin.stock.entry.title"
      [modal]="true"
      [draggable]="false"
      [style]="{ width: 'min(30rem, calc(100vw - 1rem))' }"
      [visible]="visible()"
      (visibleChange)="visibleChange($event)"
    >
      <strong data-testid="entry-product">{{ productName() }}</strong>
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="field">
          <label for="entry-quantity" i18n="@@admin.stock.entry.quantity">Unidades que entran</label>
          <p-inputnumber
            inputId="entry-quantity"
            formControlName="quantity"
            [useGrouping]="false"
            [invalid]="invalid('quantity')"
            [pt]="numberAriaInvalid(invalid('quantity'))"
          />
          @if (invalid('quantity')) {
            <small class="error" i18n="@@admin.stock.entry.quantity.invalid">
              Escribe un número entero entre 1 y 1000000.
            </small>
          }
        </div>
        <div class="field">
          <label for="entry-note" i18n="@@admin.stock.entry.note">Nota (opcional)</label>
          <textarea
            pTextarea
            id="entry-note"
            data-testid="entry-note"
            formControlName="note"
            maxlength="300"
            rows="3"
            fluid
          ></textarea>
        </div>
      </form>
      <ng-template #footer>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="entry-cancel"
          (click)="dismissed.emit()"
          pTooltip="Cerrar sin registrar la entrada"
          i18n-pTooltip="@@admin.stock.entry.cancel.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.cancel" aria-hidden="true" />
          <span i18n="@@admin.stock.entry.cancel">Cancelar</span>
        </button>
        <button
          pButton
          type="button"
          data-testid="entry-save"
          [loading]="saving()"
          (click)="submit()"
          pTooltip="Sumar estas unidades al stock del producto"
          i18n-pTooltip="@@admin.stock.entry.save.tooltip"
          tooltipPosition="top"
        >
          @if (!saving()) {
            <tabler-icon [icon]="icons.save" aria-hidden="true" />
          }
          <span i18n="@@admin.stock.entry.save">Registrar entrada</span>
        </button>
      </ng-template>
    </p-dialog>
  `,
})
export class StockEntryDialog {
  readonly visible = input.required<boolean>();
  readonly item = input<StockItem | null>(null);
  readonly productName = input('');

  readonly saved = output<StockItem>();
  readonly dismissed = output<void>();

  private readonly api = inject(StockApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly destroyRef = inject(DestroyRef);
  private readonly keys = new IntentKeys();

  protected readonly icons = { save: IconDeviceFloppy, cancel: IconX };
  protected readonly numberAriaInvalid = numberAriaInvalid;
  protected readonly saving = signal(false);
  protected readonly form = new FormGroup({
    quantity: new FormControl<number | null>(null, [...wholeNumber(1), Validators.max(MAX_QUANTITY)]),
    note: new FormControl('', { nonNullable: true }),
  });

  constructor() {
    // An edited entry is a new intent; only an unchanged retry reuses the key.
    this.form.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.keys.release(ENTRY_INTENT));
    effect(() => {
      if (this.visible()) {
        untracked(() => {
          this.saving.set(false);
          this.form.reset({ quantity: null, note: '' });
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

  protected submit(): void {
    const target = this.item();
    if (this.saving() || !target) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { quantity, note } = this.form.getRawValue();
    const text = note.trim();
    this.saving.set(true);
    this.api
      .recordEntry(
        target.productId,
        { quantity: quantity ?? 0, note: text === '' ? null : text },
        this.keys.keyFor(ENTRY_INTENT),
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (saved) => {
          this.keys.release(ENTRY_INTENT);
          this.saving.set(false);
          this.saved.emit(saved);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          reportFailure(this.notifier, error, this.form);
        },
      });
  }
}
