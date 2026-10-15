import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { expand, map, Observable, reduce } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-config';
import type {
  AdjustStockRequest,
  Product,
  ProductPage,
  RecordEntryRequest,
  StaffMember,
  StaffPage,
  StockItem,
  StockMovementPage,
  TrackStockRequest,
} from '../../../core/api/api-types';

const PAGE_SIZE = 100;

function idempotent(key: string) {
  return { headers: { 'Idempotency-Key': key } };
}

@Injectable()
export class StockApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);
  private readonly stock = `${this.base}/admin/stock-items`;

  list(): Observable<StockItem[]> {
    return this.http.get<StockItem[]>(this.stock);
  }

  low(): Observable<StockItem[]> {
    return this.http.get<StockItem[]>(`${this.stock}/low`);
  }

  item(productId: string): Observable<StockItem> {
    return this.http.get<StockItem>(`${this.stock}/${productId}`);
  }

  track(productId: string, body: TrackStockRequest): Observable<StockItem> {
    return this.http.put<StockItem>(`${this.stock}/${productId}`, body);
  }

  recordEntry(productId: string, body: RecordEntryRequest, key: string): Observable<StockItem> {
    return this.http.post<StockItem>(`${this.stock}/${productId}/entries`, body, idempotent(key));
  }

  adjust(productId: string, body: AdjustStockRequest, key: string): Observable<StockItem> {
    return this.http.post<StockItem>(`${this.stock}/${productId}/adjustments`, body, idempotent(key));
  }

  movements(productId: string, page: number, size: number): Observable<StockMovementPage> {
    return this.http.get<StockMovementPage>(`${this.stock}/${productId}/movements`, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  // Stock rows carry only a product id; the catalog gives the names and the untracked products.
  catalogProducts(): Observable<Product[]> {
    const read = (page: number) =>
      this.http.get<ProductPage>(`${this.base}/admin/products`, {
        params: new HttpParams().set('page', page).set('size', PAGE_SIZE),
      });
    return read(0).pipe(
      expand((result) => (result.page + 1 < result.totalPages ? read(result.page + 1) : [])),
      reduce<ProductPage, Product[]>((all, result) => [...all, ...result.content], []),
    );
  }

  // Movements carry only an actor id; the accounts turn it into an email.
  staffAccounts(): Observable<StaffMember[]> {
    const read = (page: number) =>
      this.http.get<StaffPage>(`${this.base}/staff`, {
        params: new HttpParams().set('page', page).set('size', PAGE_SIZE),
      });
    return read(0).pipe(
      expand((result) => (result.page + 1 < result.totalPages ? read(result.page + 1) : [])),
      reduce<StaffPage, StaffMember[]>((all, result) => [...all, ...result.content], []),
    );
  }

  timeZone(): Observable<string> {
    return this.http
      .get<{ timeZone: string }>(`${this.base}/admin/settings`)
      .pipe(map((settings) => settings.timeZone));
  }
}
