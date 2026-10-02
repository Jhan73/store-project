import { FormControl } from '@angular/forms';
import { passwordPolicy, passwordViolations } from './password-policy';

const VALID = 'Abcdef1!';

describe('passwordViolations', () => {
  it('accepts a password that meets every rule', () => {
    expect(passwordViolations(VALID)).toEqual([]);
  });

  it('accepts exactly 8 and exactly 50 characters, rejects 7 and 51', () => {
    expect(passwordViolations('Abcde1!')).toEqual(['length']);
    expect(passwordViolations('Abcdef1!')).toEqual([]);
    expect(passwordViolations('Aa1!' + 'x'.repeat(46))).toEqual([]);
    expect(passwordViolations('Aa1!' + 'x'.repeat(47))).toEqual(['length']);
  });

  it.each([
    ['abcdef1!', 'uppercase'],
    ['ABCDEF1!', 'lowercase'],
    ['Abcdefg!', 'digit'],
    ['Abcdefg1', 'special'],
  ])('reports %s as missing %s', (password, rule) => {
    expect(passwordViolations(password)).toEqual([rule]);
  });

  it('reports every broken rule at once', () => {
    expect(passwordViolations('abc')).toEqual(['length', 'uppercase', 'digit', 'special']);
  });

  it('counts a space as the special character, like the backend', () => {
    expect(passwordViolations('Abcdef1 ')).toEqual([]);
  });

  it('counts non-ASCII letters as special, not as lowercase or uppercase', () => {
    expect(passwordViolations('Abcdef1ñ')).toEqual([]);
    expect(passwordViolations('ÁÉÍÓÚ1ñ!')).toEqual(['lowercase', 'uppercase']);
  });

  it('counts only ASCII digits', () => {
    expect(passwordViolations('Abcdef!٣')).toEqual(['digit']);
  });

  it('measures length in UTF-16 units, so an emoji counts as two', () => {
    expect(passwordViolations('Ab1😀😀x')).toEqual([]);
  });
});

describe('passwordViolations byte limit', () => {
  it('accepts exactly 72 UTF-8 bytes and rejects 73, within 50 characters', () => {
    expect(passwordViolations('Aa1!' + 'ñ'.repeat(34))).toEqual([]);
    expect(passwordViolations('Aa1!' + 'ñ'.repeat(35))).toEqual(['bytes']);
  });

  it('counts an emoji as four bytes', () => {
    expect(passwordViolations('Aa1!' + '😀'.repeat(17))).toEqual([]);
    expect(passwordViolations('Aa1!' + '😀'.repeat(18))).toEqual(['bytes']);
  });

  it('reports both the character and the byte limit for a very long password', () => {
    expect(passwordViolations('Aa1!' + 'ñ'.repeat(60))).toEqual(['length', 'bytes']);
  });

  it('leaves ASCII passwords of up to 50 characters unaffected', () => {
    expect(passwordViolations('Aa1!' + 'x'.repeat(46))).toEqual([]);
  });
});

describe('passwordViolations line separators', () => {
  it.each(['\n', '\r', '\u0085', '\u2028', '\u2029'])(
    'rejects %j like the backend pattern',
    (separator) => {
      expect(passwordViolations('Abcdef1!' + separator)).toEqual(['lineBreak']);
    },
  );
});

describe('passwordPolicy validator', () => {
  it('ignores an empty value, which belongs to the required rule', () => {
    expect(passwordPolicy(new FormControl(''))).toBeNull();
  });

  it('returns the broken rules under one key', () => {
    expect(passwordPolicy(new FormControl('abc'))).toEqual({
      passwordPolicy: ['length', 'uppercase', 'digit', 'special'],
    });
  });

  it('passes a valid password', () => {
    expect(passwordPolicy(new FormControl(VALID))).toBeNull();
  });
});
