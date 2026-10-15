import type { BoardMarker, BoardOrder } from '../../../core/api/api-types';

export type MarkerKind = BoardMarker['kind'];

// The most urgent first, which is also the order the sounds play in.
const KIND_PRIORITY: readonly MarkerKind[] = ['VOID', 'MODIFIED', 'NEW'];

function unacknowledged(orders: readonly BoardOrder[]): BoardMarker[] {
  return orders.flatMap((order) => order.markers).filter((marker) => marker.acknowledgedAt === null);
}

export function unacknowledgedMarkerIds(orders: readonly BoardOrder[]): Set<string> {
  return new Set(unacknowledged(orders).map((marker) => marker.id));
}

// The kinds of the markers this read has that the previous one did not, each once.
export function newMarkerKinds(previous: ReadonlySet<string>, orders: readonly BoardOrder[]): MarkerKind[] {
  const arrived = new Set(
    unacknowledged(orders)
      .filter((marker) => !previous.has(marker.id))
      .map((marker) => marker.kind),
  );
  return KIND_PRIORITY.filter((kind) => arrived.has(kind));
}
