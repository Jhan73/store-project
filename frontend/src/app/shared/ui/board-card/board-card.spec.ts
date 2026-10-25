import { ComponentFixture, TestBed } from '@angular/core/testing';
import type { BoardOrder } from '../../../core/api/api-types';
import { line, marker, order } from '../../../testing/board-fixtures';
import { BoardCard } from './board-card';

describe('BoardCard', () => {
  let fixture: ComponentFixture<BoardCard>;

  function render(
    value: BoardOrder,
    options: {
      minutes?: number;
      level?: 'normal' | 'warning' | 'late';
      enabled?: boolean;
      pending?: string[];
      queued?: string[];
      timeZone?: string;
      mayHandOver?: boolean;
    } = {},
  ) {
    fixture = TestBed.createComponent(BoardCard);
    fixture.componentRef.setInput('order', value);
    fixture.componentRef.setInput('minutes', options.minutes ?? 3);
    fixture.componentRef.setInput('level', options.level ?? 'normal');
    fixture.componentRef.setInput('enabled', options.enabled ?? true);
    fixture.componentRef.setInput('pending', new Set(options.pending ?? []));
    fixture.componentRef.setInput('queued', new Set(options.queued ?? []));
    fixture.componentRef.setInput('timeZone', options.timeZone ?? 'America/Lima');
    fixture.componentRef.setInput('mayHandOver', options.mayHandOver ?? true);
    fixture.detectChanges();
  }

  const host = () => fixture.nativeElement as HTMLElement;
  const text = () => host().textContent ?? '';
  const button = (testId: string) =>
    host().querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`);
  const emitted = (name: 'start' | 'readyOrder' | 'readyLine' | 'undo' | 'handover' | 'recall' | 'flag' | 'acknowledge') => {
    const spy = vi.fn();
    fixture.componentInstance[name].subscribe(spy);
    return spy;
  };

  describe('header', () => {
    it('shows the short number, the channel, the label and the elapsed minutes as text', () => {
      render(order({ id: 'o1', shortNumber: 'W-042', channel: 'PICKUP', label: 'Ana' }), { minutes: 12 });

      expect(text()).toContain('W-042');
      expect(text()).toContain('Recojo');
      expect(text()).toContain('Ana');
      expect(host().querySelector('[data-testid="elapsed"]')?.textContent).toContain('12 min');
    });

    it('exposes the short number on its own so screens can be matched by it', () => {
      render(order({ id: 'o1', shortNumber: 'L-018', channel: 'COUNTER', label: 'Ana' }));

      expect(host().querySelector('[data-testid="short-number"]')?.textContent?.trim()).toBe('L-018');
    });

    it.each([
      ['TABLE', 'Mesa'],
      ['COUNTER', 'Mostrador'],
      ['PICKUP', 'Recojo'],
      ['DELIVERY', 'Delivery'],
    ] as const)('names the %s channel', (channel, name) => {
      render(order({ id: 'o1', channel }));

      expect(text()).toContain(name);
    });

    it('colors the header by level and always says the level in words, never only by color', () => {
      render(order({ id: 'o1' }), { minutes: 11, level: 'late' });
      expect(host().querySelector('header')?.classList.contains('late')).toBe(true);
      expect(host().querySelector('[data-testid="level"]')?.textContent).toContain('Atrasado');
      expect(host().querySelector('[data-testid="elapsed"]')?.textContent).toContain('11 min');

      render(order({ id: 'o1' }), { minutes: 6, level: 'warning' });
      expect(host().querySelector('header')?.classList.contains('warning')).toBe(true);
      expect(host().querySelector('[data-testid="level"]')?.textContent).toContain('Atención');
    });

    it('adds no level word while the card is on time', () => {
      render(order({ id: 'o1' }), { level: 'normal' });

      expect(host().querySelector('[data-testid="level"]')).toBeNull();
      expect(host().querySelector('header')?.classList.contains('normal')).toBe(true);
    });

    it('shows the scheduled time of a scheduled order', () => {
      render(order({ id: 'o1', fireAt: '2026-10-13T15:30:00Z' }));

      expect(host().querySelector('[data-testid="scheduled"]')?.textContent).toMatch(/Programado/);
    });

    it('prints the scheduled time in the store zone, not the device one', () => {
      const scheduled = order({ id: 'o1', fireAt: '2026-10-13T15:30:00Z' });

      render(scheduled, { timeZone: 'America/Lima' });
      expect(host().querySelector('[data-testid="scheduled"]')?.textContent).toContain('10:30');

      render(scheduled, { timeZone: 'Asia/Tokyo' });
      expect(host().querySelector('[data-testid="scheduled"]')?.textContent).toContain('12:30');
    });

    it('shows no scheduled time otherwise', () => {
      render(order({ id: 'o1' }));

      expect(host().querySelector('[data-testid="scheduled"]')).toBeNull();
    });
  });

  describe('lines', () => {
    it('lists quantity, name, modifiers and note', () => {
      render(
        order({
          id: 'o1',
          lines: [
            line({
              id: 'l1',
              quantity: 2,
              displayName: 'Jugo de fresa',
              modifiersText: 'Sin azúcar, 500 ml',
              note: 'Sin hielo',
            }),
          ],
        }),
      );

      expect(text()).toContain('2×');
      expect(text()).toContain('Jugo de fresa');
      expect(text()).toContain('Sin azúcar, 500 ml');
      expect(text()).toContain('Sin hielo');
    });

    it('makes an allergy line stand out with a word, not only a color', () => {
      render(order({ id: 'o1', lines: [line({ id: 'l1', allergy: true, note: 'Maní' })] }));

      const item = host().querySelector('[data-testid="line-l1"]')!;
      expect(item.classList.contains('allergy')).toBe(true);
      expect(item.querySelector('[data-testid="allergy"]')?.textContent).toContain('Alergia');
    });

    it('marks a ready line in words', () => {
      render(order({ id: 'o1', status: 'PREPARING', lines: [line({ id: 'l1', state: 'READY' })] }));

      expect(host().querySelector('[data-testid="line-state-l1"]')?.textContent).toContain('Lista');
      expect(button('ready-line-l1')).toBeNull();
    });

    it('marks a flagged line as sold out and takes its Ready away', () => {
      render(
        order({ id: 'o1', status: 'PREPARING', lines: [line({ id: 'l1', flaggedUnavailable: true })] }),
      );

      expect(host().querySelector('[data-testid="line-state-l1"]')?.textContent).toContain('Agotado');
      expect(button('ready-line-l1')).toBeNull();
      expect(button('flag-l1')).toBeNull();
    });

    it('shows a voided line struck through with the word', () => {
      render(
        order({
          id: 'o1',
          status: 'PREPARING',
          lines: [line({ id: 'l1', state: 'VOIDED' })],
          markers: [marker({ id: 'm1', boardOrderId: 'o1', kind: 'VOID', lineId: 'l1' })],
        }),
      );

      const item = host().querySelector('[data-testid="line-l1"]')!;
      expect(item.classList.contains('voided')).toBe(true);
      expect(item.textContent).toContain('ANULADO');
      expect(button('ready-line-l1')).toBeNull();
    });
  });

  describe('comped lines', () => {
    it('keeps preparing a comped line and labels it as a courtesy', () => {
      render(
        order({ id: 'o1', status: 'PREPARING', lines: [line({ id: 'l1', comped: true })] }),
      );

      expect(host().querySelector('[data-testid="comp-l1"]')?.textContent).toContain('Cortesía');
      expect(button('ready-line-l1')).not.toBeNull();
    });

    it('labels a comped line that is already ready with both words', () => {
      render(
        order({ id: 'o1', status: 'PREPARING', lines: [line({ id: 'l1', state: 'READY', comped: true })] }),
      );

      expect(host().querySelector('[data-testid="line-state-l1"]')?.textContent).toContain('Lista');
      expect(host().querySelector('[data-testid="comp-l1"]')?.textContent).toContain('Cortesía');
    });

    it('shows no courtesy label on an ordinary line', () => {
      render(order({ id: 'o1', status: 'PREPARING' }));

      expect(host().querySelector('[data-testid^="comp-"]')).toBeNull();
    });
  });

  describe('actions', () => {
    it('starts a waiting order', () => {
      render(order({ id: 'o1', status: 'WAITING' }));
      const start = emitted('start');

      button('start')!.click();

      expect(start).toHaveBeenCalledWith('o1');
      expect(button('ready-order')).toBeNull();
    });

    it('offers Ready for a line and for the whole order once preparing', () => {
      render(order({ id: 'o1', status: 'PREPARING', lines: [line({ id: 'l1' })] }));
      const readyLine = emitted('readyLine');
      const readyOrder = emitted('readyOrder');

      button('ready-line-l1')!.click();
      button('ready-order')!.click();

      expect(readyLine).toHaveBeenCalledWith('l1');
      expect(readyOrder).toHaveBeenCalledWith('o1');
      expect(button('start')).toBeNull();
    });

    it('does not offer line Ready before the order is started', () => {
      render(order({ id: 'o1', status: 'WAITING', lines: [line({ id: 'l1' })] }));

      expect(button('ready-line-l1')).toBeNull();
    });

    it('flags a pending line as sold out', () => {
      render(order({ id: 'o1', status: 'PREPARING', lines: [line({ id: 'l1' })] }));
      const flag = emitted('flag');

      button('flag-l1')!.click();

      expect(flag).toHaveBeenCalledWith('l1');
    });

    it('swaps the order Ready for Undo while it waits out the window', () => {
      render(order({ id: 'o1', status: 'PREPARING' }), { queued: ['o1'] });
      const undo = emitted('undo');

      expect(button('ready-order')).toBeNull();
      button('undo-order')!.click();

      expect(undo).toHaveBeenCalledWith('o1');
    });

    it('swaps a line Ready for Undo while it waits out the window', () => {
      render(order({ id: 'o1', status: 'PREPARING', lines: [line({ id: 'l1' })] }), { queued: ['l1'] });
      const undo = emitted('undo');

      expect(button('ready-line-l1')).toBeNull();
      expect(host().querySelector('[data-testid="line-state-l1"]')?.textContent).toContain('Lista');
      button('undo-line-l1')!.click();

      expect(undo).toHaveBeenCalledWith('l1');
    });

    describe('keyboard focus', () => {
      const focused = () => document.activeElement?.getAttribute('data-testid');

      it('moves focus from Ready to Undo and back for the whole order', () => {
        render(order({ id: 'o1', status: 'PREPARING', lines: [line({ id: 'l1' })] }));
        button('ready-order')!.focus();
        button('ready-order')!.click();

        fixture.componentRef.setInput('queued', new Set(['o1']));
        fixture.detectChanges();
        expect(focused()).toBe('undo-order');

        button('undo-order')!.click();
        fixture.componentRef.setInput('queued', new Set());
        fixture.detectChanges();
        expect(focused()).toBe('ready-order');
      });

      it('moves focus from Ready to Undo and back for a line', () => {
        render(order({ id: 'o1', status: 'PREPARING', lines: [line({ id: 'l1' })] }));
        button('ready-line-l1')!.focus();
        button('ready-line-l1')!.click();

        fixture.componentRef.setInput('queued', new Set(['l1']));
        fixture.detectChanges();
        expect(focused()).toBe('undo-line-l1');

        button('undo-line-l1')!.click();
        fixture.componentRef.setInput('queued', new Set());
        fixture.detectChanges();
        expect(focused()).toBe('ready-line-l1');
      });

      it('leaves focus alone when the queue changes without a click on this card', () => {
        render(order({ id: 'o1', status: 'PREPARING', lines: [line({ id: 'l1' })] }));
        button('flag-l1')!.focus();

        fixture.componentRef.setInput('queued', new Set(['o1']));
        fixture.detectChanges();

        expect(document.activeElement).not.toBe(button('undo-order'));
      });
    });

    it('treats the lines as ready while the whole order waits out the window', () => {
      render(order({ id: 'o1', status: 'PREPARING', lines: [line({ id: 'l1' })] }), { queued: ['o1'] });

      expect(button('ready-line-l1')).toBeNull();
      expect(host().querySelector('[data-testid="line-state-l1"]')?.textContent).toContain('Lista');
    });

    it('takes the run-out flag away while the line or the whole order waits out the undo window', () => {
      const value = order({ id: 'o1', status: 'PREPARING', lines: [line({ id: 'l1' })] });

      render(value, { queued: ['l1'] });
      expect(button('flag-l1')).toBeNull();

      render(value, { queued: ['o1'] });
      expect(button('flag-l1')).toBeNull();

      render(value, { queued: [] });
      expect(button('flag-l1')).not.toBeNull();
    });

    it('hands over or recalls a ready order', () => {
      render(order({ id: 'o1', status: 'READY', lines: [line({ id: 'l1', state: 'READY' })] }));
      const handover = emitted('handover');
      const recall = emitted('recall');

      button('handover')!.click();
      button('recall')!.click();

      expect(handover).toHaveBeenCalledWith('o1');
      expect(recall).toHaveBeenCalledWith('o1');
    });

    it('disables every action while the actions are off', () => {
      render(order({ id: 'o1', status: 'PREPARING', lines: [line({ id: 'l1' })] }), { enabled: false });

      for (const id of ['ready-order', 'ready-line-l1', 'flag-l1']) {
        expect(button(id)!.disabled).toBe(true);
      }
    });

    it('disables the actions of an order and of a line while their request is in flight', () => {
      render(order({ id: 'o1', status: 'PREPARING', lines: [line({ id: 'l1' }), line({ id: 'l2', productId: 'p2' })] }), {
        pending: ['l1'],
      });

      expect(button('ready-line-l1')!.disabled).toBe(true);
      expect(button('ready-line-l2')!.disabled).toBe(false);
      expect(button('ready-order')!.disabled).toBe(false);

      render(order({ id: 'o1', status: 'WAITING' }), { pending: ['o1'] });
      expect(button('start')!.disabled).toBe(true);
    });
  });

  describe('markers', () => {
    it.each([
      ['NEW', 'NUEVO'],
      ['MODIFIED', 'MODIFICADO'],
      ['VOID', 'ANULADO'],
    ] as const)('shows the %s label until it is acknowledged', (kind, word) => {
      render(order({ id: 'o1', markers: [marker({ id: 'm1', boardOrderId: 'o1', kind })] }));
      const acknowledge = emitted('acknowledge');

      expect(host().querySelector('[data-testid="marker-m1"]')?.textContent).toContain(word);
      button('ack-m1')!.click();

      expect(acknowledge).toHaveBeenCalledWith('m1');
    });

    it('hides a marker that was already acknowledged', () => {
      render(
        order({
          id: 'o1',
          markers: [marker({ id: 'm1', boardOrderId: 'o1', acknowledgedAt: '2026-10-13T10:00:00Z' })],
        }),
      );

      expect(host().querySelector('[data-testid="marker-m1"]')).toBeNull();
    });

    it('shows the label of a line next to that line', () => {
      render(
        order({
          id: 'o1',
          status: 'PREPARING',
          lines: [line({ id: 'l1' }), line({ id: 'l2' })],
          markers: [marker({ id: 'm1', boardOrderId: 'o1', kind: 'MODIFIED', lineId: 'l2' })],
        }),
      );

      expect(host().querySelector('[data-testid="line-l2"] [data-testid="marker-m1"]')).not.toBeNull();
      expect(host().querySelector('[data-testid="line-l1"] [data-testid="marker-m1"]')).toBeNull();
    });

    it('disables the acknowledgement while the actions are off', () => {
      render(order({ id: 'o1', markers: [marker({ id: 'm1', boardOrderId: 'o1' })] }), { enabled: false });

      expect(button('ack-m1')!.disabled).toBe(true);
    });
  });

  describe('order indicators', () => {
    const indicator = (name: 'allergy' | 'note') => host().querySelector(`[data-testid="indicator-${name}"]`);

    it('flags an allergy in red with the word, apart from the line badge', () => {
      render(order({ id: 'o1', lines: [line({ id: 'l1', allergy: true })] }));

      expect(indicator('allergy')?.textContent).toContain('Alergia');
      expect(indicator('allergy')?.classList.contains('alert-allergy')).toBe(true);
      expect(indicator('note')).toBeNull();
    });

    it('flags a free note in amber with the word Nota', () => {
      render(order({ id: 'o1', lines: [line({ id: 'l1', note: 'Sin hielo' })] }));

      expect(indicator('note')?.textContent).toContain('Nota');
      expect(indicator('note')?.classList.contains('alert-note')).toBe(true);
      expect(indicator('allergy')).toBeNull();
    });

    it('shows both when the order has both', () => {
      render(
        order({ id: 'o1', lines: [line({ id: 'l1', allergy: true }), line({ id: 'l2', productId: 'p2', note: 'x' })] }),
      );

      expect(indicator('allergy')).not.toBeNull();
      expect(indicator('note')).not.toBeNull();
    });

    it('shows the chosen modifiers without any alert', () => {
      render(order({ id: 'o1', lines: [line({ id: 'l1', modifiersText: 'Sin azúcar' })] }));

      expect(text()).toContain('Sin azúcar');
      expect(indicator('allergy')).toBeNull();
      expect(indicator('note')).toBeNull();
    });
  });

  describe('repeated lines', () => {
    const three = () =>
      order({
        id: 'o1',
        status: 'PREPARING',
        lines: [
          line({ id: 'a', displayName: 'Mango' }),
          line({ id: 'b', displayName: 'Mango' }),
          line({ id: 'c', displayName: 'Mango' }),
        ],
      });

    it('shows identical lines once with the total quantity', () => {
      render(three());

      expect(host().querySelectorAll('[data-testid^="line-"]:not([data-testid^="line-state-"])')).toHaveLength(1);
      expect(host().querySelector('[data-testid="line-a"]')?.textContent).toContain('3×');
    });

    it('keeps lines with different notes or allergies separate', () => {
      render(
        order({
          id: 'o1',
          status: 'PREPARING',
          lines: [
            line({ id: 'a', displayName: 'Mango' }),
            line({ id: 'b', displayName: 'Mango', note: 'Sin hielo' }),
            line({ id: 'c', displayName: 'Mango', allergy: true }),
          ],
        }),
      );

      expect(host().querySelector('[data-testid="line-a"]')?.textContent).not.toContain('3×');
      expect(host().querySelector('[data-testid="line-b"]')?.textContent).toContain('Sin hielo');
      expect(host().querySelector('[data-testid="line-c"] [data-testid="allergy"]')).not.toBeNull();
    });

    it('marks every line of the group ready with one tap', () => {
      render(three());
      const readyLine = emitted('readyLine');

      button('ready-line-a')!.click();

      expect(readyLine.mock.calls.map(([id]) => id)).toEqual(['a', 'b', 'c']);
    });

    it('flags every line of the group as sold out', () => {
      render(three());
      const flag = emitted('flag');

      button('flag-a')!.click();

      expect(flag.mock.calls.map(([id]) => id)).toEqual(['a', 'b', 'c']);
    });

    it('offers Undo for the group once all its lines wait out the window', () => {
      render(three(), { queued: ['a', 'b', 'c'] });
      const undo = emitted('undo');

      expect(button('ready-line-a')).toBeNull();
      button('undo-line-a')!.click();

      expect(undo.mock.calls.map(([id]) => id)).toEqual(['a', 'b', 'c']);
    });

    it('disables the group while any of its lines has a request in flight', () => {
      render(three(), { pending: ['b'] });

      expect(button('ready-line-a')!.disabled).toBe(true);
    });
  });

  describe('detail', () => {
    const detail = () => document.body.querySelector<HTMLElement>('[data-testid="detail-dialog"]');
    const withNotes = () =>
      order({
        id: 'o1',
        shortNumber: 'L-021',
        lines: [
          line({ id: 'a', displayName: 'Mango', quantity: 2, modifiersText: '500 ml, sin azúcar', note: 'Sin hielo' }),
          line({ id: 'b', displayName: 'Fresa', allergy: true }),
        ],
      });

    async function open() {
      render(withNotes());
      button('detail')!.click();
      await fixture.whenStable();
    }

    it('opens from the card with the lines, modifiers, notes and allergies', async () => {
      await open();

      expect(detail()).not.toBeNull();
      const content = document.body.querySelector('[role="dialog"]')!.textContent!;
      for (const expected of ['L-021', 'Mango', '500 ml, sin azúcar', 'Sin hielo', 'Fresa', 'Alergia']) {
        expect(content).toContain(expected);
      }
      expect(button('detail')!.getAttribute('aria-haspopup')).toBe('dialog');
    });

    it('is closed until it is opened', () => {
      render(withNotes());

      expect(detail()).toBeNull();
    });

    it('closes on Escape and gives the focus back to its button', async () => {
      await open();

      detail()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      await fixture.whenStable();

      expect(detail()).toBeNull();
      expect(document.activeElement).toBe(button('detail'));
    });

    it('closes from its close control', async () => {
      await open();

      document.body.querySelector<HTMLElement>('.p-dialog-close-button')!.click();
      await fixture.whenStable();

      expect(detail()).toBeNull();
    });

    it('carries no price or payment text', async () => {
      await open();

      expect(detail()!.textContent).not.toMatch(/S\/|PEN|total|precio|pago/i);
    });
  });

  describe('floor actions', () => {
    it('offers hand over and recall by default', () => {
      render(order({ id: 'o1', status: 'READY', lines: [line({ id: 'l1', state: 'READY' })] }));

      expect(button('handover')).not.toBeNull();
      expect(button('recall')).not.toBeNull();
    });

    it('hides hand over and recall where the device may not hand over', () => {
      render(order({ id: 'o1', status: 'READY', lines: [line({ id: 'l1', state: 'READY' })] }), {
        mayHandOver: false,
      });

      expect(button('handover')).toBeNull();
      expect(button('recall')).toBeNull();
    });

    it('still lets that device prepare', () => {
      render(order({ id: 'o1', status: 'PREPARING', lines: [line({ id: 'l1' })] }), { mayHandOver: false });

      expect(button('ready-order')).not.toBeNull();
      expect(button('ready-line-l1')).not.toBeNull();
    });
  });
});
