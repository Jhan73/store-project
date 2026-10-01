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
    if (role === null) {
      return router.createUrlTree(['/login'], { queryParams: { returnUrl: target } });
    }
    return allowed.includes(role) ? true : router.createUrlTree(['/forbidden']);
  };
}
