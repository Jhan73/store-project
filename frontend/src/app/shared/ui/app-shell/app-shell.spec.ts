import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { IconHome, IconSettings } from '@tabler/icons-angular';
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
      (themeModeChange)="changes.push($event)"
      (logout)="logouts.set(logouts() + 1)"
    >
      <p>Contenido de la página</p>
    </app-shell>
  `,
})
class Harness {
  readonly items = items;
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
  async function render() {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Harness);
    await fixture.whenStable();
    return { fixture, host: fixture.nativeElement as HTMLElement };
  }

  it('shows the brand, the role and the projected page', async () => {
    const { host } = await render();

    expect(host.querySelector('header')?.textContent).toContain('Juguería');
    expect(host.querySelector('header')?.textContent).toContain('Cajero');
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
    await fixture.whenStable();

    const buttons = Array.from(host.querySelectorAll<HTMLButtonElement>('[role="group"] button'));
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

    const [light, dark, system] = Array.from(
      host.querySelectorAll<HTMLButtonElement>('[role="group"] button'),
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
});
