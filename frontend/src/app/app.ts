import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { AuthStore } from './core/auth/auth-store';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<router-outlet />',
})
export class App {
  constructor() {
    const auth = inject(AuthStore);
    const router = inject(Router);
    // Another tab signed in as someone else: leave the screens of the previous user.
    effect(() => {
      if (auth.identityChanged()) {
        void router.navigateByUrl('/login');
      }
    });
  }
}
