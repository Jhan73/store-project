import { request, type APIRequestContext } from '@playwright/test';
import { adminCredentials, apiUrl } from './env';

export const E2E_NAME = /^e2e-\d{13}-/;

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
  return `e2e-${Date.now()}-${label}`;
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
  // deactivated; modifier groups are deleted after being detached from the products that used them. Items are
  // processed independently and failures are reported together at the end.
  async cleanUp(): Promise<void> {
    const errors: string[] = [];
    const attempt = async (what: string, action: () => Promise<unknown>) => {
      try {
        await action();
      } catch (error) {
        errors.push(`${what}: ${error instanceof Error ? error.message : String(error)}`);
      }
    };

    const products = await this.products();
    const usedByOthers = new Set(
      products.filter((product) => !E2E_NAME.test(product.name)).flatMap((product) => product.modifierGroupIds),
    );
    for (const product of products.filter((candidate) => E2E_NAME.test(candidate.name))) {
      await attempt(`product ${product.id}`, async () => {
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
      });
    }
    for (const group of await this.get<Versioned[]>('/api/v1/admin/modifier-groups')) {
      if (!E2E_NAME.test(group.name)) continue;
      if (usedByOthers.has(group.id)) {
        console.warn(`Skipped modifier group ${group.id}: it is attached to a product this suite did not create.`);
        continue;
      }
      await attempt(`modifier group ${group.id}`, () =>
        this.send('DELETE', `/api/v1/admin/modifier-groups/${group.id}`, undefined, group.etag),
      );
    }
    for (const category of await this.get<Category[]>('/api/v1/admin/categories')) {
      if (E2E_NAME.test(category.name) && category.active) {
        await attempt(`category ${category.id}`, () =>
          this.send('POST', `/api/v1/admin/categories/${category.id}/deactivate`, undefined, category.etag),
        );
      }
    }
    if (errors.length > 0) {
      throw new Error([`Cleanup left ${errors.length} item(s) behind:`, ...errors].join('\n'));
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
