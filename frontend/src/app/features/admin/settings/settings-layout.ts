import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-settings-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../section-nav.scss',
  template: `
    <h1 i18n="@@admin.settings.title">Configuración</h1>
    <nav aria-label="Configuración" i18n-aria-label="@@admin.settings.nav.label">
      <ul>
        <li>
          <a
            routerLink="general"
            routerLinkActive="is-active"
            ariaCurrentWhenActive="page"
            i18n="@@admin.settings.nav.general"
            >General</a
          >
        </li>
        <li>
          <a
            routerLink="hours"
            routerLinkActive="is-active"
            ariaCurrentWhenActive="page"
            i18n="@@admin.settings.nav.hours"
            >Horario</a
          >
        </li>
        <li>
          <a
            routerLink="zones"
            routerLinkActive="is-active"
            ariaCurrentWhenActive="page"
            i18n="@@admin.settings.nav.zones"
            >Zonas de reparto</a
          >
        </li>
        <li>
          <a
            routerLink="reasons"
            routerLinkActive="is-active"
            ariaCurrentWhenActive="page"
            i18n="@@admin.settings.nav.reasons"
            >Motivos</a
          >
        </li>
      </ul>
    </nav>
    <router-outlet />
  `,
})
export class SettingsLayout {}
