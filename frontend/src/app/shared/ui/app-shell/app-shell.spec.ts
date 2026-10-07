import {
  ChangeDetectionStrategy,
  Component,
  type DebugElement,
  inject,
  signal,
} from '@angular/core';
import { type ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';
import { IconHome, IconSettings } from '@tabler/icons-angular';
import { Select } from 'primeng/select';
import { Tooltip } from 'primeng/tooltip';
import { chooseOption, openSelect, selectedLabel } from '../../../testing/primeng-controls';
import { SidebarStore } from '../../../core/layout/sidebar-store';
import type { ThemeMode } from '../../../core/theme/theme-store';
import { AppShell } from './app-shell';
import type { NavItem } from './nav-item';

const items: NavItem[] = [
  { path: '/staff', label: 'Operación', icon: IconHome },
  { path: '/admin', label: 'Administración', icon: IconSettings },
];

@Component({
  selector: 'app-shell-harness',
  imports: [AppShell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-shell
      [items]="items"
      roleLabel="Cajero"
      [themeMode]="mode()"
      [sidebarExpanded]="sidebar.expanded()"
      [sidebarPinned]="sidebar.pinned()"
      (sidebarToggle)="sidebar.toggle()"
      (sidebarPinToggle)="sidebar.togglePin()"
      (sidebarDismiss)="sidebar.dismiss()"
      (themeModeChange)="changes.push($event)"
      (logout)="logouts.set(logouts() + 1)"
    >
      <p>Contenido de la página</p>
    </app-shell>
  `,
})
class Harness {
  readonly items = items;
  readonly sidebar = inject(SidebarStore);
  readonly mode = signal<ThemeMode>('system');
  readonly changes: ThemeMode[] = [];
  readonly logouts = signal(0);
}

@Component({
  selector: 'app-shell-nested-harness',
  imports: [AppShell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<app-shell [items]="items" roleLabel="Admin" themeMode="system" />`,
})
class NestedHarness {
  readonly items: NavItem[] = [
    { path: '/admin', label: 'Administración', icon: IconSettings, exact: true },
    { path: '/admin/catalog', label: 'Catálogo', icon: IconHome },
  ];
}

describe('AppShell', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('gives every icon-only and text button a tooltip that matches its name', async () => {
    const { fixture, host } = await render();
    await openDrawer(fixture, host);

    const themeButtons = [...drawerEl()!.querySelectorAll<HTMLElement>('.themes button')];
    expect(themeButtons).toHaveLength(3);
    for (const button of themeButtons) {
      expect(button.getAttribute('pTooltip')).toBe(button.getAttribute('aria-label'));
      expect(button.querySelector('svg')).not.toBeNull();
    }
    const logout = host.querySelector<HTMLElement>('[data-testid="logout"]');
    expect(logout?.getAttribute('pTooltip')).toBe('Cerrar la sesión');
    expect(logout?.textContent?.trim()).toBe('Salir');
  });

  const drawerEl = () => document.body.querySelector<HTMLElement>('.p-drawer');
  async function openDrawer(fixture: { whenStable(): Promise<unknown> }, host: HTMLElement) {
    host.querySelector<HTMLButtonElement>('[data-testid="menu-button"]')!.click();
    await fixture.whenStable();
  }

  async function render() {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', children: [] }])] });
    const fixture = TestBed.createComponent(Harness);
    await fixture.whenStable();
    return { fixture, host: fixture.nativeElement as HTMLElement };
  }

  it('shows the brand, the role and the projected page', async () => {
    const { host } = await render();

    expect(host.querySelector('header')?.textContent).toContain('Juguería');
    expect(host.querySelector('aside')?.textContent).toContain('Juguería');
    expect(host.querySelector('aside')?.textContent).toContain('Cajero');
    expect(host.querySelector('main')?.textContent).toContain('Contenido de la página');
  });

  it('lists the navigation items as links', async () => {
    const { host } = await render();

    const links = Array.from(host.querySelectorAll('nav a'));
    expect(links.map((link) => link.getAttribute('href'))).toEqual(['/staff', '/admin']);
    expect(links.map((link) => link.textContent?.trim())).toEqual(['Operación', 'Administración']);
    expect(host.querySelector('nav')?.getAttribute('aria-label')).toBeTruthy();
  });

  it('marks only the exact item active when a nested route is open', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', children: [] }])] });
    const fixture = TestBed.createComponent(NestedHarness);
    await TestBed.inject(Router).navigateByUrl('/admin/catalog');
    await fixture.whenStable();

    const active = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('nav a.is-active'),
    ).map((link) => link.getAttribute('href'));
    expect(active).toEqual(['/admin/catalog']);
  });

  it('keeps decorative icons out of the accessibility tree', async () => {
    const { host } = await render();

    const icons = Array.from(host.querySelectorAll('tabler-icon'));
    expect(icons.length).toBeGreaterThan(0);
    expect(icons.every((icon) => icon.getAttribute('aria-hidden') === 'true')).toBe(true);
  });

  it('offers light, dark and system themes with labelled buttons', async () => {
    const { fixture, host } = await render();
    fixture.componentInstance.mode.set('dark');
    await openDrawer(fixture, host);

    const buttons = Array.from(drawerEl()!.querySelectorAll<HTMLButtonElement>('[role="group"] button'));
    expect(buttons).toHaveLength(3);
    expect(buttons.every((button) => !!button.getAttribute('aria-label'))).toBe(true);
    expect(buttons.map((button) => button.getAttribute('aria-pressed'))).toEqual([
      'false',
      'true',
      'false',
    ]);
  });

  it('emits the chosen theme', async () => {
    const { fixture, host } = await render();
    await openDrawer(fixture, host);

    const [light, dark, system] = Array.from(
      drawerEl()!.querySelectorAll<HTMLButtonElement>('[role="group"] button'),
    );
    light.click();
    dark.click();
    system.click();

    expect(fixture.componentInstance.changes).toEqual(['light', 'dark', 'system']);
  });

  it('emits logout', async () => {
    const { fixture, host } = await render();

    host.querySelector<HTMLButtonElement>('button[data-testid="logout"]')?.click();

    expect(fixture.componentInstance.logouts()).toBe(1);
  });

  describe('menu drawer', () => {
    const drawer = () => document.body.querySelector<HTMLElement>('.p-drawer');
    const hamburger = (host: HTMLElement) =>
      host.querySelector<HTMLButtonElement>('[data-testid="menu-button"]')!;

    it('starts with a closed drawer and a labelled hamburger button', async () => {
      const { host } = await render();

      const button = hamburger(host);
      expect(button.getAttribute('aria-label')).toBe('Abrir el menú');
      expect(button.getAttribute('pTooltip')).toBe('Abrir el menú');
      expect(button.getAttribute('aria-expanded')).toBe('false');
      expect(button.getAttribute('aria-controls')).toBeTruthy();
      expect(button.querySelector('svg')).not.toBeNull();
      expect(drawer()).toBeNull();
    });

    it('opens the drawer with the navigation, themes, role and sign-out', async () => {
      const { fixture, host } = await render();

      hamburger(host).click();
      await fixture.whenStable();

      expect(hamburger(host).getAttribute('aria-expanded')).toBe('true');
      const open = drawer()!;
      expect(open).not.toBeNull();
      expect(open.querySelector(`#${hamburger(host).getAttribute('aria-controls')}`)).not.toBeNull();
      const links = Array.from(open.querySelectorAll('nav a'));
      expect(links.map((link) => link.getAttribute('href'))).toEqual(['/staff', '/admin']);
      expect(open.textContent).toContain('Cajero');
      expect(open.querySelectorAll('.themes button')).toHaveLength(3);
      expect(open.querySelector('[data-testid="logout"]')).not.toBeNull();
    });

    it('closes on Escape and gives the focus back to the hamburger', async () => {
      const { fixture, host } = await render();
      hamburger(host).click();
      await fixture.whenStable();

      document.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', which: 27, bubbles: true }),
      );
      await fixture.whenStable();

      expect(hamburger(host).getAttribute('aria-expanded')).toBe('false');
      expect(document.activeElement).toBe(hamburger(host));
    });

    it('closes when a navigation link is followed', async () => {
      const { fixture, host } = await render();
      hamburger(host).click();
      await fixture.whenStable();

      drawer()!.querySelector<HTMLAnchorElement>('nav a')!.click();
      await fixture.whenStable();

      expect(hamburger(host).getAttribute('aria-expanded')).toBe('false');
      expect(document.activeElement).toBe(hamburger(host));
    });

    it('closes when the sign-out button is used', async () => {
      const { fixture, host } = await render();
      hamburger(host).click();
      await fixture.whenStable();

      drawer()!.querySelector<HTMLButtonElement>('[data-testid="logout"]')!.click();
      await fixture.whenStable();

      expect(fixture.componentInstance.logouts()).toBe(1);
      expect(hamburger(host).getAttribute('aria-expanded')).toBe('false');
    });
  });

  describe('sidebar', () => {
    const aside = (host: HTMLElement) => host.querySelector<HTMLElement>('aside')!;
    const toggle = (host: HTMLElement) =>
      host.querySelector<HTMLButtonElement>('[data-testid="sidebar-toggle"]')!;
    const pin = (host: HTMLElement) =>
      host.querySelector<HTMLButtonElement>('[data-testid="sidebar-pin"]');
    const content = (host: HTMLElement) => host.querySelector<HTMLElement>('main')!;
    const firstLink = (host: HTMLElement) =>
      aside(host).querySelector<HTMLAnchorElement>('nav a')!;
    const expand = async (fixture: { whenStable(): Promise<unknown> }, host: HTMLElement) => {
      toggle(host).click();
      await fixture.whenStable();
    };
    const focusOut = (from: Element, to: Element | null) =>
      from.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: to }));
    const keydown = (target: HTMLElement, key: string) =>
      target.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));

    it('starts as a rail: toggle collapsed, no pin button, content not docked', async () => {
      const { host } = await render();

      expect(toggle(host).getAttribute('aria-expanded')).toBe('false');
      expect(toggle(host).getAttribute('aria-controls')).toBe(aside(host).id);
      expect(toggle(host).getAttribute('aria-label')).toBeTruthy();
      expect(toggle(host).getAttribute('pTooltip')).toBe(toggle(host).getAttribute('aria-label'));
      expect(toggle(host).querySelector('svg')).not.toBeNull();
      expect(aside(host).classList).not.toContain('is-expanded');
      expect(pin(host)).toBeNull();
      expect(content(host).classList).not.toContain('is-docked');
    });

    it('labels the rail links and shows their name in a tooltip on the right', async () => {
      const { fixture, host } = await render();

      const links = Array.from(aside(host).querySelectorAll<HTMLAnchorElement>('nav a'));
      const names = ['Operación', 'Administración'];
      expect(links.map((link) => link.getAttribute('aria-label'))).toEqual(names);
      const tooltips = fixture.debugElement
        .queryAll(By.css('aside nav a'))
        .map((link) => link.injector.get(Tooltip));
      expect(tooltips.map((tooltip) => tooltip.content)).toEqual(names);
      expect(tooltips.every((tooltip) => tooltip.tooltipPosition === 'right')).toBe(true);
      expect(links.every((link) => link.querySelector('svg') !== null)).toBe(true);
    });

    it('gives every sidebar button an icon, a tooltip and a name', async () => {
      const { fixture, host } = await render();
      await expand(fixture, host);

      const buttons = Array.from(aside(host).querySelectorAll<HTMLElement>('button'));
      expect(buttons.length).toBeGreaterThanOrEqual(3);
      for (const button of buttons) {
        expect(button.querySelector('svg')).not.toBeNull();
        expect(button.getAttribute('pTooltip')).toBeTruthy();
        expect(button.getAttribute('aria-label')).toBeTruthy();
      }
    });

    it('keeps the sidebar and the drawer navigations under different names', async () => {
      const { fixture, host } = await render();
      host.querySelector<HTMLButtonElement>('[data-testid="menu-button"]')!.click();
      await fixture.whenStable();

      const names = Array.from(document.body.querySelectorAll('nav')).map((nav) =>
        nav.getAttribute('aria-label'),
      );
      expect(names).toHaveLength(2);
      expect(new Set(names).size).toBe(2);
    });

    it('expands as an overlay without moving the content', async () => {
      const { fixture, host } = await render();

      await expand(fixture, host);

      expect(toggle(host).getAttribute('aria-expanded')).toBe('true');
      expect(aside(host).classList).toContain('is-expanded');
      expect(pin(host)).not.toBeNull();
      expect(pin(host)!.getAttribute('aria-pressed')).toBe('false');
      expect(content(host).classList).not.toContain('is-docked');
    });

    it('docks the sidebar when pinned and pushes the content', async () => {
      const { fixture, host } = await render();
      await expand(fixture, host);

      pin(host)!.click();
      await fixture.whenStable();

      expect(pin(host)!.getAttribute('aria-pressed')).toBe('true');
      expect(content(host).classList).toContain('is-docked');
      expect(aside(host).classList).toContain('is-docked');
    });

    it('unpins and returns to the rail when a pinned sidebar is collapsed', async () => {
      const { fixture, host } = await render();
      await expand(fixture, host);
      pin(host)!.click();
      await fixture.whenStable();

      toggle(host).click();
      await fixture.whenStable();

      expect(toggle(host).getAttribute('aria-expanded')).toBe('false');
      expect(pin(host)).toBeNull();
      expect(content(host).classList).not.toContain('is-docked');
      expect(localStorage.getItem('app-sidebar-pinned')).toBeNull();
    });

    it('collapses a temporary expansion on Escape and returns the focus to the toggle', async () => {
      const { fixture, host } = await render();
      await expand(fixture, host);
      pin(host)!.focus();

      keydown(pin(host)!, 'Escape');
      await fixture.whenStable();

      expect(toggle(host).getAttribute('aria-expanded')).toBe('false');
      expect(document.activeElement).toBe(toggle(host));
    });

    it('collapses a temporary expansion when a link is followed', async () => {
      const { fixture, host } = await render();
      await expand(fixture, host);

      firstLink(host).click();
      await fixture.whenStable();

      expect(toggle(host).getAttribute('aria-expanded')).toBe('false');
    });

    it('collapses a temporary expansion when the focus leaves it', async () => {
      const { fixture, host } = await render();
      await expand(fixture, host);

      focusOut(firstLink(host), content(host));
      await fixture.whenStable();

      expect(toggle(host).getAttribute('aria-expanded')).toBe('false');
    });

    it('stays expanded while the focus moves inside the sidebar', async () => {
      const { fixture, host } = await render();
      await expand(fixture, host);

      focusOut(toggle(host), pin(host));
      await fixture.whenStable();

      expect(toggle(host).getAttribute('aria-expanded')).toBe('true');
    });

    it('collapses a temporary expansion when the pointer leaves it', async () => {
      const { fixture, host } = await render();
      await expand(fixture, host);

      aside(host).dispatchEvent(new MouseEvent('mouseleave'));
      await fixture.whenStable();

      expect(toggle(host).getAttribute('aria-expanded')).toBe('false');
    });

    it('keeps a pinned sidebar docked on Escape, link, focus-out and pointer leave', async () => {
      const { fixture, host } = await render();
      await expand(fixture, host);
      pin(host)!.click();
      await fixture.whenStable();

      keydown(pin(host)!, 'Escape');
      firstLink(host).click();
      focusOut(firstLink(host), content(host));
      aside(host).dispatchEvent(new MouseEvent('mouseleave'));
      await fixture.whenStable();

      expect(toggle(host).getAttribute('aria-expanded')).toBe('true');
      expect(content(host).classList).toContain('is-docked');
    });

    it('restores a pinned sidebar as docked', async () => {
      localStorage.setItem('app-sidebar-pinned', 'true');

      const { host } = await render();

      expect(toggle(host).getAttribute('aria-expanded')).toBe('true');
      expect(content(host).classList).toContain('is-docked');
    });

    it('renders when the storage throws', async () => {
      vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
        throw new Error('blocked');
      });

      const { host } = await render();

      expect(toggle(host).getAttribute('aria-expanded')).toBe('false');
    });

    describe('theme select', () => {
      const themeSelect = (host: HTMLElement) =>
        aside(host).querySelector<HTMLElement>('p-select')!;
      const combobox = (host: HTMLElement) =>
        aside(host).querySelector<HTMLElement>('p-select [role="combobox"]')!;

      it('replaces the three theme buttons with one labelled select', async () => {
        const { host } = await render();

        expect(aside(host).querySelectorAll('.themes button')).toHaveLength(0);
        expect(aside(host).querySelectorAll('p-select')).toHaveLength(1);
        expect(combobox(host).getAttribute('aria-label')).toBe('Tema');
      });

      it('emits the chosen theme', async () => {
        const { fixture, host } = await render();
        await expand(fixture, host);

        await chooseOption(fixture, document.body, 'shell-theme', 'Oscuro');

        expect(fixture.componentInstance.changes).toEqual(['dark']);
      });

      it('reflects the current theme with its icon and label when expanded', async () => {
        const { fixture, host } = await render();
        fixture.componentInstance.mode.set('dark');
        await expand(fixture, host);

        expect(selectedLabel(host, 'shell-theme')).toBe('Oscuro');
        expect(combobox(host).querySelector('svg')).not.toBeNull();

        fixture.componentInstance.mode.set('light');
        await fixture.whenStable();
        expect(selectedLabel(host, 'shell-theme')).toBe('Claro');
      });

      it('shows only the icon in the rail and names the control in a tooltip on the right', async () => {
        const { fixture, host } = await render();
        fixture.componentInstance.mode.set('dark');
        await fixture.whenStable();

        expect(aside(host).classList).not.toContain('is-expanded');
        expect(combobox(host).querySelector('svg')).not.toBeNull();
        expect(selectedLabel(host, 'shell-theme')).toBe('');
        const tooltip = fixture.debugElement.query(By.css('aside p-select')).injector.get(Tooltip);
        expect(tooltip.content).toBe('Tema');
        expect(tooltip.tooltipPosition).toBe('right');
        expect(themeSelect(host)).not.toBeNull();
      });

      it('lists every theme with an icon in the overlay', async () => {
        const { fixture } = await render();

        await openSelect(fixture, document.body, 'shell-theme');

        const options = Array.from(
          document.body.querySelectorAll<HTMLElement>('li[role="option"]'),
        );
        expect(options.map((option) => option.textContent?.trim())).toEqual([
          'Claro',
          'Oscuro',
          'Sistema',
        ]);
        expect(options.every((option) => option.querySelector('svg') !== null)).toBe(true);
      });

      const themeSelectComponent = (fixture: { debugElement: DebugElement }) =>
        fixture.debugElement.query(By.css('aside p-select')).componentInstance as Select;
      const once = (emitter: { subscribe(next: () => void): { unsubscribe(): void } }) =>
        new Promise<void>((resolve) => {
          const subscription = emitter.subscribe(() => {
            subscription.unsubscribe();
            resolve();
          });
        });
      const openOverlay = async (fixture: ComponentFixture<Harness>) => {
        const shown = once(themeSelectComponent(fixture).onShow);
        await openSelect(fixture, document.body, 'shell-theme');
        await shown;
      };
      const closeOverlay = async (fixture: ComponentFixture<Harness>) => {
        const hidden = once(themeSelectComponent(fixture).onHide);
        themeSelectComponent(fixture).hide();
        await hidden;
        await fixture.whenStable();
      };

      it('keeps a temporary expansion open while its overlay is open', async () => {
        const { fixture, host } = await render();
        await expand(fixture, host);
        await openOverlay(fixture);

        aside(host).dispatchEvent(new MouseEvent('mouseleave'));
        focusOut(firstLink(host), content(host));
        await fixture.whenStable();

        expect(toggle(host).getAttribute('aria-expanded')).toBe('true');
      });

      describe('when the overlay closes', () => {
        const hovering = (host: HTMLElement, value: boolean) => {
          const original = aside(host).matches.bind(aside(host));
          vi.spyOn(aside(host), 'matches').mockImplementation((selector: string) =>
            selector === ':hover' ? value : original(selector),
          );
        };
        const blur = () => (document.activeElement as HTMLElement | null)?.blur();

        it('collapses when the pointer left while it was open', async () => {
          const { fixture, host } = await render();
          await expand(fixture, host);
          hovering(host, false);
          await openOverlay(fixture);
          aside(host).dispatchEvent(new MouseEvent('mouseleave'));
          blur();

          await closeOverlay(fixture);

          expect(toggle(host).getAttribute('aria-expanded')).toBe('false');
        });

        it('collapses when the focus left while it was open', async () => {
          const { fixture, host } = await render();
          await expand(fixture, host);
          hovering(host, false);
          await openOverlay(fixture);
          focusOut(firstLink(host), content(host));
          blur();

          await closeOverlay(fixture);

          expect(toggle(host).getAttribute('aria-expanded')).toBe('false');
        });

        it('stays expanded while the pointer is still over the sidebar', async () => {
          const { fixture, host } = await render();
          await expand(fixture, host);
          hovering(host, true);
          await openOverlay(fixture);
          blur();

          await closeOverlay(fixture);

          expect(toggle(host).getAttribute('aria-expanded')).toBe('true');
        });

        it('stays expanded while the focus is inside the sidebar', async () => {
          const { fixture, host } = await render();
          await expand(fixture, host);
          hovering(host, false);
          await openOverlay(fixture);
          toggle(host).focus();

          await closeOverlay(fixture);

          expect(toggle(host).getAttribute('aria-expanded')).toBe('true');
        });

        it('keeps a pinned sidebar docked', async () => {
          const { fixture, host } = await render();
          await expand(fixture, host);
          pin(host)!.click();
          await fixture.whenStable();
          hovering(host, false);
          await openOverlay(fixture);
          aside(host).dispatchEvent(new MouseEvent('mouseleave'));
          blur();

          await closeOverlay(fixture);

          expect(toggle(host).getAttribute('aria-expanded')).toBe('true');
          expect(content(host).classList).toContain('is-docked');
        });

        it('collapses after picking an option while the pointer is outside', async () => {
          const { fixture, host } = await render();
          await expand(fixture, host);
          hovering(host, false);
          await openOverlay(fixture);
          aside(host).dispatchEvent(new MouseEvent('mouseleave'));
          const hidden = once(themeSelectComponent(fixture).onHide);

          await chooseOption(fixture, document.body, 'shell-theme', 'Oscuro');
          await hidden;
          await fixture.whenStable();

          expect(fixture.componentInstance.changes).toEqual(['dark']);
          expect(toggle(host).getAttribute('aria-expanded')).toBe('false');
        });
      });
    });
  });
});
