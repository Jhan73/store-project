import { AbstractControl, ValidationErrors } from '@angular/forms';
import { parseAmount } from '../../../core/money/money';

export function amountValidator(control: AbstractControl): ValidationErrors | null {
  return parseAmount(String(control.value ?? '')) === null ? { amount: true } : null;
}
