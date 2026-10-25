import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthStore } from '../../core/auth/auth-store';
import { SessionExpiryWarning } from '../../core/auth/session-expiry-warning';
import { ScreenWakeLock } from '../../core/screen/screen-wake-lock';
import { SessionExpiryBanner } from '../../shared/ui/session-expiry-banner/session-expiry-banner';
import { densityOf } from './display-density';
import { DisplayStore } from './display-store';

// A screen for customers: numbers and first names, nothing to click, nothing else of the order.
@Component({
  selector: 'app-display',
  imports: [RouterLink, SessionExpiryBanner],
  providers: [DisplayStore, ScreenWakeLock],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './display.scss',
  template: `
    <div class="columns" [style.--display-scale]="density().scale" [style.--display-columns]="density().columns">
      <section class="column" data-testid="column-preparing">
        <h2 i18n="@@display.preparing">Preparando</h2>
        <ol>
          @for (entry of store.preparing(); track $index) {
            <li>
              <span class="number">{{ entry.shortNumber }}</span>
              @if (entry.firstName) {
                <span class="name">{{ entry.firstName }}</span>
              }
            </li>
          }
        </ol>
        @if (store.preparing().length === 0 && store.loaded()) {
          <p class="empty" i18n="@@display.empty">Sin pedidos</p>
        }
      </section>
      <section class="column ready" data-testid="column-ready">
        <h2 i18n="@@display.ready">Listos para recoger</h2>
        <ol>
          @for (entry of store.ready(); track $index) {
            <li>
              <span class="number">{{ entry.shortNumber }}</span>
              @if (entry.firstName) {
                <span class="name">{{ entry.firstName }}</span>
              }
            </li>
          }
        </ol>
        @if (store.ready().length === 0 && store.loaded()) {
          <p class="empty" i18n="@@display.empty">Sin pedidos</p>
        }
      </section>
    </div>
    @if (store.failed() || !store.connected()) {
      <p class="stale" role="status" data-testid="stale" i18n="@@display.stale">Actualizando…</p>
    }
    @if (auth.sessionEnded()) {
      <p class="ended" role="status" data-testid="session-ended">
        <ng-container i18n="@@display.sessionEnded"
          >La sesión terminó: los números dejarán de actualizarse hasta que alguien inicie sesión de nuevo.</ng-container
        >
        <a routerLink="/login" [queryParams]="{ returnUrl: '/display' }" i18n="@@display.signIn">Iniciar sesión</a>
      </p>
    }
    @let daysLeft = warning.daysLeft();
    @if (daysLeft !== null) {
      <app-session-expiry-banner [daysLeft]="daysLeft" />
    }
  `,
})
export class Display {
  protected readonly store = inject(DisplayStore);
  protected readonly auth = inject(AuthStore);
  protected readonly warning = inject(SessionExpiryWarning);
  protected readonly density = computed(() =>
    densityOf(Math.max(this.store.preparing().length, this.store.ready().length)),
  );

  constructor() {
    inject(ScreenWakeLock).start();
  }
}
