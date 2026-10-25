import { line, order } from '../../../testing/board-fixtures';
import { groupLines, indicatorsOf } from './board-lines';

const noMarkers = () => '';

describe('groupLines', () => {
  it('merges identical lines into one group that adds their quantities', () => {
    const groups = groupLines(
      [line({ id: 'a', quantity: 1 }), line({ id: 'b', quantity: 2 }), line({ id: 'c', quantity: 1 })],
      noMarkers,
    );

    expect(groups).toHaveLength(1);
    expect(groups[0].quantity).toBe(4);
    expect(groups[0].lines.map((item) => item.id)).toEqual(['a', 'b', 'c']);
    expect(groups[0].id).toBe('a');
  });

  it('keeps the position of the first line of each group', () => {
    const groups = groupLines(
      [
        line({ id: 'a', productId: 'mango' }),
        line({ id: 'b', productId: 'fresa' }),
        line({ id: 'c', productId: 'mango' }),
      ],
      noMarkers,
    );

    expect(groups.map((group) => group.id)).toEqual(['a', 'b']);
    expect(groups[0].quantity).toBe(2);
  });

  it.each([
    ['modifiers', { modifiersText: 'Sin azúcar' }],
    ['note', { note: 'Sin hielo' }],
    ['allergy', { allergy: true }],
    ['state', { state: 'READY' as const }],
    ['sold-out flag', { flaggedUnavailable: true }],
    ['comp', { comped: true }],
    ['product', { productId: 'other' }],
  ])('never merges lines that differ in %s', (_name, change) => {
    const groups = groupLines([line({ id: 'a' }), line({ id: 'b', ...change })], noMarkers);

    expect(groups).toHaveLength(2);
  });

  it('does not hide two different notes behind one entry', () => {
    const groups = groupLines(
      [line({ id: 'a', note: 'Sin hielo' }), line({ id: 'b', note: 'Con hielo' }), line({ id: 'c', note: 'Sin hielo' })],
      noMarkers,
    );

    expect(groups.map((group) => [group.id, group.quantity])).toEqual([
      ['a', 2],
      ['b', 1],
    ]);
  });

  it('keeps a line with its own label apart from the others', () => {
    const groups = groupLines([line({ id: 'a' }), line({ id: 'b' })], (item) => (item.id === 'b' ? 'm1' : ''));

    expect(groups).toHaveLength(2);
  });
});

describe('indicatorsOf', () => {
  it('flags an allergy and a note on any live line', () => {
    const value = order({
      id: 'o',
      lines: [line({ id: 'a', allergy: true }), line({ id: 'b', note: 'Sin hielo' })],
    });

    expect(indicatorsOf(value)).toEqual({ allergy: true, note: true });
  });

  it('flags nothing for plain lines, and ignores modifiers', () => {
    const value = order({ id: 'o', lines: [line({ id: 'a', modifiersText: 'Sin azúcar' })] });

    expect(indicatorsOf(value)).toEqual({ allergy: false, note: false });
  });

  it('ignores a voided line', () => {
    const value = order({ id: 'o', lines: [line({ id: 'a', allergy: true, note: 'Maní', state: 'VOIDED' })] });

    expect(indicatorsOf(value)).toEqual({ allergy: false, note: false });
  });

  it('treats a blank note as no note', () => {
    const value = order({ id: 'o', lines: [line({ id: 'a', note: '   ' })] });

    expect(indicatorsOf(value).note).toBe(false);
  });
});
