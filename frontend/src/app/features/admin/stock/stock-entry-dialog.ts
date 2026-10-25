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
import { InputNumber } from 'primeng/inputnumber';
import { Textarea } from 'primeng/textarea';
import type { StockItem } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { numberAriaInvalid } from '../../../shared/forms/aria-invalid';
import { IntentKeys } from '../../../shared/state/intent-keys';
import { FormDialog } from '../../../shared/ui/form-dialog/form-dialog';
import { FormField } from '../../../shared/ui/form-field/form-field';
import { reportFailure } from '../admin-errors';
import { wholeNumber } from '../admin-validators';
import { StockApi } from './stock-api';

const MAX_QUANTITY = 1_000_000;
const ENTRY_INTENT = 'entry';

// Adds purchased units to a tracked product.
@Component({
  selector: 'app-stock-entry-dialog',
  imports: [ReactiveFormsModule, FormDialog, FormField, InputNumber, Textarea],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .dialog-fields {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
      margin-top: var(--space-3);
    }
  `,
  template: `
    <app-form-dialog
      header="Registrar entrada"
      i18n-header="@@admin.stock.entry.title"
      width="30rem"
      [visible]="visible()"
      [busy]="saving()"
      (saved)="submit()"
      (dismissed)="dismissed.emit()"
    >
      <strong data-testid="entry-product">{{ productName() }}</strong>
      <div [formGroup]="form" class="dialog-fields">
        <app-form-field
          label="Unidades que entran"
          i18n-label="@@admin.stock.entry.quantity"
          controlId="entry-quantity"
          [error]="invalid('quantity') ? labels.quantityInvalid : null"
        >
          <p-inputnumber
            inputId="entry-quantity"
            formControlName="quantity"
            [useGrouping]="false"
            [invalid]="invalid('quantity')"
            [pt]="numberAriaInvalid(invalid('quantity'))"
            fluid
          />
        </app-form-field>
        <app-form-field
          label="Nota (opcional)"
          i18n-label="@@admin.stock.entry.note"
          controlId="entry-note"
        >
          <textarea
            pTextarea
            id="entry-note"
            data-testid="entry-note"
            formControlName="note"
            maxlength="300"
            fluid
          ></textarea>
        </app-form-field>
      </div>
    </app-form-dialog>
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

  protected readonly labels = {
    quantityInvalid: $localize`:@@admin.stock.entry.quantity.invalid:Escribe un número entero entre 1 y 1000000.`,
  };
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
