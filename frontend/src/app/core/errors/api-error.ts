import type { ErrorCode } from '../api/api-types';

export interface FieldError {
  readonly field: string;
  readonly constraint?: string;
  readonly message?: string;
}

export interface ApiErrorInit {
  readonly status: number;
  readonly code: ErrorCode | null;
  readonly correlationId: string | null;
  readonly fieldErrors: readonly FieldError[];
  readonly properties: Readonly<Record<string, unknown>>;
}

// What every failed API call turns into. Status 0 is a network failure; `code` is null when the body is not Problem Details.
export class ApiError extends Error {
  readonly status: number;
  readonly code: ErrorCode | null;
  readonly correlationId: string | null;
  readonly fieldErrors: readonly FieldError[];
  // Members the code declares beyond the standard ones, e.g. `lockedUntil`.
  readonly properties: Readonly<Record<string, unknown>>;

  constructor(init: ApiErrorInit) {
    super(init.code ?? `HTTP ${init.status}`);
    this.name = 'ApiError';
    this.status = init.status;
    this.code = init.code;
    this.correlationId = init.correlationId;
    this.fieldErrors = init.fieldErrors;
    this.properties = init.properties;
  }
}
