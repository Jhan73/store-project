import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import type { Role } from '../api/api-types';
import { AuthStore } from './auth-store';

// UX only: the backend enforces every permission.
export function roleGuard(...allowed: Role[]): CanMatchFn {
  return async () => {
    const store = inject(AuthStore);
    const router = inject(Router);
    const target = router.currentNavigation()?.extractedUrl.toString();

    await store.restore();

    const role = store.role();
    if (role === null && store.refreshError() !== null) {
      // An outage says nothing about the session: keep the destination instead of logging the employee out.
      return router.createUrlTree(['/service-unavailable'], { queryParams: { returnUrl: target } });
    }
    if (role === null) {
      return router.createUrlTree(['/login'], { queryParams: { returnUrl: target } });
    }
    return allowed.includes(role) ? true : router.createUrlTree(['/forbidden']);
  };
}
