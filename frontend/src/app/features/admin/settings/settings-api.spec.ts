import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { API_ORIGIN } from '../../../core/api/api-config';
import type {
  DeliveryZone,
  OpeningHour,
  Reason,
  StoreSettings,
  UpdateOpeningHourRequest,
  UpdateStoreSettingsRequest,
} from '../../../core/api/api-types';
import { SettingsApi } from './settings-api';

const ADMIN = 'http://api.test/api/v1/admin';

const settings: StoreSettings = {
  id: 'st',
  timeZone: 'America/Lima',
  currency: 'PEN',
  basePrepMinutes: 10,
  queueMinutesPerOrder: 2,
  busyModeMinutes: 15,
  boardWarningMinutes: 8,
  boardLateMinutes: 12,
  registerDifferenceThreshold: { amount: '5.00', currency: 'PEN' },
  exceptionThreshold: 3,
  onlineCapacityLimit: 20,
};
const update: UpdateStoreSettingsRequest = {
  timeZone: 'America/Lima',
  currency: 'PEN',
  basePrepMinutes: 12,
  queueMinutesPerOrder: 2,
  busyModeMinutes: 15,
  boardWarningMinutes: 8,
  boardLateMinutes: 12,
  registerDifferenceThreshold: { amount: '5.00', currency: 'PEN' },
  exceptionThreshold: 3,
  onlineCapacityLimit: 20,
};
const monday: OpeningHour = {
  dayOfWeek: 'MONDAY',
  closed: false,
  opensAt: '08:00:00',
  closesAt: '20:00:00',
};
const zone: DeliveryZone = {
  id: 'z1',
  name: 'Centro',
  fee: { amount: '4.00', currency: 'PEN' },
  deliveryMinutes: 30,
  minimumOrder: null,
  freeDeliveryThreshold: null,
  active: true,
  etag: '"2"',
};
const reason: Reason = { id: 'r1', type: 'VOID', code: 'Spilled', active: true, etag: '"1"' };

describe('SettingsApi', () => {
  let api: SettingsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        SettingsApi,
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    api = TestBed.inject(SettingsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the settings together with the ETag of the response', async () => {
    const result = firstValueFrom(api.settings());

    const request = http.expectOne(`${ADMIN}/settings`);
    expect(request.request.method).toBe('GET');
    request.flush(settings, { headers: { ETag: '"4"' } });

    expect(await result).toEqual({ value: settings, etag: '"4"' });
  });

  it('fails when the response carries no ETag, so a save never goes out unguarded', async () => {
    const result = firstValueFrom(api.settings());

    http.expectOne(`${ADMIN}/settings`).flush(settings);

    await expect(result).rejects.toThrow();
  });

  it('replaces the settings guarded by the ETag it was given and returns the new one', async () => {
    const result = firstValueFrom(api.updateSettings('"4"', update));

    const request = http.expectOne(`${ADMIN}/settings`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.headers.get('If-Match')).toBe('"4"');
    expect(request.request.body).toEqual(update);
    request.flush({ ...settings, basePrepMinutes: 12 }, { headers: { ETag: '"5"' } });

    expect((await result).etag).toBe('"5"');
  });

  it('reads the opening hours with their ETag', async () => {
    const result = firstValueFrom(api.openingHours());

    http.expectOne(`${ADMIN}/settings/opening-hours`).flush([monday], { headers: { ETag: '"7"' } });

    expect(await result).toEqual({ value: [monday], etag: '"7"' });
  });

  it('replaces the week guarded by the ETag of the last read', async () => {
    const week: UpdateOpeningHourRequest[] = [
      { dayOfWeek: 'MONDAY', closed: false, opensAt: '08:00:00', closesAt: '20:00:00' },
    ];
    const result = firstValueFrom(api.replaceOpeningHours('"7"', week));

    const request = http.expectOne(`${ADMIN}/settings/opening-hours`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.headers.get('If-Match')).toBe('"7"');
    expect(request.request.body).toEqual(week);
    request.flush([monday], { headers: { ETag: '"8"' } });

    expect((await result).etag).toBe('"8"');
  });

  it('lists, creates and changes delivery zones with the version of the item', () => {
    api.zones().subscribe();
    http.expectOne(`${ADMIN}/delivery-zones`).flush([zone]);

    const body = { name: 'Centro', fee: zone.fee, deliveryMinutes: 30 };
    api.createZone(body).subscribe();
    const create = http.expectOne(`${ADMIN}/delivery-zones`);
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(body);
    create.flush(zone);

    api.changeZone('z1', '"2"', body).subscribe();
    const change = http.expectOne(`${ADMIN}/delivery-zones/z1`);
    expect(change.request.method).toBe('PATCH');
    expect(change.request.headers.get('If-Match')).toBe('"2"');
    change.flush(zone);

    api.deactivateZone('z1', '"2"').subscribe();
    const off = http.expectOne(`${ADMIN}/delivery-zones/z1/deactivate`);
    expect(off.request.method).toBe('POST');
    expect(off.request.headers.get('If-Match')).toBe('"2"');
    off.flush(zone);

    api.reactivateZone('z1', '"3"').subscribe();
    const on = http.expectOne(`${ADMIN}/delivery-zones/z1/reactivate`);
    expect(on.request.headers.get('If-Match')).toBe('"3"');
    on.flush(zone);
  });

  it('lists reasons of one type, creates them and toggles them with the version of the item', () => {
    api.reasons('CASH_OUT').subscribe();
    http.expectOne(`${ADMIN}/reasons?type=CASH_OUT`).flush([reason]);

    api.createReason({ type: 'VOID', code: 'Spilled' }).subscribe();
    const create = http.expectOne(`${ADMIN}/reasons`);
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({ type: 'VOID', code: 'Spilled' });
    create.flush(reason);

    api.deactivateReason('r1', '"1"').subscribe();
    const off = http.expectOne(`${ADMIN}/reasons/r1/deactivate`);
    expect(off.request.headers.get('If-Match')).toBe('"1"');
    off.flush(reason);

    api.reactivateReason('r1', '"2"').subscribe();
    const on = http.expectOne(`${ADMIN}/reasons/r1/reactivate`);
    expect(on.request.headers.get('If-Match')).toBe('"2"');
    on.flush(reason);
  });
});
