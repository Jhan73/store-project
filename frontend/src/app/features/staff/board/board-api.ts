import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-config';
import type {
  BoardAllDay,
  BoardMarker,
  BoardOrder,
  BoardThresholds,
  StationOption,
} from '../../../core/api/api-types';

const stationParams = (station: string | null) => (station === null ? new HttpParams() : new HttpParams().set('station', station));

export interface BoardRead {
  readonly orders: BoardOrder[];
  // The server's `Date` header, to correct elapsed times for a wrong device clock.
  readonly serverDate: string | null;
}

@Injectable()
export class BoardApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  tickets(station: string | null = null): Observable<BoardRead> {
    return this.http
      .get<BoardOrder[]>(`${this.base}/board/tickets`, { observe: 'response', params: stationParams(station) })
      .pipe(map((response) => ({ orders: response.body ?? [], serverDate: response.headers.get('Date') })));
  }

  ready(station: string | null = null): Observable<BoardOrder[]> {
    return this.http.get<BoardOrder[]>(`${this.base}/board/ready`, { params: stationParams(station) });
  }

  allDay(station: string | null = null): Observable<BoardAllDay> {
    return this.http.get<BoardAllDay>(`${this.base}/board/all-day`, { params: stationParams(station) });
  }

  stations(): Observable<StationOption[]> {
    return this.http.get<StationOption[]>(`${this.base}/stations`);
  }

  thresholds(): Observable<BoardThresholds> {
    return this.http.get<BoardThresholds>(`${this.base}/store/board-thresholds`);
  }

  start(orderId: string): Observable<BoardOrder> {
    return this.http.post<BoardOrder>(`${this.base}/board/orders/${orderId}/start`, null);
  }

  // `keepalive` lets the request finish while the page is being closed.
  readyOrder(orderId: string, keepalive = false): Observable<BoardOrder> {
    return this.http.post<BoardOrder>(`${this.base}/board/orders/${orderId}/ready`, null, { keepalive });
  }

  handover(orderId: string): Observable<BoardOrder> {
    return this.http.post<BoardOrder>(`${this.base}/board/orders/${orderId}/handover`, null);
  }

  recall(orderId: string): Observable<BoardOrder> {
    return this.http.post<BoardOrder>(`${this.base}/board/orders/${orderId}/recall`, null);
  }

  readyLine(lineId: string, keepalive = false): Observable<BoardOrder> {
    return this.http.post<BoardOrder>(`${this.base}/board/lines/${lineId}/ready`, null, { keepalive });
  }

  flagLine(lineId: string): Observable<BoardOrder> {
    return this.http.post<BoardOrder>(`${this.base}/board/lines/${lineId}/flag`, null);
  }

  acknowledge(markerId: string): Observable<BoardMarker> {
    return this.http.post<BoardMarker>(`${this.base}/board/markers/${markerId}/ack`, null);
  }
}
