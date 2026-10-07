import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
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
  IconLayoutSidebarLeftCollapse,
  IconLayoutSidebarLeftExpand,
  IconLogout,
  IconMenu2,
  IconMoon,
  IconPin,
  IconPinnedOff,
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
  host: { '(document:keydown.escape)': 'onEscape()' },
  styleUrl: './app-shell.scss',
  template: `
    <header class="bar">
      <div class="brand">
        <tabler-icon [icon]="icons.brand" aria-hidden="true" />
        <strong i18n="@@shell.brand">Juguería</strong>
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
        [attr.aria-controls]="menuId"
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
      [modal]="true"
      [dismissible]="true"
      [closeOnEscape]="true"
    >
      <div [id]="menuId" class="menu">
        <ng-container *ngTemplateOutlet="links" />
        <div class="tools">
          <ng-container *ngTemplateOutlet="tools" />
        </div>
      </div>
    </p-drawer>

    <aside
      #sidebar
      class="sidebar"
      [id]="sidebarId"
      [class.is-expanded]="sidebarExpanded()"
      [class.is-docked]="sidebarPinned()"
      aria-label="Barra lateral"
      i18n-aria-label="@@shell.sidebar.label"
      (focusout)="onSidebarFocusOut($event)"
      (mouseleave)="collapseTemporarily()"
    >
      <div class="sidebar-top">
        <div class="brand">
          <tabler-icon [icon]="icons.brand" aria-hidden="true" />
          <strong class="label" i18n="@@shell.sidebar.brand">Juguería</strong>
        </div>

        <div class="sidebar-actions">
          <button
            #sidebarToggleButton
            pButton
            type="button"
            severity="secondary"
            [text]="true"
            data-testid="sidebar-toggle"
            aria-label="Expandir o contraer la barra lateral"
            i18n-aria-label="@@shell.sidebar.toggle"
            pTooltip="Expandir o contraer la barra lateral"
            i18n-pTooltip="@@shell.sidebar.toggle"
            tooltipPosition="top"
            [attr.aria-controls]="sidebarId"
            [attr.aria-expanded]="sidebarExpanded()"
            (click)="sidebarToggle.emit()"
          >
            <tabler-icon [icon]="toggleIcon()" aria-hidden="true" />
          </button>
          @if (sidebarExpanded()) {
            <button
              #sidebarPinButton
              pButton
              type="button"
              severity="secondary"
              [text]="true"
              data-testid="sidebar-pin"
              aria-label="Anclar la barra lateral"
              i18n-aria-label="@@shell.sidebar.pin"
              pTooltip="Anclar la barra lateral"
              i18n-pTooltip="@@shell.sidebar.pin"
              tooltipPosition="top"
              [attr.aria-pressed]="sidebarPinned()"
              (click)="sidebarPinToggle.emit()"
            >
              <tabler-icon [icon]="pinIcon()" aria-hidden="true" />
            </button>
          }
        </div>
      </div>

      <nav
        class="nav sidebar-nav"
        aria-label="Navegación lateral"
        i18n-aria-label="@@shell.sidebar.nav.label"
      >
        <ul>
          @for (item of items(); track item.path) {
            <li>
              <a
                [routerLink]="item.path"
                routerLinkActive="is-active"
                [routerLinkActiveOptions]="{ exact: item.exact ?? false }"
                ariaCurrentWhenActive="page"
                [attr.aria-label]="item.label"
                [pTooltip]="item.label"
                tooltipPosition="right"
                [tooltipDisabled]="sidebarExpanded()"
                (click)="followSidebarLink()"
              >
                <tabler-icon [icon]="item.icon" aria-hidden="true" />
                <span class="label">{{ item.label }}</span>
              </a>
            </li>
          }
        </ul>
      </nav>

      <div class="tools sidebar-tools">
        <span class="role label">{{ roleLabel() }}</span>
        <ng-container *ngTemplateOutlet="themes" />
        <button
          pButton
          type="button"
          severity="secondary"
          [outlined]="true"
          data-testid="logout"
          aria-label="Cerrar la sesión"
          i18n-aria-label="@@shell.logout.tooltip"
          pTooltip="Cerrar la sesión"
          i18n-pTooltip="@@shell.logout.tooltip"
          tooltipPosition="top"
          (click)="signOut()"
        >
          <tabler-icon [icon]="icons.logout" aria-hidden="true" />
          <span class="label" i18n="@@shell.logout">Salir</span>
        </button>
      </div>
    </aside>

    <main class="content" [class.is-docked]="sidebarPinned()">
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

      <ng-container *ngTemplateOutlet="themes" />

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

    <ng-template #themes>
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
    </ng-template>
  `,
})
export class AppShell {
  readonly items = input.required<readonly NavItem[]>();
  readonly roleLabel = input.required<string>();
  readonly themeMode = input.required<ThemeMode>();
  readonly sidebarExpanded = input(false);
  readonly sidebarPinned = input(false);
  readonly themeModeChange = output<ThemeMode>();
  readonly sidebarToggle = output<void>();
  readonly sidebarPinToggle = output<void>();
  readonly sidebarDismiss = output<void>();
  readonly logout = output<void>();

