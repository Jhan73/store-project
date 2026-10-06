import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-config';
import type {
  CreateStaffRequest,
  Role,
  StaffMember,
  StaffPage,
} from '../../../core/api/api-types';

@Injectable()
export class StaffApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  list(page: number, size: number): Observable<StaffPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<StaffPage>(`${this.base}/staff`, { params });
  }

  create(body: CreateStaffRequest): Observable<StaffMember> {
    return this.http.post<StaffMember>(`${this.base}/staff`, body);
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
