import type { Router } from '@angular/router';

export function redirectToLogin(router: Router): void {
  const returnUrl = router.url;
  if (returnUrl.startsWith('/login')) {
    return;
  }
  void router.navigate(['/login'], {
    queryParams: returnUrl === '/' ? undefined : { returnUrl },
  });
}
