import { TestBed } from '@angular/core/testing';
import { Landing } from './landing';

describe('Landing', () => {
  it('shows the store name as the main heading', async () => {
    const fixture = TestBed.createComponent(Landing);
    await fixture.whenStable();

    const heading = (fixture.nativeElement as HTMLElement).querySelector('h1');
    expect(heading?.textContent).toContain('Juguería');
  });
});
