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
