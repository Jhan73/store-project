import { ChangeDetectionStrategy, Component, input } from '@angular/core';

// Text only, so the customer screen can show it too.
@Component({
  selector: 'app-session-expiry-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    :host {
      display: block;
    }

    .banner {
      margin: 0;
      padding: var(--space-3) var(--space-4);
      color: var(--warning-foreground);
      background: var(--warning);
      font-weight: 600;
    }

    .banner.compact {
      padding: var(--space-1) var(--space-3);
      border-radius: var(--radius);
      font-size: 0.875rem;
      font-weight: 500;
    }
  `,
  template: `
    <p class="banner" [class.compact]="compact()" role="status" data-testid="session-expiry-banner">
      @if (daysLeft() >= 1) {
        <ng-container i18n="@@shared.sessionExpiry.days"
          >La sesión de esta pantalla vence en {daysLeft(), plural, =1 {1 día} other {{{ daysLeft() }} días}}. Alguien del
          equipo debe volver a iniciar sesión antes para que no se detenga.</ng-container
        >
      } @else {
        <ng-container i18n="@@shared.sessionExpiry.soon"
          >La sesión de esta pantalla vence en menos de un día. Alguien del equipo debe volver a iniciar sesión ya para que
          no se detenga.</ng-container
        >
      }
    </p>
  `,
})
export class SessionExpiryBanner {
  // Whole days left; 0 means under a day.
  readonly daysLeft = input.required<number>();
  // For a screen that people other than the team look at: the same words, smaller.
  readonly compact = input(false);
}
