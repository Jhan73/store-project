import { AbstractControl, ValidationErrors } from '@angular/forms';
import { parseAmount } from '../../core/money/money';

export function amountValidator(control: AbstractControl): ValidationErrors | null {
  return parseAmount(String(control.value ?? '')) === null ? { amount: true } : null;
}

// For an amount the person may leave blank; anything typed must still be an amount.
export function optionalAmountValidator(control: AbstractControl): ValidationErrors | null {
  const text = String(control.value ?? '').trim();
  return text === '' || parseAmount(text) !== null ? null : { amount: true };
}
