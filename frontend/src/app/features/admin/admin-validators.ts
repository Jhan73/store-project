import { ValidatorFn, Validators } from '@angular/forms';

// A whole number of at least `min`; `type="number"` alone lets "1.5" and "1e2" through.
export function wholeNumber(min: number): ValidatorFn[] {
  return [Validators.required, Validators.min(min), Validators.pattern(/^\d+$/)];
}
