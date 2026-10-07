import { request, type APIRequestContext } from '@playwright/test';
import { adminCredentials, apiUrl } from './env';

export const RUN_PREFIX = 'e2e-';

export interface Money {
  amount: string;
  currency: string;
}
interface Versioned {
  id: string;
  etag: string;
  name: string;
}
interface Product extends Versioned {
  active: boolean;
  categoryId: string;
  description: string | null;
  displayOrder: number;
  price: Money;
  quickSalePinned: boolean;
  modifierGroupIds: string[];
  allergens: string[];
}
interface Category extends Versioned {
  active: boolean;
}
interface Page<T> {
  content: T[];
  totalPages: number;
}

export function uniqueName(label: string): string {
  return `${RUN_PREFIX}${Date.now()}-${label}`;
}

export class AdminApi {
  private constructor(private readonly http: APIRequestContext) {}

  // A separate sign-in from the browser sessions: its refresh cookie is never reused.
  static async create(): Promise<AdminApi> {
    const { email, password } = adminCredentials();
    const anonymous = await request.newContext({ baseURL: apiUrl });
    const response = await anonymous.post('/api/v1/auth/login', { data: { email, password } });
    if (!response.ok()) {
      throw new Error(`Admin API sign-in failed with status ${response.status()}.`);
    }
    const { accessToken } = (await response.json()) as { accessToken: string };
    await anonymous.dispose();
    return new AdminApi(
      await request.newContext({
        baseURL: apiUrl,
        extraHTTPHeaders: { Authorization: `Bearer ${accessToken}` },
      }),
    );
  }

  async dispose(): Promise<void> {
    await this.http.dispose();
  }

  async currency(): Promise<string> {
    const settings = await this.get<{ currency: string }>('/api/v1/admin/settings');
    return settings.currency;
  }

  async createCategory(name: string): Promise<Category> {
    return this.send<Category>('POST', '/api/v1/admin/categories', { name, displayOrder: 0 });
  }

  async createProduct(name: string, categoryId: string): Promise<Product> {
    const currency = await this.currency();
    return this.send<Product>('POST', '/api/v1/admin/products', {
      name,
      categoryId,
      displayOrder: 0,
      price: { amount: '5.00', currency },
      quickSalePinned: false,
    });
  }

  // Removes everything this suite created, whichever run left it behind. Products and categories can only be
  // deactivated; modifier groups are deleted after being detached from the products that used them.
  async cleanUp(): Promise<void> {
    for (const product of await this.products()) {
      if (!product.name.startsWith(RUN_PREFIX)) continue;
      let current: Product = product;
      if (current.modifierGroupIds.length > 0) {
        current = await this.send<Product>(
          'PUT',
          `/api/v1/admin/products/${current.id}`,
          this.saveBody(current, []),
          current.etag,
        );
      }
      if (current.active) {
        await this.send('POST', `/api/v1/admin/products/${current.id}/deactivate`, undefined, current.etag);
      }
    }
    for (const group of await this.get<Versioned[]>('/api/v1/admin/modifier-groups')) {
      if (group.name.startsWith(RUN_PREFIX)) {
        await this.send('DELETE', `/api/v1/admin/modifier-groups/${group.id}`, undefined, group.etag);
      }
    }
    for (const category of await this.get<Category[]>('/api/v1/admin/categories')) {
      if (category.name.startsWith(RUN_PREFIX) && category.active) {
        await this.send('POST', `/api/v1/admin/categories/${category.id}/deactivate`, undefined, category.etag);
      }
    }
  }

  private saveBody(product: Product, modifierGroupIds: string[]) {
    return {
      name: product.name,
      description: product.description,
      categoryId: product.categoryId,
      displayOrder: product.displayOrder,
      price: product.price,
      quickSalePinned: product.quickSalePinned,
      allergens: product.allergens,
      modifierGroupIds,
    };
  }

  private async products(): Promise<Product[]> {
    const all: Product[] = [];
    for (let page = 0; ; page++) {
      const result = await this.get<Page<Product>>(`/api/v1/admin/products?page=${page}&size=100`);
      all.push(...result.content);
      if (page + 1 >= result.totalPages) return all;
    }
  }

  private async get<T>(path: string): Promise<T> {
    return this.send<T>('GET', path);
  }

  private async send<T>(method: string, path: string, data?: unknown, etag?: string): Promise<T> {
    const response = await this.http.fetch(path, {
      method,
      data,
      headers: etag ? { 'If-Match': etag } : undefined,
    });
    if (!response.ok()) {
      throw new Error(`${method} ${path} failed with status ${response.status()}: ${await response.text()}`);
    }
    return response.status() === 204 ? (undefined as T) : ((await response.json()) as T);
  }
}
