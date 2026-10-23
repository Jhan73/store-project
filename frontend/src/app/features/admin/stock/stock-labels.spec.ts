import type { StockMovementType } from '../../../core/api/api-types';
import { movementTypeLabel } from './stock-labels';

const TYPES: StockMovementType[] = [
  'ENTRY',
  'ADJUSTMENT',
  'SALE',
  'RETURN',
  'WASTE',
  'RESERVE',
  'COMMIT',
  'RELEASE',
  'EXPIRE',
];

describe('stock labels', () => {
  it('names every movement type in Spanish', () => {
    expect(movementTypeLabel('ENTRY')).toBe('Entrada');
    expect(movementTypeLabel('ADJUSTMENT')).toBe('Ajuste');
    expect(movementTypeLabel('SALE')).toBe('Venta');
    expect(movementTypeLabel('RETURN')).toBe('Devolución');
    expect(movementTypeLabel('WASTE')).toBe('Merma');
    for (const type of TYPES) {
      expect(movementTypeLabel(type)).not.toBe(type);
    }
  });

  it('falls back to the raw value for a type this screen does not know', () => {
    expect(movementTypeLabel('SOMETHING_NEW' as StockMovementType)).toBe('SOMETHING_NEW');
  });
});
