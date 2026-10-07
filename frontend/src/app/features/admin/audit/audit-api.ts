import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { expand, map, Observable, reduce } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-config';
import type { AuditPage, StaffMember, StaffPage } from '../../../core/api/api-types';

const STAFF_PAGE_SIZE = 100;

export interface AuditQuery {
  readonly actorId?: string;
  readonly entityType?: string;
  readonly entityId?: string;
  readonly action?: string;
  readonly from?: string;
  readonly to?: string;
  readonly page: number;
  readonly size: number;
}

@Injectable()
export class AuditApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  search(query: AuditQuery): Observable<AuditPage> {
    let params = new HttpParams();
    for (const [name, value] of Object.entries(query)) {
      if (value !== undefined && value !== '') {
        params = params.set(name, value);
      }
    }
    return this.http.get<AuditPage>(`${this.base}/audit-entries`, { params });
  }

  // The audit rows carry only an actor id; the accounts turn it into an email.
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

  timeZone(): Observable<string> {
    return this.http
      .get<{ timeZone: string }>(`${this.base}/admin/settings`)
      .pipe(map((settings) => settings.timeZone));
  }
}
