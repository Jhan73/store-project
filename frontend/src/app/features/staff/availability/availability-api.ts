import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-config';
import type { Availability, Menu } from '../../../core/api/api-types';

@Injectable()
export class AvailabilityApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  menu(): Observable<Menu> {
    return this.http.get<Menu>(`${this.base}/catalog/menu`);
  }

  setProduct(id: string, available: boolean): Observable<Availability> {
    return this.http.put<Availability>(`${this.base}/catalog/products/${id}/availability`, {
      available,
    });
  }

  setOption(id: string, available: boolean): Observable<Availability> {
    return this.http.put<Availability>(`${this.base}/catalog/modifier-options/${id}/availability`, {
      available,
    });
  }
}
