import { densityOf } from './display-density';

describe('densityOf', () => {
  it('keeps full size and one column for a few entries', () => {
    expect(densityOf(0)).toEqual({ scale: 1, columns: 1 });
    expect(densityOf(6)).toEqual({ scale: 1, columns: 1 });
  });

  it('shrinks in steps and adds columns as the entries grow', () => {
    expect(densityOf(7)).toEqual({ scale: 0.75, columns: 1 });
    expect(densityOf(16)).toEqual({ scale: 0.75, columns: 2 });
    expect(densityOf(17)).toEqual({ scale: 0.5, columns: 2 });
    expect(densityOf(25)).toEqual({ scale: 0.35, columns: 2 });
    expect(densityOf(51)).toEqual({ scale: 0.35, columns: 3 });
    expect(densityOf(52)).toEqual({ scale: 0.25, columns: 3 });
  });

  it('never gives a step fewer cells than entries, whatever the count', () => {
    const rowsAt: Record<number, number> = { 1: 6, 0.75: 8, 0.5: 12, 0.35: 17, 0.25: 24 };
    for (let count = 0; count <= 400; count++) {
      const { scale, columns } = densityOf(count);
      expect(columns * rowsAt[scale]).toBeGreaterThanOrEqual(count);
    }
  });

  it('never shrinks when entries are removed', () => {
    let previous = 1;
    for (let count = 0; count <= 400; count++) {
      const { scale } = densityOf(count);
      expect(scale).toBeLessThanOrEqual(previous);
      previous = scale;
    }
  });
});
