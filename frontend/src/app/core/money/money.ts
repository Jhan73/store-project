import type { Money } from '../api/api-types';

export type { Money };

const AMOUNT = /^(-?)(\d+)\.(\d{2})$/;

export function toMinorUnits(money: Money): number {
  const parts = AMOUNT.exec(money.amount);
  if (!parts) {
    throw new Error(`Malformed money amount: "${money.amount}"`);
  }
  const [, sign, whole, cents] = parts;
  const minor = parseInt(whole, 10) * 100 + parseInt(cents, 10);
  if (!Number.isSafeInteger(minor)) {
    throw new Error(`Money amount out of range: "${money.amount}"`);
  }
  return sign ? -minor || 0 : minor;
}

export function fromMinorUnits(minor: number, currency: string): Money {
  if (!Number.isSafeInteger(minor)) {
    throw new Error(`Minor units must be a safe integer: ${minor}`);
  }
  const abs = Math.abs(minor);
  const whole = Math.trunc(abs / 100);
  const cents = String(abs % 100).padStart(2, '0');
  return { amount: `${minor < 0 ? '-' : ''}${whole}.${cents}`, currency };
}

export function addMoney(a: Money, b: Money): Money {
  if (a.currency !== b.currency) {
    throw new Error(`Cannot add ${a.currency} to ${b.currency}`);
  }
  return fromMinorUnits(toMinorUnits(a) + toMinorUnits(b), a.currency);
}

export function formatMoney(money: Money, locale = 'es-PE'): string {
  return new Intl.NumberFormat(locale, { style: 'currency', currency: money.currency }).format(
    toMinorUnits(money) / 100,
  );
}

const TYPED_AMOUNT = /^(\d{1,10})(?:[.,](\d{1,2}))?$/;

// Turns what a person typed ("9", "9,5") into the API's two-decimal string, or null when it is not an amount.
export function parseAmount(text: string): string | null {
  const parts = TYPED_AMOUNT.exec(text.trim());
  if (!parts) {
    return null;
  }
  const [, whole, cents = ''] = parts;
  return `${whole.replace(/^0+(?=\d)/, '')}.${cents.padEnd(2, '0')}`;
}
