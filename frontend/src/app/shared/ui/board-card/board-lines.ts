import type { BoardLine, BoardOrder } from '../../../core/api/api-types';

export interface LineGroup {
  // The id of the first line, so a group of one keeps the id its line always had.
  readonly id: string;
  readonly lines: readonly BoardLine[];
  readonly quantity: number;
}

export interface OrderIndicators {
  readonly allergy: boolean;
  readonly note: boolean;
}

// Lines merge only when everything the kitchen reads or acts on is the same; a line with its own label stays alone.
export function groupLines(lines: readonly BoardLine[], markerKey: (line: BoardLine) => string): LineGroup[] {
  const groups = new Map<string, BoardLine[]>();
  for (const item of lines) {
    const key = JSON.stringify([
      item.productId,
      item.displayName,
      item.modifiersText,
      item.note,
      item.allergy,
      item.state,
      item.flaggedUnavailable,
      item.comped,
      item.waste,
      markerKey(item),
    ]);
    const members = groups.get(key);
    if (members) {
      members.push(item);
    } else {
      groups.set(key, [item]);
    }
  }
  return [...groups.values()].map((members) => ({
    id: members[0].id,
    lines: members,
    quantity: members.reduce((total, member) => total + member.quantity, 0),
  }));
}

// A voided line is on its way out and must not raise an alert; modifiers are choices, not alerts.
export function indicatorsOf(order: Pick<BoardOrder, 'lines'>): OrderIndicators {
  const live = order.lines.filter((item) => item.state !== 'VOIDED');
  return {
    allergy: live.some((item) => item.allergy),
    note: live.some((item) => (item.note ?? '').trim() !== ''),
  };
}
