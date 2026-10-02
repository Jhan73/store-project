import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { API_BASE_URL, API_ORIGIN } from '../api/api-config';
import { AuthStore } from './auth-store';
import { redirectToLogin } from './session-redirect';

function withBearer(request: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
}

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const origin = inject(API_ORIGIN);
  const authBase = `${inject(API_BASE_URL)}/auth/`;
  const store = inject(AuthStore);
  const router = inject(Router);

  if (!request.url.startsWith(`${origin}/`) || request.url.startsWith(authBase)) {
    return next(request);
  }

  const sentToken = store.accessToken();
  return next(withBearer(request, sentToken)).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || store.hasEnded()) {
        return throwError(() => error);
      }
      // Another request may have renewed the token while this one was in flight.
      const renewed = store.accessToken() !== null && store.accessToken() !== sentToken;
      return from(renewed ? Promise.resolve('refreshed' as const) : store.refresh()).pipe(
        switchMap((outcome) => {
          if (outcome === 'refreshed') {
            return next(withBearer(request, store.accessToken()));
          }
          if (outcome === 'rejected') {
            redirectToLogin(router);
            return throwError(() => error);
          }
          // An outage says nothing about the session: keep it and report the outage instead of "expired".
          return throwError(() => store.refreshError() ?? error);
        }),
      );
    }),
  );
};