  protected readonly menuId = 'shell-menu';
  protected readonly sidebarId = 'shell-sidebar';
  protected readonly menuOpen = signal(false);
  private readonly menuButton = viewChild.required<ElementRef<HTMLButtonElement>>('menuButton');
  private readonly sidebar = viewChild.required<ElementRef<HTMLElement>>('sidebar');
  private readonly sidebarToggleButton =
    viewChild.required<ElementRef<HTMLButtonElement>>('sidebarToggleButton');
  private readonly sidebarPinButton = viewChild<ElementRef<HTMLButtonElement>>('sidebarPinButton');

  protected readonly icons = {
    brand: IconGlassFull,
    light: IconSun,
    dark: IconMoon,
    system: IconDeviceDesktop,
    logout: IconLogout,
    menu: IconMenu2,
    expand: IconLayoutSidebarLeftExpand,
    collapse: IconLayoutSidebarLeftCollapse,
    pin: IconPin,
    unpin: IconPinnedOff,
  };

  protected readonly toggleIcon = computed(() =>
    this.sidebarExpanded() ? this.icons.collapse : this.icons.expand,
  );
  protected readonly pinIcon = computed(() =>
    this.sidebarPinned() ? this.icons.unpin : this.icons.pin,
  );

  protected closeMenu(): void {
    if (this.menuOpen()) {
      this.menuOpen.set(false);
      this.restoreFocus();
    }
  }

  protected restoreFocus(): void {
    this.menuButton().nativeElement.focus();
  }

  protected onEscape(): void {
    if (this.sidebarPinned() || !this.sidebarExpanded()) {
      return;
    }
    const hadFocus = this.sidebar().nativeElement.contains(document.activeElement);
    this.collapseTemporarily();
    if (hadFocus) {
      this.sidebarToggleButton().nativeElement.focus();
    }
  }

  protected onSidebarFocusOut(event: FocusEvent): void {
    const next = event.relatedTarget;
    if (next instanceof Node && !this.sidebar().nativeElement.contains(next)) {
      this.collapseTemporarily();
    }
  }

  protected followSidebarLink(): void {
    this.collapseTemporarily();
  }

  // The pin button leaves the page when the sidebar collapses, so the focus moves to the toggle first.
  protected collapseTemporarily(): void {
    if (this.sidebarPinned() || !this.sidebarExpanded()) {
      return;
    }
    if (document.activeElement === this.sidebarPinButton()?.nativeElement) {
      this.sidebarToggleButton().nativeElement.focus();
    }
    this.sidebarDismiss.emit();
  }

  protected signOut(): void {
    this.closeMenu();
    this.logout.emit();
  }
}
