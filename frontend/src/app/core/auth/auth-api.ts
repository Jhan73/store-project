import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api/api-config';
import type { LoginRequest, LoginResponse } from '../api/api-types';

// The backend rejects cookie-authenticated calls without this header; its presence forces a CORS preflight.
const REQUESTED_WITH = { 'X-Requested-With': 'XMLHttpRequest' };

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(API_BASE_URL)}/auth`;

  login(body: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.base}/login`, body, { withCredentials: true });
  }

  refresh(): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.base}/refresh`, null, {
      withCredentials: true,
      headers: REQUESTED_WITH,
    });
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.base}/logout`, null, {
      withCredentials: true,
      headers: REQUESTED_WITH,
    });
  }
}
