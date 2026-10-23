import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { IconRefresh, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';
import { AuthStore } from '../../core/auth/auth-store';
import { homeRouteFor, safeReturnUrl } from '../../core/auth/navigation';

// Shown when the session cannot be checked because the auth service is down; the employee's destination is kept.
@Component({
  selector: 'app-service-unavailable',
  imports: [ButtonDirective, TablerIconComponent, Tooltip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    main {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: var(--space-3);
      padding: var(--page-padding);
    }

    h1,
    p {
      margin: 0;
    }
  `,
  template: `
    <main>
      <h1 i18n="@@serviceUnavailable.title">El servicio no está disponible</h1>
      <p i18n="@@serviceUnavailable.hint">
        No se pudo comprobar tu sesión. Tu trabajo no se perdió: vuelve a intentarlo en unos segundos.
      </p>
      @if (stillDown()) {
        <p role="status" data-testid="still-down" i18n="@@serviceUnavailable.stillDown">
          El servicio sigue sin responder.
        </p>
      }
      <button
        pButton
        type="button"
        data-testid="retry"
        [loading]="retrying()"
        [disabled]="retrying()"
        pTooltip="Volver a comprobar la sesión"
        i18n-pTooltip="@@serviceUnavailable.retry.tooltip"
        tooltipPosition="top"
        (click)="retry()"
      >
        @if (!retrying()) {
          <tabler-icon [icon]="icons.retry" aria-hidden="true" />
        }
        <span i18n="@@serviceUnavailable.retry">Reintentar</span>
      </button>
    </main>
  `,
})
export class ServiceUnavailable {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly icons = { retry: IconRefresh };
  protected readonly retrying = signal(false);
  protected readonly stillDown = signal(false);

  protected async retry(): Promise<void> {
    this.retrying.set(true);
    this.stillDown.set(false);
    try {
      await this.auth.restore();
      if (this.auth.refreshError() !== null) {
        this.stillDown.set(true);
        return;
      }
      const returnUrl = safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));
      const role = this.auth.role();
      if (role === null) {
        await this.router.navigateByUrl(
          returnUrl ? `/login?returnUrl=${encodeURIComponent(returnUrl)}` : '/login',
        );
        return;
      }
      await this.router.navigateByUrl(returnUrl ?? homeRouteFor(role));
    } finally {
      this.retrying.set(false);
    }
  }
}
