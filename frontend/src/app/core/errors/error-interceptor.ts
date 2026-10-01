import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { API_ORIGIN } from '../api/api-config';
import type { ErrorCode } from '../api/api-types';
import { ApiError, FieldError } from './api-error';

const STANDARD_MEMBERS = new Set(['type', 'title', 'status', 'detail', 'instance', 'code', 'correlationId', 'errors']);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function fieldErrorsOf(body: Record<string, unknown>): FieldError[] {
  return Array.isArray(body['errors'])
    ? body['errors'].filter(isRecord).flatMap((item) =>
        typeof item['field'] === 'string'
          ? [
              {
                field: item['field'],
                ...(typeof item['constraint'] === 'string' && { constraint: item['constraint'] }),
                ...(typeof item['message'] === 'string' && { message: item['message'] }),
              },
            ]
          : [],
      )
    : [];
}

function toApiError(response: HttpErrorResponse): ApiError {
  const body = isRecord(response.error) ? response.error : {};
  const correlationId =
    typeof body['correlationId'] === 'string'
      ? body['correlationId']
      : response.headers?.get('X-Request-Id') ?? null;
  return new ApiError({
    status: response.status,
    code: typeof body['code'] === 'string' ? (body['code'] as ErrorCode) : null,
    correlationId,
    fieldErrors: fieldErrorsOf(body),
    properties: Object.fromEntries(Object.entries(body).filter(([key]) => !STANDARD_MEMBERS.has(key))),
  });
}

export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const origin = inject(API_ORIGIN);
  if (!request.url.startsWith(`${origin}/`)) {
    return next(request);
  }
  return next(request).pipe(
    catchError((error: unknown) =>
      throwError(() => (error instanceof HttpErrorResponse ? toApiError(error) : error)),
    ),
  );
};
