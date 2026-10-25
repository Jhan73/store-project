import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { expand, map, Observable, reduce } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-config';
import type {
  CreateStaffRequest,
  Role,
  StaffMember,
  StaffPage,
} from '../../../core/api/api-types';

const ALL_PAGE_SIZE = 100;

@Injectable()
export class StaffApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  list(page: number, size: number): Observable<StaffPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<StaffPage>(`${this.base}/staff`, { params });
  }

  // The list filters, sorts and pages the whole staff in the browser.
  all(): Observable<StaffMember[]> {
    return this.list(0, ALL_PAGE_SIZE).pipe(
      expand((result) =>
        result.page + 1 < result.totalPages ? this.list(result.page + 1, ALL_PAGE_SIZE) : [],
      ),
      reduce<StaffPage, StaffMember[]>((all, result) => [...all, ...result.content], []),
    );
  }

  create(body: CreateStaffRequest): Observable<StaffMember> {
    return this.http.post<StaffMember>(`${this.base}/staff`, body);
  }

  rename(id: string, username: string): Observable<StaffMember> {
    return this.http.patch<StaffMember>(`${this.base}/staff/${id}/username`, { username });
  }

  changeRole(id: string, role: Role): Observable<StaffMember> {
    return this.http.patch<StaffMember>(`${this.base}/staff/${id}/role`, { role });
  }

  deactivate(id: string): Observable<StaffMember> {
    return this.http.post<StaffMember>(`${this.base}/staff/${id}/deactivate`, null);
  }

  reactivate(id: string): Observable<StaffMember> {
    return this.http.post<StaffMember>(`${this.base}/staff/${id}/reactivate`, null);
  }

  resendSetPasswordLink(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/staff/${id}/set-password-link`, null);
  }

  timeZone(): Observable<string> {
    return this.http
      .get<{ timeZone: string }>(`${this.base}/admin/settings`)
      .pipe(map((settings) => settings.timeZone));
  }
}
