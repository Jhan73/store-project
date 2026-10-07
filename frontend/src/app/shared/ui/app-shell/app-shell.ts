import { DOCUMENT, isPlatformBrowser, NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
  output,
  PLATFORM_ID,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
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
import { Select } from 'primeng/select';
import { Tooltip } from 'primeng/tooltip';
import type { ThemeMode } from '../../../core/theme/theme-store';
import type { NavItem } from './nav-item';

@Component({
  selector: 'app-shell',
  imports: [
    ButtonDirective,
    Drawer,
    FormsModule,
    NgTemplateOutlet,
    RouterLink,
    RouterLinkActive,
    Select,
    TablerIconComponent,
    Tooltip,
  ],
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
        <p-select
          class="theme-select"
          [class.is-rail]="!sidebarExpanded()"
          inputId="shell-theme"
          [options]="themeOptions"
          optionLabel="label"
          optionValue="value"
          [ngModel]="themeMode()"
          (ngModelChange)="pickTheme($event)"
          [appendTo]="'body'"
          ariaLabel="Tema"
          i18n-ariaLabel="@@shell.theme.group"
          pTooltip="Tema"
          i18n-pTooltip="@@shell.theme.group"
          tooltipPosition="right"
          [tooltipDisabled]="sidebarExpanded()"
          (onShow)="themeOverlayOpen.set(true)"
          (onHide)="onThemeOverlayHide()"
        >
          <ng-template #selectedItem let-option>
            <span class="theme-value">
              <tabler-icon [icon]="option.icon" aria-hidden="true" />
              @if (sidebarExpanded()) {
                <span>{{ option.label }}</span>
              }
            </span>
          </ng-template>
          <ng-template #item let-option>
            <span class="theme-value">
              <tabler-icon [icon]="option.icon" aria-hidden="true" />
              <span>{{ option.label }}</span>
            </span>
          </ng-template>
        </p-select>
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
  protected readonly themeOverlayOpen = signal(false);
  private themePicked = false;
  private readonly menuButton = viewChild.required<ElementRef<HTMLButtonElement>>('menuButton');
  private readonly themeSelect = viewChild(Select);
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

  protected readonly themeOptions: { value: ThemeMode; label: string; icon: unknown }[] = [
    { value: 'light', label: $localize`:@@shell.theme.option.light:Claro`, icon: this.icons.light },
    { value: 'dark', label: $localize`:@@shell.theme.option.dark:Oscuro`, icon: this.icons.dark },
    {
      value: 'system',
      label: $localize`:@@shell.theme.option.system:Sistema`,
      icon: this.icons.system,
    },
  ];

  constructor() {
    if (!isPlatformBrowser(inject(PLATFORM_ID)) || typeof matchMedia !== 'function') {
      return;
    }
    // The desktop width is defined once in _breakpoints.scss and exposed by styles.scss.
    const desktop = getComputedStyle(inject(DOCUMENT).documentElement)
      .getPropertyValue('--bp-desktop')
      .trim();
    if (!desktop) {
      return;
    }
    const query = matchMedia(`(min-width: ${desktop})`);
    const onChange = () => this.resetForBreakpoint();
    query.addEventListener('change', onChange);
    inject(DestroyRef).onDestroy(() => query.removeEventListener('change', onChange));
  }

  protected readonly toggleIcon = computed(() =>
    this.sidebarExpanded() ? this.icons.collapse : this.icons.expand,
  );
  protected readonly pinIcon = computed(() =>
    this.sidebarPinned() ? this.icons.unpin : this.icons.pin,
  );

  // Each layout hides the other one's controls, so whatever the old layout left open must not linger.
  private resetForBreakpoint(): void {
    this.menuOpen.set(false);
    this.themeOverlayOpen.set(false);
    this.themeSelect()?.hide();
    if (!this.sidebarPinned() && this.sidebarExpanded()) {
      this.sidebarDismiss.emit();
    }
  }

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
    if (this.sidebarPinned() || !this.sidebarExpanded() || this.themeOverlayOpen()) {
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
    if (
      next instanceof Element &&
      !this.sidebar().nativeElement.contains(next) &&
      !next.closest('.p-select-overlay')
    ) {
      this.collapseTemporarily();
    }
  }

  protected pickTheme(mode: ThemeMode): void {
    this.themePicked = true;
    this.themeModeChange.emit(mode);
  }

  // The overlay swallowed the pointer and focus events, so nothing else re-evaluates the sidebar.
  protected onThemeOverlayHide(): void {
    const picked = this.themePicked;
    this.themePicked = false;
    this.themeOverlayOpen.set(false);
    if (this.sidebarPinned() || !this.sidebarExpanded()) {
      return;
    }
    const sidebar = this.sidebar().nativeElement;
    const pointerAway = !sidebar.matches(':hover');
    // Choosing an option returns the focus to the select, so the pointer alone decides then.
    if (pointerAway && (picked || !sidebar.contains(document.activeElement))) {
      this.collapseTemporarily();
    }
  }

  protected followSidebarLink(): void {
    this.collapseTemporarily();
  }

  // The pin button leaves the page when the sidebar collapses, so the focus moves to the toggle first.
  protected collapseTemporarily(): void {
    if (this.sidebarPinned() || !this.sidebarExpanded() || this.themeOverlayOpen()) {
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
