import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-config';
import type {
  AdminTable,
  CreateTableRequest,
  UpdateTableRequest,
} from '../../../core/api/api-types';

function ifMatch(etag: string) {
  return { headers: { 'If-Match': etag } };
}

@Injectable()
export class TablesApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/admin/tables`;

  tables(): Observable<AdminTable[]> {
    return this.http.get<AdminTable[]>(this.base);
  }

  create(body: CreateTableRequest): Observable<AdminTable> {
    return this.http.post<AdminTable>(this.base, body);
  }

  change(id: string, etag: string, body: UpdateTableRequest): Observable<AdminTable> {
    return this.http.put<AdminTable>(`${this.base}/${id}`, body, ifMatch(etag));
  }

  deactivate(id: string, etag: string): Observable<AdminTable> {
    return this.http.post<AdminTable>(`${this.base}/${id}/deactivate`, null, ifMatch(etag));
  }

  reactivate(id: string, etag: string): Observable<AdminTable> {
    return this.http.post<AdminTable>(`${this.base}/${id}/reactivate`, null, ifMatch(etag));
  }
}
