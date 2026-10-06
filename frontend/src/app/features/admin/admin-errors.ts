import { AbstractControl } from '@angular/forms';
import { ApiError } from '../../core/errors/api-error';
import { ErrorNotifier } from '../../core/errors/error-notifier';
import { applyFieldErrors } from '../../core/errors/field-errors';

// The item moved or is gone since it was listed: what the screen shows is stale and must be re-read.
export function isStale(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    (error.status === 412 || error.status === 404 || error.code === 'common.concurrent-modification')
  );
}

// Marks the rejected fields when the form has them, and always tells the user through the toast.
export function reportFailure(notifier: ErrorNotifier, error: unknown, form?: AbstractControl): void {
  if (form && error instanceof ApiError && error.code === 'common.validation-failed') {
    applyFieldErrors(form, error);
  }
  notifier.show(error);
}
