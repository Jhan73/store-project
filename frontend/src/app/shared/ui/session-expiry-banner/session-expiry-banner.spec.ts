import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { SessionExpiryBanner } from './session-expiry-banner';

@Component({
  imports: [SessionExpiryBanner],
  template: '<app-session-expiry-banner [daysLeft]="days()" [compact]="compact()" />',
})
class Host {
  readonly days = signal(2);
  readonly compact = signal(false);
}

describe('SessionExpiryBanner', () => {
  async function render(days: number, compact = false) {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.days.set(days);
    fixture.componentInstance.compact.set(compact);
    await fixture.whenStable();
    return (fixture.nativeElement as HTMLElement).querySelector('[data-testid="session-expiry-banner"]');
  }

  it('says how many days are left', async () => {
    expect((await render(2))?.textContent).toContain('2 días');
  });

  it('uses the singular for a single day', async () => {
    const text = (await render(1))?.textContent ?? '';

    expect(text).toContain('1 día');
    expect(text).not.toContain('1 días');
  });

  it('says less than a day when no whole day is left', async () => {
    expect((await render(0))?.textContent).toContain('menos de un día');
  });

  it('is announced politely and tells someone to sign in again', async () => {
    const banner = await render(2);

    expect(banner?.getAttribute('role')).toBe('status');
    expect(banner?.textContent).toContain('iniciar sesión');
  });

  it('is full size unless asked to be compact, and compact keeps the same words', async () => {
    expect((await render(2))?.classList).not.toContain('compact');

    const compact = await render(2, true);

    expect(compact?.classList).toContain('compact');
    expect(compact?.textContent).toContain('2 días');
  });
});
