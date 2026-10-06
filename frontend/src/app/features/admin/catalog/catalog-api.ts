import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/api/api-config';
import type {
  Allergen,
  Availability,
  Category,
  CreateCategoryRequest,
  ModifierGroup,
  Product,
  ProductPage,
  SaveModifierGroupRequest,
  SaveProductRequest,
  SaveStationRequest,
  Station,
  UpdateCategoryRequest,
} from '../../../core/api/api-types';

export interface ProductQuery {
  readonly categoryId?: string;
  readonly page: number;
  readonly size: number;
}

// Every catalog update carries the `etag` of the item as it was listed or read; the backend answers 412 when it moved.
function ifMatch(etag: string) {
  return { headers: { 'If-Match': etag } };
}

@Injectable()
export class CatalogApi {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  currency(): Observable<string> {
    return this.http
      .get<{ currency: string }>(`${this.base}/admin/settings`)
      .pipe(map((settings) => settings.currency));
  }

  allergens(): Observable<Allergen[]> {
    return this.http.get<Allergen[]>(`${this.base}/admin/allergens`);
  }

  stations(): Observable<Station[]> {
    return this.http.get<Station[]>(`${this.base}/admin/stations`);
  }

  createStation(body: SaveStationRequest): Observable<Station> {
    return this.http.post<Station>(`${this.base}/admin/stations`, body);
  }

  renameStation(id: string, etag: string, body: SaveStationRequest): Observable<Station> {
    return this.http.put<Station>(`${this.base}/admin/stations/${id}`, body, ifMatch(etag));
  }

  categories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${this.base}/admin/categories`);
  }

  createCategory(body: CreateCategoryRequest): Observable<Category> {
    return this.http.post<Category>(`${this.base}/admin/categories`, body);
  }

  changeCategory(id: string, etag: string, body: UpdateCategoryRequest): Observable<Category> {
    return this.http.put<Category>(`${this.base}/admin/categories/${id}`, body, ifMatch(etag));
  }

  deactivateCategory(id: string, etag: string): Observable<Category> {
    return this.http.post<Category>(
      `${this.base}/admin/categories/${id}/deactivate`,
      null,
      ifMatch(etag),
    );
  }

  reactivateCategory(id: string, etag: string): Observable<Category> {
    return this.http.post<Category>(
      `${this.base}/admin/categories/${id}/reactivate`,
      null,
      ifMatch(etag),
    );
  }

  modifierGroups(): Observable<ModifierGroup[]> {
    return this.http.get<ModifierGroup[]>(`${this.base}/admin/modifier-groups`);
  }

  modifierGroup(id: string): Observable<ModifierGroup> {
    return this.http.get<ModifierGroup>(`${this.base}/admin/modifier-groups/${id}`);
  }

  createModifierGroup(body: SaveModifierGroupRequest): Observable<ModifierGroup> {
    return this.http.post<ModifierGroup>(`${this.base}/admin/modifier-groups`, body);
  }

  changeModifierGroup(
    id: string,
    etag: string,
    body: SaveModifierGroupRequest,
  ): Observable<ModifierGroup> {
    return this.http.put<ModifierGroup>(
      `${this.base}/admin/modifier-groups/${id}`,
      body,
      ifMatch(etag),
    );
  }

  deleteModifierGroup(id: string, etag: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/admin/modifier-groups/${id}`, ifMatch(etag));
  }

  products(query: ProductQuery): Observable<ProductPage> {
    let params = new HttpParams();
    if (query.categoryId) {
      params = params.set('categoryId', query.categoryId);
    }
    params = params.set('page', query.page).set('size', query.size);
    return this.http.get<ProductPage>(`${this.base}/admin/products`, { params });
  }

  product(id: string): Observable<Product> {
    return this.http.get<Product>(`${this.base}/admin/products/${id}`);
  }

  createProduct(body: SaveProductRequest): Observable<Product> {
    return this.http.post<Product>(`${this.base}/admin/products`, body);
  }

  changeProduct(id: string, etag: string, body: SaveProductRequest): Observable<Product> {
    return this.http.put<Product>(`${this.base}/admin/products/${id}`, body, ifMatch(etag));
  }

  deactivateProduct(id: string, etag: string): Observable<Product> {
    return this.http.post<Product>(
      `${this.base}/admin/products/${id}/deactivate`,
      null,
      ifMatch(etag),
    );
  }

  reactivateProduct(id: string, etag: string): Observable<Product> {
    return this.http.post<Product>(
      `${this.base}/admin/products/${id}/reactivate`,
      null,
      ifMatch(etag),
    );
  }

  replaceProductImage(id: string, etag: string, file: File): Observable<Product> {
    const form = new FormData();
    form.append('file', file);
    return this.http.put<Product>(`${this.base}/admin/products/${id}/image`, form, ifMatch(etag));
  }

  removeProductImage(id: string, etag: string): Observable<Product> {
    return this.http.delete<Product>(`${this.base}/admin/products/${id}/image`, ifMatch(etag));
  }

  setProductAvailability(id: string, available: boolean): Observable<Availability> {
    return this.http.put<Availability>(`${this.base}/catalog/products/${id}/availability`, {
      available,
    });
  }

  setOptionAvailability(id: string, available: boolean): Observable<Availability> {
    return this.http.put<Availability>(`${this.base}/catalog/modifier-options/${id}/availability`, {
      available,
    });
  }
}
