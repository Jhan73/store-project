import { marker, order } from '../../../testing/board-fixtures';
import { newMarkerKinds, unacknowledgedMarkerIds } from './board-markers';

describe('board markers', () => {
  const withMarkers = (...markers: ReturnType<typeof marker>[]) => [order({ id: 'o1', markers })];

  it('collects the ids of the markers nobody has acknowledged yet', () => {
    const orders = withMarkers(
      marker({ id: 'm1', boardOrderId: 'o1' }),
      marker({ id: 'm2', boardOrderId: 'o1', acknowledgedAt: '2026-10-13T10:00:00Z' }),
    );

    expect(unacknowledgedMarkerIds(orders)).toEqual(new Set(['m1']));
  });

  it('reports the kind of a marker that was not in the previous read', () => {
    const orders = withMarkers(
      marker({ id: 'm1', boardOrderId: 'o1', kind: 'NEW' }),
      marker({ id: 'm2', boardOrderId: 'o1', kind: 'MODIFIED' }),
    );

    expect(newMarkerKinds(new Set(['m1']), orders)).toEqual(['MODIFIED']);
  });

  it('reports each kind once, however many markers of it arrived', () => {
    const orders = withMarkers(
      marker({ id: 'm1', boardOrderId: 'o1', kind: 'NEW' }),
      marker({ id: 'm2', boardOrderId: 'o1', kind: 'NEW' }),
    );

    expect(newMarkerKinds(new Set(), orders)).toEqual(['NEW']);
  });

  it('puts a void before a modification before a new order', () => {
    const orders = withMarkers(
      marker({ id: 'm1', boardOrderId: 'o1', kind: 'NEW' }),
      marker({ id: 'm2', boardOrderId: 'o1', kind: 'VOID' }),
      marker({ id: 'm3', boardOrderId: 'o1', kind: 'MODIFIED' }),
    );

    expect(newMarkerKinds(new Set(), orders)).toEqual(['VOID', 'MODIFIED', 'NEW']);
  });

  it('ignores markers that are already acknowledged', () => {
    const orders = withMarkers(
      marker({ id: 'm1', boardOrderId: 'o1', kind: 'VOID', acknowledgedAt: '2026-10-13T10:00:00Z' }),
    );

    expect(newMarkerKinds(new Set(), orders)).toEqual([]);
  });
});
