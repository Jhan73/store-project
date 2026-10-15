import type { BoardAllDay, BoardLine, BoardMarker, BoardOrder } from '../../../core/api/api-types';

export const minutesAgo = (minutes: number) => new Date(Date.now() - minutes * 60_000).toISOString();

export function line(overrides: Partial<BoardLine> & { id: string }): BoardLine {
  return {
    allergy: false,
    displayName: 'Jugo de naranja',
    flaggedUnavailable: false,
    modifiersText: null,
    note: null,
    productId: 'p1',
    quantity: 1,
    readyAt: null,
    sourceLineId: `src-${overrides.id}`,
    state: 'PENDING',
    stationId: null,
    waste: false,
    ...overrides,
  };
}

export function marker(
  overrides: Partial<BoardMarker> & { id: string; boardOrderId: string },
): BoardMarker {
  return {
    acknowledgedAt: null,
    createdAt: minutesAgo(1),
    kind: 'NEW',
    lineId: null,
    ...overrides,
  };
}

export function order(overrides: Partial<BoardOrder> & { id: string }): BoardOrder {
  return {
    channel: 'TABLE',
    fireAt: null,
    handedOverAt: null,
    label: 'Mesa 4',
    lines: [line({ id: `${overrides.id}-l1` })],
    markers: [],
    readyAt: null,
    sentAt: minutesAgo(1),
    shortNumber: 'L-017',
    source: 'TICKET',
    sourceId: `ticket-${overrides.id}`,
    startedAt: null,
    status: 'WAITING',
    ...overrides,
  };
}

export const emptyAllDay: BoardAllDay = { groups: [], individual: [] };
