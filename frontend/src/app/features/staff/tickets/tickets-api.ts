import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-config';
import type {
  BoardOrder,
  LineRequest,
  Menu,
  OpenTicketRequest,
  TableGridEntry,
  Ticket,
  TicketSummary,
} from '../../../core/api/api-types';

export interface TableGridRead {
  readonly tables: TableGridEntry[];
  // The server's `Date` header, to correct elapsed times for a wrong device clock.
  readonly serverDate: string | null;
}

const idempotencyKey = (key: string) => ({ headers: new HttpHeaders({ 'Idempotency-Key': key }) });

@Injectable()
export class TicketsApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  tables(): Observable<TableGridRead> {
    return this.http
      .get<TableGridEntry[]>(`${this.base}/tables`, { observe: 'response' })
      .pipe(map((response) => ({ tables: response.body ?? [], serverDate: response.headers.get('Date') })));
  }

  openTickets(): Observable<TicketSummary[]> {
    return this.http.get<TicketSummary[]>(`${this.base}/tickets`);
  }

  ticket(id: string): Observable<Ticket> {
    return this.http.get<Ticket>(`${this.base}/tickets/${id}`);
  }

  menu(): Observable<Menu> {
    return this.http.get<Menu>(`${this.base}/catalog/menu`);
  }

  board(): Observable<BoardOrder[]> {
    return this.http.get<BoardOrder[]>(`${this.base}/board/tickets`);
  }

  open(request: OpenTicketRequest, key: string): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/tickets`, request, idempotencyKey(key));
  }

  addLine(id: string, line: LineRequest, key: string): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/tickets/${id}/lines`, line, idempotencyKey(key));
  }

  changeLine(id: string, lineId: string, line: LineRequest): Observable<Ticket> {
    return this.http.put<Ticket>(`${this.base}/tickets/${id}/lines/${lineId}`, line);
  }

  removeLine(id: string, lineId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/tickets/${id}/lines/${lineId}`);
  }

  send(id: string): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/tickets/${id}/send`, null);
  }

  transfer(id: string, tableId: string): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/tickets/${id}/transfer`, { tableId });
  }

  repeat(id: string, lineIds: string[], key: string): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/tickets/${id}/repeat`, { lineIds }, idempotencyKey(key));
  }

  setBillRequested(id: string, requested: boolean): Observable<Ticket> {
    return this.http.put<Ticket>(`${this.base}/tickets/${id}/bill-requested`, { requested });
  }
}
