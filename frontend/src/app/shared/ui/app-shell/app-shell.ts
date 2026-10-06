import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import {
  IconDeviceDesktop,
  IconGlassFull,
  IconLogout,
  IconMoon,
  IconSun,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import type { ThemeMode } from '../../../core/theme/theme-store';
import type { NavItem } from './nav-item';

@Component({
  selector: 'app-shell',
  imports: [ButtonDirective, RouterLink, RouterLinkActive, TablerIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './app-shell.scss',
  template: `
    <header class="bar">
      <div class="brand">
        <tabler-icon [icon]="icons.brand" aria-hidden="true" />
        <strong i18n="@@shell.brand">Juguería</strong>
      </div>

      <nav
        class="nav"
        aria-label="Navegación principal"
        i18n-aria-label="@@shell.nav.label"
      >
        <ul>
          @for (item of items(); track item.path) {
            <li>
              <a
                [routerLink]="item.path"
                routerLinkActive="is-active"
                [routerLinkActiveOptions]="{ exact: item.exact ?? false }"
                ariaCurrentWhenActive="page"
              >
                <tabler-icon [icon]="item.icon" aria-hidden="true" />
                <span>{{ item.label }}</span>
              </a>
            </li>
          }
        </ul>
      </nav>

      <div class="tools">
        <span class="role">{{ roleLabel() }}</span>

        <div class="themes" role="group" aria-label="Tema" i18n-aria-label="@@shell.theme.group">
          <button
            pButton
            type="button"
            severity="secondary"
            [text]="true"
            [attr.aria-pressed]="themeMode() === 'light'"
            aria-label="Tema claro"
            i18n-aria-label="@@shell.theme.light"
            (click)="themeModeChange.emit('light')"
          >
            <tabler-icon [icon]="icons.light" aria-hidden="true" />
          </button>
          <button
            pButton
            type="button"
            severity="secondary"
            [text]="true"
            [attr.aria-pressed]="themeMode() === 'dark'"
            aria-label="Tema oscuro"
            i18n-aria-label="@@shell.theme.dark"
            (click)="themeModeChange.emit('dark')"
          >
            <tabler-icon [icon]="icons.dark" aria-hidden="true" />
          </button>
          <button
            pButton
            type="button"
            severity="secondary"
            [text]="true"
            [attr.aria-pressed]="themeMode() === 'system'"
            aria-label="Tema del sistema"
            i18n-aria-label="@@shell.theme.system"
            (click)="themeModeChange.emit('system')"
          >
            <tabler-icon [icon]="icons.system" aria-hidden="true" />
          </button>
        </div>

        <button
          pButton
          type="button"
          severity="secondary"
          [outlined]="true"
          data-testid="logout"
          (click)="logout.emit()"
        >
          <tabler-icon [icon]="icons.logout" aria-hidden="true" />
          <span i18n="@@shell.logout">Salir</span>
        </button>
      </div>
    </header>

    <main class="content">
      <ng-content />
    </main>
  `,
})
export class AppShell {
  readonly items = input.required<readonly NavItem[]>();
  readonly roleLabel = input.required<string>();
  readonly themeMode = input.required<ThemeMode>();
  readonly themeModeChange = output<ThemeMode>();
  readonly logout = output<void>();

  protected readonly icons = {
    brand: IconGlassFull,
    light: IconSun,
    dark: IconMoon,
    system: IconDeviceDesktop,
    logout: IconLogout,
  };
}
