import type { Shift } from '../core/api/api-types';

export const pen = (amount: string) => ({ amount, currency: 'PEN' });

export function shift(overrides: Partial<Shift> = {}): Shift {
  return {
    id: 's1',
    cashierId: 'u1',
    status: 'OPEN',
    openedAt: '2026-10-13T14:00:00Z',
    openingFloat: pen('50.00'),
    closedAt: null,
    closedBy: null,
    closeKind: null,
    countedCash: null,
    expectedCash: null,
    difference: null,
    differenceNote: null,
    forceReason: null,
    totals: null,
    ...overrides,
  };
}

export function problem(status: number, code: string, extra: Record<string, unknown> = {}) {
  return { type: 'about:blank', status, code, correlationId: 'c', ...extra };
}
