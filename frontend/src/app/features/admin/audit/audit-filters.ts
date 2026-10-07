import type { ParamMap, Params } from '@angular/router';
import { dayToDate, storeDayEnd, storeDayStart } from '../../../core/time/store-day';
import type { AuditQuery } from './audit-api';

export const DEFAULT_PAGE_SIZE = 20;
export const PAGE_SIZES: readonly number[] = [10, 20, 50, 100];

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// Days are store-local calendar days (YYYY-MM-DD), so a shared link means the same thing in any browser zone.
export interface AuditFilters {
  readonly from: string | null;
  readonly to: string | null;
  readonly actorId: string | null;
  readonly entityType: string | null;
  readonly entityId: string;
  readonly action: string | null;
  readonly page: number;
  readonly size: number;
}

export const EMPTY_FILTERS: AuditFilters = {
  from: null,
  to: null,
  actorId: null,
  entityType: null,
  entityId: '',
  action: null,
  page: 0,
  size: DEFAULT_PAGE_SIZE,
};

export interface AuditFilterErrors {
  range?: 'order';
  entityId?: 'format';
}

function day(value: string | null): string | null {
  return value !== null && dayToDate(value) !== null ? value : null;
}

function text(value: string | null): string | null {
  const trimmed = value?.trim();
  return trimmed ? trimmed : null;
}

export function paramsToFilters(params: ParamMap): AuditFilters {
  const actorId = text(params.get('actorId'));
  const page = Number(params.get('page') ?? 0);
  const size = Number(params.get('size') ?? DEFAULT_PAGE_SIZE);
  return {
    from: day(params.get('from')),
    to: day(params.get('to')),
    actorId: actorId !== null && UUID.test(actorId) ? actorId : null,
    entityType: text(params.get('entityType')),
    entityId: text(params.get('entityId')) ?? '',
    action: text(params.get('action')),
    page: Number.isInteger(page) && page > 0 ? page : 0,
    size: PAGE_SIZES.includes(size) ? size : DEFAULT_PAGE_SIZE,
  };
}

export function filtersToParams(filters: AuditFilters): Params {
  const params: Params = {};
  for (const name of ['from', 'to', 'actorId', 'entityType', 'entityId', 'action'] as const) {
    if (filters[name]) {
      params[name] = filters[name];
    }
  }
  if (filters.page > 0) {
    params['page'] = filters.page;
  }
  if (filters.size !== DEFAULT_PAGE_SIZE) {
    params['size'] = filters.size;
  }
  return params;
}

export function filtersToQuery(filters: AuditFilters, timeZone: string): AuditQuery {
  return {
    ...(filters.actorId && { actorId: filters.actorId }),
    ...(filters.entityType && { entityType: filters.entityType }),
    ...(filters.entityId && { entityId: filters.entityId }),
    ...(filters.action && { action: filters.action }),
    ...(filters.from && { from: storeDayStart(filters.from, timeZone) }),
    ...(filters.to && { to: storeDayEnd(filters.to, timeZone) }),
    page: filters.page,
    size: filters.size,
  };
}

export function filterErrors(filters: AuditFilters): AuditFilterErrors {
  const errors: AuditFilterErrors = {};
  if (filters.from && filters.to && filters.from > filters.to) {
    errors.range = 'order';
  }
  if (filters.entityId && !UUID.test(filters.entityId)) {
    errors.entityId = 'format';
  }
  return errors;
}
