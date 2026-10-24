import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR } from '@angular/forms';
import { DatePicker } from 'primeng/datepicker';
import { datePickerAriaInvalid } from '../../forms/aria-invalid';

const TIME = /^([01]\d|2[0-3]):([0-5]\d)$/;

// The calendar day is irrelevant; only the wall-clock hours and minutes are read back.
export function fromTimeText(text: string): Date | null {
  const parts = TIME.exec(text);
  return parts ? new Date(2000, 0, 1, Number(parts[1]), Number(parts[2])) : null;
}

export function toTimeText(date: Date | null): string {
  if (!date) {
    return '';
  }
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

// The value is store-local `HH:mm` text, never a Date, so it is never shifted by a time zone.
@Component({
  selector: 'app-time-input',
  imports: [DatePicker, FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TimeInput), multi: true }],
  template: `
    <p-datepicker
      [inputId]="inputId()"
      [timeOnly]="true"
      hourFormat="24"
      [ngModel]="date()"
      [disabled]="disabled()"
      [invalid]="invalid()"
      [pt]="ariaInvalid(invalid())"
      (ngModelChange)="changed($event)"
      (onBlur)="onTouched()"
      fluid
    />
  `,
})
export class TimeInput implements ControlValueAccessor {
  readonly inputId = input.required<string>();
  readonly invalid = input(false);

  protected readonly ariaInvalid = datePickerAriaInvalid;
  protected readonly date = signal<Date | null>(null);
  protected readonly disabled = signal(false);

  private onChange: (value: string) => void = () => undefined;
  protected onTouched: () => void = () => undefined;

  writeValue(value: string | null): void {
    this.date.set(fromTimeText(value ?? ''));
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

  protected changed(value: Date | null): void {
    this.date.set(value);
    this.onChange(toTimeText(value));
  }
}
