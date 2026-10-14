import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../api/api-config';
import type { ActiveReason, ReasonType } from '../api/api-types';

// The active reasons floor staff may pick from; managing the lists is the admin screens' job.
@Injectable({ providedIn: 'root' })
export class ReasonsApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  active(type: ReasonType): Observable<ActiveReason[]> {
    return this.http.get<ActiveReason[]>(`${this.base}/reasons`, {
      params: new HttpParams().set('type', type),
    });
  }
}
