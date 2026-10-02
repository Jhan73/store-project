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
});
