import { ChangeDetectionStrategy, Component, DestroyRef, forwardRef, inject, input, signal } from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
} from '@angular/forms';
import { InputText } from 'primeng/inputtext';
import { amountValidator, optionalAmountValidator } from '../../forms/amount-validator';

// The value is the typed text; the owner turns it into the API amount with `parseAmount`.
@Component({
  selector: 'app-money-input',
  imports: [InputText],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './money-input.scss',
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => MoneyInput), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => MoneyInput), multi: true },
  ],
  template: `
    <input
      pInputText
      type="text"
      autocomplete="off"
      [id]="inputId()"
      [attr.inputmode]="'decimal'"
      [attr.aria-invalid]="showInvalid()"
      [attr.placeholder]="placeholder()"
      [value]="text()"
      [disabled]="disabled()"
      (input)="typed($event)"
      (blur)="touch()"
      fluid
    />
    @if (currency(); as code) {
      <span class="suffix" aria-hidden="true">{{ code }}</span>
    }
  `,
})
export class MoneyInput implements ControlValueAccessor, Validator {
  readonly inputId = input.required<string>();
  readonly currency = input<string | null>(null);
  readonly optional = input(false);
  readonly placeholder = input<string | null>(null);

  protected readonly text = signal('');
  protected readonly disabled = signal(false);
  protected readonly showInvalid = signal(false);

  private onChange: (value: string) => void = () => undefined;
  private onTouched: () => void = () => undefined;
  private control: AbstractControl | null = null;
  private readonly destroyRef = inject(DestroyRef);

  writeValue(value: string | null): void {
    this.text.set(value ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabled.set(disabled);
  }

  validate(control: AbstractControl): ValidationErrors | null {
    if (this.control !== control) {
      this.control = control;
      // Touched and status changes made by the owner (markAllAsTouched, reset) never pass through this input.
      const subscription = control.events.subscribe(() => this.refresh());
      this.destroyRef.onDestroy(() => subscription.unsubscribe());
    }
    return this.optional() ? optionalAmountValidator(control) : amountValidator(control);
  }

  protected typed(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.text.set(value);
    this.onChange(value);
    this.refresh();
  }

  protected touch(): void {
    this.onTouched();
    this.refresh();
  }

  private refresh(): void {
    const control = this.control;
    this.showInvalid.set(!!control && control.invalid && control.touched);
  }
}
