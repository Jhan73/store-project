import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { AdminTable } from '../../../core/api/api-types';
import { TablesApi } from './tables-api';

const ADMIN = 'http://api.test/api/v1/admin/tables';

const table: AdminTable = {
  id: 't1',
  name: 'Mesa 1',
  area: 'Terraza',
  displayOrder: 1,
  active: true,
  etag: '"2"',
};

describe('TablesApi', () => {
  let api: TablesApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        TablesApi,
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    api = TestBed.inject(TablesApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists, creates and replaces tables with the version of the item', () => {
    api.tables().subscribe();
    http.expectOne(ADMIN).flush([table]);

    api.create({ name: 'Mesa 1', area: null, displayOrder: 1 }).subscribe();
    const create = http.expectOne(ADMIN);
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({ name: 'Mesa 1', area: null, displayOrder: 1 });
    create.flush(table);

    api.change('t1', '"2"', { name: 'Mesa 1', area: null, displayOrder: 2 }).subscribe();
    const change = http.expectOne(`${ADMIN}/t1`);
    expect(change.request.method).toBe('PUT');
    expect(change.request.headers.get('If-Match')).toBe('"2"');
    change.flush(table);
  });

  it('deactivates and reactivates guarded by the version of the item', () => {
    api.deactivate('t1', '"2"').subscribe();
    const off = http.expectOne(`${ADMIN}/t1/deactivate`);
    expect(off.request.method).toBe('POST');
    expect(off.request.headers.get('If-Match')).toBe('"2"');
    off.flush(table);

    api.reactivate('t1', '"3"').subscribe();
    const on = http.expectOne(`${ADMIN}/t1/reactivate`);
    expect(on.request.headers.get('If-Match')).toBe('"3"');
    on.flush(table);
  });
});
