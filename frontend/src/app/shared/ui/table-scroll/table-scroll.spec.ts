import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TableScroll } from './table-scroll';

@Component({
  selector: 'app-table-scroll-harness',
  imports: [TableScroll],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-table-scroll label="Listado de mesas">
      <table>
        <tbody>
          <tr>
            <td>Mesa 1</td>
          </tr>
        </tbody>
      </table>
    </app-table-scroll>
  `,
})
class Harness {}

describe('TableScroll', () => {
  async function render() {
    const fixture = TestBed.createComponent(Harness);
    await fixture.whenStable();
    const host = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(
      'app-table-scroll',
    )!;
    return host;
  }

  it('is a focusable labelled region so keyboard users can scroll it', async () => {
    const host = await render();

    expect(host.getAttribute('role')).toBe('region');
    expect(host.getAttribute('tabindex')).toBe('0');
    expect(host.getAttribute('aria-label')).toBe('Listado de mesas');
  });

  it('is the containing block of absolute descendants so they cannot widen the page', async () => {
    const host = await render();

    const style = getComputedStyle(host);

    expect(style.overflowX).toBe('auto');
    expect(style.position).toBe('relative');
  });

  it('projects the table inside the scroll container', async () => {
    const host = await render();

    expect(host.querySelector('table td')?.textContent).toBe('Mesa 1');
  });
});
