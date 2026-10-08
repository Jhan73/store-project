import type { ValidationErrors, ValidatorFn } from '@angular/forms';

export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 50;
// bcrypt cannot hash more than 72 bytes.
export const PASSWORD_MAX_BYTES = 72;

export type PasswordRule =
  | 'length'
  | 'bytes'
  | 'lowercase'
  | 'uppercase'
  | 'digit'
  | 'special'
  | 'lineBreak';

// The backend pattern's `.` does not match these, so it rejects the whole password.
const LINE_SEPARATORS = /[\n\r\u0085\u2028\u2029]/;

// Mirrors the backend rule for fast feedback only; ASCII classes, and anything else (space, accents) is "special".
export function passwordViolations(value: string): PasswordRule[] {
  const violations: PasswordRule[] = [];
  if (value.length < PASSWORD_MIN_LENGTH || value.length > PASSWORD_MAX_LENGTH) {
    violations.push('length');
  }
  if (new TextEncoder().encode(value).length > PASSWORD_MAX_BYTES) {
    violations.push('bytes');
  }
  if (!/[a-z]/.test(value)) {
    violations.push('lowercase');
  }
  if (!/[A-Z]/.test(value)) {
    violations.push('uppercase');
  }
  if (!/[0-9]/.test(value)) {
    violations.push('digit');
  }
  if (!/[^A-Za-z0-9]/.test(value)) {
    violations.push('special');
  }
  if (LINE_SEPARATORS.test(value)) {
    violations.push('lineBreak');
  }
  return violations;
}

export const passwordPolicy: ValidatorFn = (control): ValidationErrors | null => {
  const value = control.value as string;
  if (!value) {
    return null;
  }
  const violations = passwordViolations(value);
  return violations.length > 0 ? { passwordPolicy: violations } : null;
};
