import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ScreenWakeLock } from '../../core/screen/screen-wake-lock';
import { densityOf } from './display-density';
import { DisplayStore } from './display-store';

// A screen for customers: numbers and first names, nothing to click, nothing else of the order.
@Component({
  selector: 'app-display',
  providers: [DisplayStore, ScreenWakeLock],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './display.scss',
  template: `
    <div class="columns" [style.--display-scale]="density().scale" [style.--display-columns]="density().columns">
      <section class="column" [attr.aria-labelledby]="'display-preparing'" data-testid="column-preparing">
        <h2 id="display-preparing" i18n="@@display.preparing">Preparando</h2>
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
      <section class="column ready" [attr.aria-labelledby]="'display-ready'" data-testid="column-ready">
        <h2 id="display-ready" i18n="@@display.ready">Listos para recoger</h2>
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
  `,
})
export class Display {
  protected readonly store = inject(DisplayStore);
  protected readonly density = computed(() =>
    densityOf(Math.max(this.store.preparing().length, this.store.ready().length)),
  );

  constructor() {
    inject(ScreenWakeLock).start();
  }
}
