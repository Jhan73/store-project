import { FormArray, FormControl, FormGroup } from '@angular/forms';
import { ApiError } from './api-error';
import { applyFieldErrors } from './field-errors';

function validationError(fields: { field: string; constraint?: string }[]): ApiError {
  return new ApiError({
    status: 400,
    code: 'common.validation-failed',
    correlationId: null,
    fieldErrors: fields,
    properties: {},
  });
}

describe('applyFieldErrors', () => {
  it('marks the matching control with the violated constraint', () => {
    const form = new FormGroup({ email: new FormControl(''), password: new FormControl('') });

    const unmatched = applyFieldErrors(form, validationError([{ field: 'email', constraint: 'Email' }]));

    expect(form.controls.email.errors).toEqual({ server: { constraint: 'Email' } });
    expect(form.controls.password.errors).toBeNull();
    expect(unmatched).toEqual([]);
  });

  it('follows JSON paths into nested groups and arrays', () => {
    const form = new FormGroup({
      lines: new FormArray([new FormGroup({ quantity: new FormControl(0) })]),
    });

    applyFieldErrors(form, validationError([{ field: 'lines[0].quantity', constraint: 'Positive' }]));

    expect(form.get('lines.0.quantity')?.errors).toEqual({ server: { constraint: 'Positive' } });
  });

  it('reports fields that have no control, so the caller can show a general message', () => {
    const form = new FormGroup({ email: new FormControl('') });

    const unmatched = applyFieldErrors(form, validationError([{ field: 'nickname', constraint: 'Size' }]));

    expect(unmatched).toEqual(['nickname']);
  });

  it('marks touched controls so the error shows', () => {
    const form = new FormGroup({ email: new FormControl('') });

    applyFieldErrors(form, validationError([{ field: 'email', constraint: 'Email' }]));

    expect(form.controls.email.touched).toBe(true);
  });

  it('clears the server error when the user edits the control', () => {
    const form = new FormGroup({ email: new FormControl('') });
    applyFieldErrors(form, validationError([{ field: 'email', constraint: 'Email' }]));

    form.controls.email.setValue('a@b.pe');

    expect(form.controls.email.errors).toBeNull();
  });
});
