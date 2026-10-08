import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-config';
import type {
  CreateDeliveryZoneRequest,
  CreateReasonRequest,
  DeliveryZone,
  OpeningHour,
  Reason,
  ReasonType,
  StoreSettings,
  UpdateDeliveryZoneRequest,
  UpdateOpeningHourRequest,
  UpdateStoreSettingsRequest,
} from '../../../core/api/api-types';

// A singleton resource and the ETag of the response that carried it; the next write sends it back.
export interface Versioned<T> {
  readonly value: T;
  readonly etag: string;
}

function ifMatch(etag: string) {
  return { headers: { 'If-Match': etag } };
}

function versioned<T>(response: HttpResponse<T>): Versioned<T> {
  const etag = response.headers.get('ETag');
  if (etag === null || response.body === null) {
    throw new Error('The response has no ETag or body to guard the next write with.');
  }
  return { value: response.body, etag };
}

@Injectable()
export class SettingsApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/admin`;

  settings(): Observable<Versioned<StoreSettings>> {
    return this.http
      .get<StoreSettings>(`${this.base}/settings`, { observe: 'response' })
      .pipe(map(versioned));
  }

  updateSettings(
    etag: string,
    body: UpdateStoreSettingsRequest,
  ): Observable<Versioned<StoreSettings>> {
    return this.http
      .put<StoreSettings>(`${this.base}/settings`, body, { ...ifMatch(etag), observe: 'response' })
      .pipe(map(versioned));
  }

  openingHours(): Observable<Versioned<OpeningHour[]>> {
    return this.http
      .get<OpeningHour[]>(`${this.base}/settings/opening-hours`, { observe: 'response' })
      .pipe(map(versioned));
  }

  replaceOpeningHours(
    etag: string,
    week: UpdateOpeningHourRequest[],
  ): Observable<Versioned<OpeningHour[]>> {
    return this.http
      .put<OpeningHour[]>(`${this.base}/settings/opening-hours`, week, {
        ...ifMatch(etag),
        observe: 'response',
      })
      .pipe(map(versioned));
  }

  zones(): Observable<DeliveryZone[]> {
    return this.http.get<DeliveryZone[]>(`${this.base}/delivery-zones`);
  }

  createZone(body: CreateDeliveryZoneRequest): Observable<DeliveryZone> {
    return this.http.post<DeliveryZone>(`${this.base}/delivery-zones`, body);
  }

  changeZone(
    id: string,
    etag: string,
    body: UpdateDeliveryZoneRequest,
  ): Observable<DeliveryZone> {
    return this.http.patch<DeliveryZone>(`${this.base}/delivery-zones/${id}`, body, ifMatch(etag));
  }

  deactivateZone(id: string, etag: string): Observable<DeliveryZone> {
    return this.http.post<DeliveryZone>(
      `${this.base}/delivery-zones/${id}/deactivate`,
      null,
      ifMatch(etag),
    );
  }

  reactivateZone(id: string, etag: string): Observable<DeliveryZone> {
    return this.http.post<DeliveryZone>(
      `${this.base}/delivery-zones/${id}/reactivate`,
      null,
      ifMatch(etag),
    );
  }

  reasons(type: ReasonType): Observable<Reason[]> {
    return this.http.get<Reason[]>(`${this.base}/reasons`, {
      params: new HttpParams().set('type', type),
    });
  }

  createReason(body: CreateReasonRequest): Observable<Reason> {
    return this.http.post<Reason>(`${this.base}/reasons`, body);
  }

  deactivateReason(id: string, etag: string): Observable<Reason> {
    return this.http.post<Reason>(`${this.base}/reasons/${id}/deactivate`, null, ifMatch(etag));
  }

  reactivateReason(id: string, etag: string): Observable<Reason> {
    return this.http.post<Reason>(`${this.base}/reasons/${id}/reactivate`, null, ifMatch(etag));
  }
}
