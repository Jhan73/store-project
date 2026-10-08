import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Landing } from './landing';

describe('Landing', () => {
  async function render() {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(Landing);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the store name as the main heading', async () => {
    const host = await render();

    expect(host.querySelector('h1')?.textContent).toContain('Juguería');
  });

  it('links staff to their area', async () => {
    const host = await render();

    expect(host.querySelector('a[href="/staff"]')?.textContent).toContain('Acceso del personal');
  });

  it('decorates the staff link with a hidden icon and a tooltip', async () => {
    const host = await render();
    const link = host.querySelector('a[href="/staff"]');

    expect(link?.querySelector('svg')?.closest('tabler-icon')?.getAttribute('aria-hidden')).toBe('true');
    expect(link?.getAttribute('pTooltip')).toBe('Entrar a la zona del personal');
    expect(link?.textContent?.trim()).toBe('Acceso del personal');
  });
});
