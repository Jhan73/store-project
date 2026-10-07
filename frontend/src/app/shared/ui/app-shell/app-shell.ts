import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import {
  IconDeviceDesktop,
  IconGlassFull,
  IconLogout,
  IconMenu2,
  IconMoon,
  IconSun,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Drawer } from 'primeng/drawer';
import { Tooltip } from 'primeng/tooltip';
import type { ThemeMode } from '../../../core/theme/theme-store';
import type { NavItem } from './nav-item';

@Component({
  selector: 'app-shell',
  imports: [ButtonDirective, Drawer, NgTemplateOutlet, RouterLink, RouterLinkActive, TablerIconComponent, Tooltip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './app-shell.scss',
  template: `
    <header class="bar">
      <div class="brand">
        <tabler-icon [icon]="icons.brand" aria-hidden="true" />
        <strong i18n="@@shell.brand">Juguería</strong>
      </div>

      <div class="inline">
        <ng-container *ngTemplateOutlet="links" />
        <div class="tools">
          <ng-container *ngTemplateOutlet="tools" />
        </div>
      </div>

      <button
        #menuButton
        pButton
        class="menu-button"
        type="button"
        severity="secondary"
        [outlined]="true"
        data-testid="menu-button"
        aria-label="Abrir el menú"
        i18n-aria-label="@@shell.menu.open"
        pTooltip="Abrir el menú"
        i18n-pTooltip="@@shell.menu.open"
        tooltipPosition="top"
        aria-controls="shell-menu"
        [attr.aria-expanded]="menuOpen()"
        (click)="menuOpen.set(true)"
      >
        <tabler-icon [icon]="icons.menu" aria-hidden="true" />
      </button>
    </header>

    <p-drawer
      [visible]="menuOpen()"
      (visibleChange)="menuOpen.set($event)"
      (onHide)="restoreFocus()"
      header="Menú"
      i18n-header="@@shell.menu.title"
      ariaCloseLabel="Cerrar el menú"
      i18n-ariaCloseLabel="@@shell.menu.close"
      position="left"
      [modal]="true"
      [dismissible]="true"
      [closeOnEscape]="true"
    >
      <div id="shell-menu" class="menu">
        <ng-container *ngTemplateOutlet="links" />
        <div class="tools">
          <ng-container *ngTemplateOutlet="tools" />
        </div>
      </div>
    </p-drawer>

    <main class="content">
      <ng-content />
    </main>

    <ng-template #links>
      <nav class="nav" aria-label="Navegación principal" i18n-aria-label="@@shell.nav.label">
        <ul>
          @for (item of items(); track item.path) {
            <li>
              <a
                [routerLink]="item.path"
                routerLinkActive="is-active"
                [routerLinkActiveOptions]="{ exact: item.exact ?? false }"
                ariaCurrentWhenActive="page"
                (click)="closeMenu()"
              >
                <tabler-icon [icon]="item.icon" aria-hidden="true" />
                <span>{{ item.label }}</span>
              </a>
            </li>
          }
        </ul>
      </nav>
    </ng-template>

    <ng-template #tools>
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
          pTooltip="Tema claro"
          i18n-pTooltip="@@shell.theme.light"
          tooltipPosition="top"
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
          pTooltip="Tema oscuro"
          i18n-pTooltip="@@shell.theme.dark"
          tooltipPosition="top"
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
          pTooltip="Tema del sistema"
          i18n-pTooltip="@@shell.theme.system"
          tooltipPosition="top"
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
        pTooltip="Cerrar la sesión"
        i18n-pTooltip="@@shell.logout.tooltip"
        tooltipPosition="top"
        (click)="signOut()"
      >
        <tabler-icon [icon]="icons.logout" aria-hidden="true" />
        <span i18n="@@shell.logout">Salir</span>
      </button>
    </ng-template>
  `,
})
export class AppShell {
  readonly items = input.required<readonly NavItem[]>();
  readonly roleLabel = input.required<string>();
  readonly themeMode = input.required<ThemeMode>();
  readonly themeModeChange = output<ThemeMode>();
  readonly logout = output<void>();

  protected readonly menuOpen = signal(false);
  private readonly menuButton = viewChild.required<ElementRef<HTMLButtonElement>>('menuButton');

  protected readonly icons = {
    brand: IconGlassFull,
    light: IconSun,
    dark: IconMoon,
    system: IconDeviceDesktop,
    logout: IconLogout,
    menu: IconMenu2,
  };

  protected closeMenu(): void {
    if (this.menuOpen()) {
      this.menuOpen.set(false);
      this.restoreFocus();
    }
  }

  protected restoreFocus(): void {
    this.menuButton().nativeElement.focus();
  }

  protected signOut(): void {
    this.closeMenu();
    this.logout.emit();
  }
}
