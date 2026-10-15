import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type { Shift } from '../../../core/api/api-types';
import { pen, shift } from '../../../testing/register-fixtures';
import { ShiftResult } from './shift-result';

@Component({
  imports: [ShiftResult],
  template: `<app-shift-result [shift]="shift" timeZone="America/Lima" />`,
})
class Host {
  shift: Shift = shift({
    status: 'CLOSED',
    closeKind: 'COUNTED',
    closedAt: '2026-10-13T22:00:00Z',
    countedCash: pen('140.00'),
    expectedCash: pen('150.00'),
    difference: pen('-10.00'),
    differenceNote: 'Faltó sencillo',
    totals: {
      cashReceived: pen('120.00'),
      changeGiven: pen('20.00'),
      voidedCash: pen('0.00'),
      cashIn: pen('30.00'),
      cashOut: pen('30.00'),
      byMethod: [
        { method: 'CASH', recorded: pen('120.00'), voided: pen('0.00'), net: pen('120.00') },
        { method: 'CARD', recorded: pen('80.00'), voided: pen('10.00'), net: pen('70.00') },
      ],
    },
  });
}

describe('ShiftResult', () => {
  async function render(change?: (host: Host) => void) {
    const fixture = TestBed.createComponent(Host);
    change?.(fixture.componentInstance);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  const text = (root: HTMLElement, testId: string) =>
    root.querySelector(`[data-testid="${testId}"]`)?.textContent ?? '';

  it('shows the expected cash, the count and the difference', async () => {
    const root = await render();

    expect(text(root, 'expected-cash')).toContain('150.00');
    expect(text(root, 'counted-cash')).toContain('140.00');
    expect(text(root, 'difference')).toContain('10.00');
    expect(text(root, 'difference-note')).toContain('Faltó sencillo');
  });

  it('shows the cash totals and a row per payment method', async () => {
    const root = await render();

    expect(text(root, 'cash-received')).toContain('120.00');
    expect(text(root, 'cash-in')).toContain('30.00');
    const rows = root.querySelectorAll('tbody tr');
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('Efectivo');
    expect(rows[1].textContent).toContain('Tarjeta');
    expect(rows[1].textContent).toContain('70.00');
  });

  it('says a shift was force-closed and why', async () => {
    const root = await render((host) => {
      host.shift = shift({ status: 'CLOSED', closeKind: 'FORCED', forceReason: 'Se fue sin cerrar', expectedCash: pen('150.00') });
    });

    expect(text(root, 'forced')).toContain('Se fue sin cerrar');
    expect(text(root, 'expected-cash')).toContain('150.00');
    expect(root.querySelector('[data-testid="counted-cash"]')).toBeNull();
  });
});
