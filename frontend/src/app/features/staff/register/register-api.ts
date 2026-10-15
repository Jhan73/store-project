import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { catchError, expand, map, Observable, of, reduce, throwError } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-config';
import type {
  BoardThresholds,
  CashMovement,
  CashMovementRequest,
  CloseShiftRequest,
  ForceCloseShiftRequest,
  Menu,
  Money,
  OpenShiftRequest,
  QuickSaleItem,
  QuickSaleRequest,
  RecordPaymentRequest,
  Shift,
  ShiftPage,
  SplitParts,
  StaffMember,
  StaffPage,
  Ticket,
  VoidPaymentRequest,
  VoidSaleRequest,
} from '../../../core/api/api-types';
import { ApiError } from '../../../core/errors/api-error';

const STAFF_PAGE_SIZE = 100;

export type CloseOutcome =
  | { readonly kind: 'closed'; readonly shift: Shift }
  | { readonly kind: 'note-required'; readonly difference: Money }
  | { readonly kind: 'count-locked' };

const idempotencyKey = (key: string) => ({ headers: new HttpHeaders({ 'Idempotency-Key': key }) });

@Injectable()
export class RegisterApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  openShift(request: OpenShiftRequest, key: string): Observable<Shift> {
    return this.http.post<Shift>(`${this.base}/shifts`, request, idempotencyKey(key));
  }

  // "No shift" is a normal state, not an error.
  currentShift(): Observable<Shift | null> {
    return this.http.get<Shift>(`${this.base}/shifts/current`).pipe(
      catchError((error: unknown) =>
        error instanceof ApiError && error.code === 'instore.shift-not-found'
          ? of(null)
          : throwError(() => error),
      ),
    );
  }

  shift(id: string): Observable<Shift> {
    return this.http.get<Shift>(`${this.base}/shifts/${id}`);
  }

  shifts(status: Shift['status'], page: number, size: number): Observable<ShiftPage> {
    const params = new HttpParams().set('status', status).set('page', page).set('size', size);
    return this.http.get<ShiftPage>(`${this.base}/shifts`, { params });
  }

  // The 409 that asks for a note and the 422 that locks the count are answers to this command, not failures.
  closeShift(id: string, request: CloseShiftRequest): Observable<CloseOutcome> {
    return this.http.post<Shift>(`${this.base}/shifts/${id}/close`, request).pipe(
      map((shift): CloseOutcome => ({ kind: 'closed', shift })),
      catchError((error: unknown) => {
        if (error instanceof ApiError && error.code === 'instore.difference-note-required') {
          return of<CloseOutcome>({
            kind: 'note-required',
            difference: error.properties['difference'] as Money,
          });
        }
        if (error instanceof ApiError && error.code === 'instore.counted-cash-locked') {
          return of<CloseOutcome>({ kind: 'count-locked' });
        }
        return throwError(() => error);
      }),
    );
  }

  forceClose(id: string, request: ForceCloseShiftRequest): Observable<Shift> {
    return this.http.post<Shift>(`${this.base}/shifts/${id}/force-close`, request);
  }

  movements(shiftId: string): Observable<CashMovement[]> {
    return this.http.get<CashMovement[]>(`${this.base}/shifts/${shiftId}/cash-movements`);
  }

  recordMovement(shiftId: string, request: CashMovementRequest, key: string): Observable<CashMovement> {
    return this.http.post<CashMovement>(
      `${this.base}/shifts/${shiftId}/cash-movements`,
      request,
      idempotencyKey(key),
    );
  }

  pay(ticketId: string, request: RecordPaymentRequest, key: string): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/tickets/${ticketId}/payments`, request, idempotencyKey(key));
  }

  voidPayment(ticketId: string, paymentId: string, request: VoidPaymentRequest): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/tickets/${ticketId}/payments/${paymentId}/void`, request);
  }

  voidSale(ticketId: string, request: VoidSaleRequest): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/tickets/${ticketId}/void`, request);
  }

  split(ticketId: string, parts: number): Observable<Money[]> {
    return this.http
      .get<SplitParts>(`${this.base}/tickets/${ticketId}/split`, {
        params: new HttpParams().set('parts', parts),
      })
      .pipe(map((split) => split.parts));
  }

  quickSaleItems(): Observable<QuickSaleItem[]> {
    return this.http.get<QuickSaleItem[]>(`${this.base}/quick-sales/items`);
  }

  quickSale(request: QuickSaleRequest, key: string): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.base}/quick-sales`, request, idempotencyKey(key));
  }

  timeZone(): Observable<string> {
    return this.http
      .get<BoardThresholds>(`${this.base}/store/board-thresholds`)
      .pipe(map((thresholds) => thresholds.timeZone));
  }

  // Floor staff cannot read the store settings; every price carries the store currency.
  currency(): Observable<string | null> {
    return this.http
      .get<Menu>(`${this.base}/catalog/menu`)
      .pipe(map((menu) => menu.categories.flatMap((category) => category.products)[0]?.price.currency ?? null));
  }

  // The shift list only carries the cashier's id; the accounts turn it into an email.
  staffAccounts(): Observable<StaffMember[]> {
    const read = (page: number) =>
      this.http.get<StaffPage>(`${this.base}/staff`, {
        params: new HttpParams().set('page', page).set('size', STAFF_PAGE_SIZE),
      });
    return read(0).pipe(
      expand((result) => (result.page + 1 < result.totalPages ? read(result.page + 1) : [])),
      reduce<StaffPage, StaffMember[]>((all, result) => [...all, ...result.content], []),
    );
  }
}
