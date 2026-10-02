import { AbstractControl } from '@angular/forms';
import type { ApiError } from './api-error';

// Maps a validation failure onto the form; returns the fields that had no control, for a general message.
export function applyFieldErrors(form: AbstractControl, error: ApiError): string[] {
  const unmatched: string[] = [];
  for (const { field, constraint } of error.fieldErrors) {
    // The API reports JSON paths (`lines[0].quantity`); forms address children as `lines.0.quantity`.
    const control = form.get(field.replace(/\[(\d+)\]/g, '.$1'));
    if (control) {
      control.setErrors({ server: { constraint } });
      control.markAsTouched();
    } else {
      unmatched.push(field);
    }
  }
  return unmatched;
}
