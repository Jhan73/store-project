// PrimeNG form components do not set aria-invalid on the element that holds focus; these pass-through
// objects do. They are constants so a template binding keeps the same reference between checks.
// The valid state writes "false" because PrimeNG never removes a pass-through attribute once set.
const SELECT_INVALID = { label: { 'aria-invalid': 'true' } };
const SELECT_VALID = { label: { 'aria-invalid': 'false' } };
const NUMBER_INVALID = { pcInputText: { root: { 'aria-invalid': 'true' } } };
const NUMBER_VALID = { pcInputText: { root: { 'aria-invalid': 'false' } } };

export function selectAriaInvalid(invalid: boolean): object {
  return invalid ? SELECT_INVALID : SELECT_VALID;
}

export function numberAriaInvalid(invalid: boolean): object {
  return invalid ? NUMBER_INVALID : NUMBER_VALID;
}

export function passwordAriaInvalid(invalid: boolean): object {
  return invalid ? NUMBER_INVALID : NUMBER_VALID;
}

export function datePickerAriaInvalid(invalid: boolean): object {
  return invalid ? NUMBER_INVALID : NUMBER_VALID;
}
