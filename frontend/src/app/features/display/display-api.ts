import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/api/api-config';
import type { CustomerDisplay } from '../../core/api/api-types';

@Injectable()
export class DisplayApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  read(): Observable<CustomerDisplay> {
    return this.http.get<CustomerDisplay>(`${this.base}/board/display`);
  }
}
