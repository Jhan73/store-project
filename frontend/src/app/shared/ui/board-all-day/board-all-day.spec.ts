import { ComponentFixture, TestBed } from '@angular/core/testing';
import type { BoardAllDay } from '../../../core/api/api-types';
import { BoardAllDayView } from './board-all-day';

describe('BoardAllDayView', () => {
  let fixture: ComponentFixture<BoardAllDayView>;

  function render(allDay: BoardAllDay) {
    fixture = TestBed.createComponent(BoardAllDayView);
    fixture.componentRef.setInput('allDay', allDay);
    fixture.detectChanges();
  }

  const host = () => fixture.nativeElement as HTMLElement;

  const group = (productId: string, displayName: string, quantity: number, modifiersText: string | null = null) => ({
    productId,
    displayName,
    modifiersText,
    quantity,
  });

  it('counts identical items together with their modifiers', () => {
    render({
      groups: [group('p1', 'Jugo de naranja', 6, '500 ml'), group('p2', 'Sándwich', 2)],
      individual: [],
    });

    const rows = Array.from(host().querySelectorAll('[data-testid="group"]')).map((row) =>
      row.textContent?.replace(/\s+/g, ' ').trim(),
    );
    expect(rows).toEqual(['6× Jugo de naranja (500 ml)', '2× Sándwich']);
  });

  it('lists lines with a note or an allergy one by one with their order number', () => {
    render({
      groups: [],
      individual: [
        {
          lineId: 'l1',
          boardOrderId: 'o1',
          shortNumber: 'W-042',
          productId: 'p1',
          displayName: 'Jugo de fresa',
          quantity: 1,
          modifiersText: null,
          note: 'Sin hielo',
          allergy: false,
        },
        {
          lineId: 'l2',
          boardOrderId: 'o2',
          shortNumber: 'L-017',
          productId: 'p1',
          displayName: 'Jugo de piña',
          quantity: 2,
          modifiersText: '500 ml',
          note: null,
          allergy: true,
        },
      ],
    });

    const first = host().querySelector('[data-testid="individual-l1"]')!;
    expect(first.textContent).toContain('W-042');
    expect(first.textContent).toContain('Sin hielo');
    expect(first.querySelector('[data-testid="allergy"]')).toBeNull();
    const second = host().querySelector('[data-testid="individual-l2"]')!;
    expect(second.textContent).toContain('2× Jugo de piña');
    expect(second.querySelector('[data-testid="allergy"]')?.textContent).toContain('Alergia');
  });

  it('says there is nothing to prepare when both lists are empty', () => {
    render({ groups: [], individual: [] });

    expect(host().textContent).toContain('No hay nada pendiente');
  });
});
