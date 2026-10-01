import { addMoney, formatMoney, fromMinorUnits, toMinorUnits } from './money';

describe('toMinorUnits', () => {
  it.each([
    ['12.50', 1250],
    ['0.07', 7],
    ['0.00', 0],
    ['1000000.99', 100000099],
    ['-3.20', -320],
    ['-0.05', -5],
  ])('parses %s as %i', (amount, minor) => {
    expect(toMinorUnits({ amount, currency: 'PEN' })).toBe(minor);
  });

  it.each(['12.5', '12', '12.505', '1,50', 'abc', '', '+1.00', ' 1.00', '1.00 '])(
    'rejects the malformed amount "%s"',
    (amount) => {
      expect(() => toMinorUnits({ amount, currency: 'PEN' })).toThrow();
    },
  );

  it('rejects amounts beyond the safe integer range', () => {
    expect(() => toMinorUnits({ amount: '99999999999999999.99', currency: 'PEN' })).toThrow();
  });
});

describe('fromMinorUnits', () => {
  it.each([
    [1250, '12.50'],
    [7, '0.07'],
    [0, '0.00'],
    [-320, '-3.20'],
    [-5, '-0.05'],
    [100000099, '1000000.99'],
  ])('writes %i as %s', (minor, amount) => {
    expect(fromMinorUnits(minor, 'PEN')).toEqual({ amount, currency: 'PEN' });
  });

  it('rejects non-integer minor units', () => {
    expect(() => fromMinorUnits(1.5, 'PEN')).toThrow();
  });
});

describe('addMoney', () => {
  it('adds with integer arithmetic, free of float drift', () => {
    const sum = addMoney({ amount: '0.10', currency: 'PEN' }, { amount: '0.20', currency: 'PEN' });

    expect(sum).toEqual({ amount: '0.30', currency: 'PEN' });
  });

  it('refuses to mix currencies', () => {
    expect(() =>
      addMoney({ amount: '1.00', currency: 'PEN' }, { amount: '1.00', currency: 'USD' }),
    ).toThrow();
  });
});

describe('formatMoney', () => {
  it('formats with the API currency and the Peruvian locale', () => {
    const text = formatMoney({ amount: '1234.50', currency: 'PEN' });

    expect(text.replace(/\s/g, ' ')).toBe('S/ 1,234.50');
  });

  it('keeps cents on small and negative amounts', () => {
    expect(formatMoney({ amount: '0.07', currency: 'PEN' })).toContain('0.07');
    expect(formatMoney({ amount: '-3.20', currency: 'PEN' })).toContain('3.20');
  });
});
