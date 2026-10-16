import type { MenuModifierGroup, MenuProduct } from '../../../core/api/api-types';
import {
  groupIssue,
  isValidSelection,
  toggleOption,
  unitPriceOf,
} from './configurator-rules';

const pen = (amount: string) => ({ amount, currency: 'PEN' });

function option(id: string, delta = '0.00', available = true) {
  return { id, name: id, available, allergens: [], priceDelta: pen(delta) };
}

function group(
  id: string,
  options: string[],
  rules: { required?: boolean; min?: number; max?: number } = {},
): MenuModifierGroup {
  return {
    id,
    name: id,
    required: rules.required ?? false,
    minChoices: rules.min ?? 0,
    maxChoices: rules.max ?? 1,
    options: options.map((name) => option(name)),
  };
}

function product(groups: MenuModifierGroup[], price = '10.00'): MenuProduct {
  return {
    id: 'p1',
    name: 'Naranja',
    available: true,
    description: null,
    imageUrl: null,
    allergens: [],
    price: pen(price),
    modifierGroups: groups,
  };
}

describe('toggleOption', () => {
  it('adds an option below the maximum', () => {
    const toppings = group('toppings', ['a', 'b', 'c'], { max: 2 });

    expect(toggleOption(toppings, ['a'], 'b')).toEqual(['a', 'b']);
  });

  it('removes an option that is already chosen', () => {
    const toppings = group('toppings', ['a', 'b'], { max: 2 });

    expect(toggleOption(toppings, ['a', 'b'], 'a')).toEqual(['b']);
  });

  it('refuses a choice beyond the maximum', () => {
    const toppings = group('toppings', ['a', 'b', 'c'], { max: 2 });

    expect(toggleOption(toppings, ['a', 'b'], 'c')).toEqual(['a', 'b']);
  });

  it('replaces the choice of a single-choice group and leaves other groups alone', () => {
    const sweetener = group('sweetener', ['honey', 'sugar']);
    const size = group('size', ['small', 'large']);

    expect(toggleOption(sweetener, ['honey', 'large'], 'sugar')).toEqual(['large', 'sugar']);
    expect(size.options).toHaveLength(2);
  });

  it('ignores an unavailable option', () => {
    const sweetener = { ...group('sweetener', ['honey']), options: [option('honey', '0.00', false)] };

    expect(toggleOption(sweetener, [], 'honey')).toEqual([]);
  });
});

describe('groupIssue', () => {
  it('asks for one choice from a required group', () => {
    const size = group('size', ['small', 'large'], { required: true });

    expect(groupIssue(size, [])).toBe('MIN');
    expect(groupIssue(size, ['small'])).toBeNull();
  });

  it('asks for the minimum of a group that allows several choices', () => {
    const toppings = group('toppings', ['a', 'b', 'c'], { required: true, min: 2, max: 3 });

    expect(groupIssue(toppings, ['a'])).toBe('MIN');
    expect(groupIssue(toppings, ['a', 'b'])).toBeNull();
  });

  it('does not ask for anything from an optional group', () => {
    expect(groupIssue(group('extras', ['a']), [])).toBeNull();
  });

  it('flags more choices than the maximum', () => {
    const size = group('size', ['small', 'large']);

    expect(groupIssue(size, ['small', 'large'])).toBe('MAX');
  });

  it('counts only the options of its own group', () => {
    const size = group('size', ['small', 'large'], { required: true });

    expect(groupIssue(size, ['honey'])).toBe('MIN');
  });
});

describe('isValidSelection', () => {
  it('is valid when every group meets its rules', () => {
    const p = product([group('size', ['small', 'large'], { required: true }), group('extras', ['x'])]);

    expect(isValidSelection(p, ['large'])).toBe(true);
    expect(isValidSelection(p, [])).toBe(false);
  });

  it('is valid for a product without modifier groups', () => {
    expect(isValidSelection(product([]), [])).toBe(true);
  });
});

describe('unitPriceOf', () => {
  it('adds the price delta of the chosen options in minor units', () => {
    const extras = {
      ...group('extras', [], { max: 2 }),
      options: [option('honey', '1.50'), option('ginger', '0.10'), option('ice', '0.00')],
    };

    expect(unitPriceOf(product([extras], '10.00'), ['honey', 'ginger'])).toEqual(pen('11.60'));
  });

  it('is the product price when nothing is chosen', () => {
    expect(unitPriceOf(product([]), [])).toEqual(pen('10.00'));
  });
});
