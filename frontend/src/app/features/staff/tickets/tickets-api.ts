import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { expand, map, Observable, reduce } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-config';
import type {
  BoardOrder,
  LineRequest,
  Menu,
  OpenTicketRequest,
  TableGridEntry,
  Ticket,
  TicketSummary,
  TicketSummaryPage,
  VoidLineRequest,
} from '../../../core/api/api-types';

export interface TableGridRead {
  readonly tables: TableGridEntry[];
  // The server's `Date` header, to correct elapsed times for a wrong device clock.
  readonly serverDate: string | null;
}

const PAGE_SIZE = 100;

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
    const read = (page: number) =>
      this.http.get<TicketSummaryPage>(`${this.base}/tickets`, {
        params: new HttpParams().set('page', page).set('size', PAGE_SIZE),
      });
    return read(0).pipe(
      expand((result) => (result.page + 1 < result.totalPages ? read(result.page + 1) : [])),
      reduce<TicketSummaryPage, TicketSummary[]>((all, result) => [...all, ...result.content], []),
    );
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

  boardReady(): Observable<BoardOrder[]> {
    return this.http.get<BoardOrder[]>(`${this.base}/board/ready`);
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

  voidLine(id: string, lineId: string, request: VoidLineRequest, key: string): Observable<Ticket> {
    return this.http.post<Ticket>(
      `${this.base}/tickets/${id}/lines/${lineId}/void`,
      request,
      idempotencyKey(key),
    );
  }

  cancel(id: string): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/tickets/${id}/cancel`, null);
  }

  changeNote(id: string, note: string | null): Observable<Ticket> {
    return this.http.put<Ticket>(`${this.base}/tickets/${id}/note`, { note });
  }

  setBillRequested(id: string, requested: boolean): Observable<Ticket> {
    return this.http.put<Ticket>(`${this.base}/tickets/${id}/bill-requested`, { requested });
  }
}
