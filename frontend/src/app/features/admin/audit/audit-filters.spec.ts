import { convertToParamMap } from '@angular/router';
import {
  DEFAULT_PAGE_SIZE,
  EMPTY_FILTERS,
  filtersToParams,
  filterErrors,
  filtersToQuery,
  paramsToFilters,
} from './audit-filters';

const ID = '0199f3a2-7c1e-7b1a-8f00-3c1d2e4a5b6c';

describe('paramsToFilters', () => {
  it('reads nothing as the empty search on the first page', () => {
    expect(paramsToFilters(convertToParamMap({}))).toEqual(EMPTY_FILTERS);
  });

  it('reads every filter from the URL', () => {
    const filters = paramsToFilters(
      convertToParamMap({
        from: '2026-10-01',
        to: '2026-10-05',
        actorId: ID,
        entityType: 'PRODUCT',
        entityId: ID,
        action: 'PRODUCT_UPDATED',
        page: '3',
        size: '50',
      }),
    );

    expect(filters).toEqual({
      from: '2026-10-01',
      to: '2026-10-05',
      actorId: ID,
      entityType: 'PRODUCT',
      entityId: ID,
      action: 'PRODUCT_UPDATED',
      page: 3,
      size: 50,
    });
  });

  it('drops values that cannot be what the screen offers', () => {
    const filters = paramsToFilters(
      convertToParamMap({ from: 'ayer', to: '2026-02-30', actorId: 'nobody', page: '-4', size: '7' }),
    );

    expect(filters).toEqual(EMPTY_FILTERS);
  });

  it('reads a lone from as that single whole day', () => {
    const filters = paramsToFilters(convertToParamMap({ from: '2026-10-05' }));

    expect(filters.from).toBe('2026-10-05');
    expect(filters.to).toBe('2026-10-05');
  });

  it('reads a lone to as that single whole day', () => {
    const filters = paramsToFilters(convertToParamMap({ to: '2026-10-05' }));

    expect(filters.from).toBe('2026-10-05');
    expect(filters.to).toBe('2026-10-05');
  });

  it('uses the valid end when the other one is not a day', () => {
    const filters = paramsToFilters(convertToParamMap({ from: 'ayer', to: '2026-10-05' }));

    expect(filters.from).toBe('2026-10-05');
    expect(filters.to).toBe('2026-10-05');
  });

  it('drops days whose year the Date constructor would shift into the 1900s', () => {
    const filters = paramsToFilters(convertToParamMap({ from: '0050-01-01', to: '0999-12-31' }));

    expect(filters.from).toBeNull();
    expect(filters.to).toBeNull();
  });

  it('keeps an entity id that is not an id so the field can say so', () => {
    expect(paramsToFilters(convertToParamMap({ entityId: ' abc ' })).entityId).toBe('abc');
  });
});

describe('filtersToParams', () => {
  it('writes only what is set, and the first page and default size are implicit', () => {
    expect(filtersToParams(EMPTY_FILTERS)).toEqual({});
    expect(
      filtersToParams({ ...EMPTY_FILTERS, entityType: 'USER', page: 0, size: DEFAULT_PAGE_SIZE }),
    ).toEqual({ entityType: 'USER' });
  });

  it('round-trips through the URL', () => {
    const filters = {
      from: '2026-10-01',
      to: '2026-10-05',
      actorId: ID,
      entityType: 'PRODUCT',
      entityId: ID,
      action: 'PRODUCT_UPDATED',
      page: 2,
      size: 50,
    };

    expect(paramsToFilters(convertToParamMap(filtersToParams(filters)))).toEqual(filters);
  });
});

describe('filtersToQuery', () => {
  it('turns the days into the exact instants of the store day, the last one inclusive', () => {
    const query = filtersToQuery(
      { ...EMPTY_FILTERS, from: '2026-10-01', to: '2026-10-05' },
      'America/Lima',
    );

    expect(query.from).toBe('2026-10-01T05:00:00.000Z');
    expect(query.to).toBe('2026-10-06T04:59:59.999999Z');
  });

  it('leaves an open end open and passes the rest through', () => {
    const query = filtersToQuery(
      { ...EMPTY_FILTERS, from: '2026-10-01', actorId: ID, action: 'USER_CREATED', page: 1, size: 20 },
      'UTC',
    );

    expect(query).toEqual({
      actorId: ID,
      action: 'USER_CREATED',
      from: '2026-10-01T00:00:00.000Z',
      page: 1,
      size: 20,
    });
  });
});

describe('filterErrors', () => {
  it('accepts an empty search', () => {
    expect(filterErrors(EMPTY_FILTERS)).toEqual({});
  });

  it('refuses a range that ends before it starts', () => {
    expect(filterErrors({ ...EMPTY_FILTERS, from: '2026-10-05', to: '2026-10-01' })).toEqual({
      range: 'order',
    });
    expect(filterErrors({ ...EMPTY_FILTERS, from: '2026-10-05', to: '2026-10-05' })).toEqual({});
  });

  it('refuses an entity id that is not a UUID', () => {
    expect(filterErrors({ ...EMPTY_FILTERS, entityId: 'abc' })).toEqual({ entityId: 'format' });
    expect(filterErrors({ ...EMPTY_FILTERS, entityId: ID.toUpperCase() })).toEqual({});
  });
});
