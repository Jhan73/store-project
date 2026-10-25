import type { ActivatedRouteSnapshot, Router } from '@angular/router';

// A screen that must keep what it shows (the customer display) sets `keepOnSessionEnd` on its route and handles the
// end of the session itself.
function keepsScreen(router: Router): boolean {
  let route: ActivatedRouteSnapshot | null = router.routerState.snapshot.root;
  while (route) {
    if (route.data['keepOnSessionEnd'] === true) {
      return true;
    }
    route = route.firstChild;
  }
  return false;
}

export function redirectToLogin(router: Router): void {
  const returnUrl = router.url;
  if (returnUrl.startsWith('/login') || keepsScreen(router)) {
    return;
  }
  void router.navigate(['/login'], {
    queryParams: returnUrl === '/' ? undefined : { returnUrl },
  });
}
