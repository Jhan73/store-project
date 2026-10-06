import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { Allergen, Category, ModifierGroup, Product } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import {
  chooseOption,
  optionLabels,
  selectedLabel,
  selectIsInvalid,
  typeNumber,
} from '../../../testing/primeng-controls';
import { CatalogApi } from './catalog-api';
import { ProductForm } from './product-form';

const API = 'http://api.test/api/v1';
const ALLERGENS: Allergen[] = ['MILK', 'PEANUTS'];

const categories: Category[] = [
  { id: 'c1', name: 'Juices', displayOrder: 1, stationId: 's1', active: true, etag: '"1"' },
  { id: 'c2', name: 'Snacks', displayOrder: 2, stationId: 's1', active: true, etag: '"1"' },
];

const group = (id: string, name: string): ModifierGroup => ({
  id,
  name,
  required: false,
  minChoices: 0,
  maxChoices: 1,
  etag: '"1"',
  options: [],
});
const groups = [group('g1', 'Size'), group('g2', 'Extras'), group('g3', 'Ice')];

const orange: Product = {
  id: 'p1',
  name: 'Orange juice',
  description: 'Fresh',
  categoryId: 'c1',
  price: { amount: '9.50', currency: 'PEN' },
  displayOrder: 2,
  quickSalePinned: true,
  allergens: ['MILK'],
  modifierGroupIds: ['g2', 'g1'],
  imageUrl: null,
  active: true,
  available: true,
  etag: '"4"',
};

async function render(product?: Product) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      MessageService,
      CatalogApi,
      { provide: API_ORIGIN, useValue: 'http://api.test' },
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap(product ? { id: product.id } : {}) } },
      },
    ],
  });
  const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  const fixture = TestBed.createComponent(ProductForm);
  const http = TestBed.inject(HttpTestingController);
  await fixture.whenStable();
  http.expectOne(`${API}/admin/settings`).flush({ currency: 'PEN' });
  http.expectOne(`${API}/admin/allergens`).flush(ALLERGENS);
  http.expectOne(`${API}/admin/categories`).flush(categories);
  http.expectOne(`${API}/admin/modifier-groups`).flush(groups);
  if (product) {
    http.expectOne(`${API}/admin/products/${product.id}`).flush(product);
  }
  await fixture.whenStable();
  return {
    fixture,
    host: fixture.nativeElement as HTMLElement,
    http,
    navigate,
    messages: TestBed.inject(MessageService),
  };
}

function type(host: HTMLElement, selector: string, value: string) {
  const input = host.querySelector<HTMLInputElement>(selector)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function submit(host: HTMLElement) {
  host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
}

async function click(
  fixture: { whenStable(): Promise<unknown> },
  host: HTMLElement,
  testId: string,
) {
  host.querySelector<HTMLElement>(`[data-testid="${testId}"]`)!.click();
  await fixture.whenStable();
}

function pickFile(host: HTMLElement, file: File) {
  const input = host.querySelector<HTMLInputElement>('p-fileupload input[type="file"]')!;
  Object.defineProperty(input, 'files', { value: [file], configurable: true });
  input.dispatchEvent(new Event('change'));
}

const attachedNames = (host: HTMLElement) =>
  Array.from(host.querySelectorAll('[data-testid^="group-"] .group-name')).map(
    (name) => name.textContent?.trim() ?? '',
  );

function problem(status: number, code: string, extra: object = {}) {
  return {
    body: { type: 'about:blank', status, code, correlationId: 'c-1', ...extra },
    init: { status, statusText: 'x' },
  };
}

describe('ProductForm', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  describe('creating', () => {
    it('creates a product with its modifier groups in the chosen order', async () => {
      const { fixture, host, http, navigate } = await render();

      type(host, '#product-name', 'Orange juice');
      type(host, '#product-description', 'Fresh');
      await chooseOption(fixture, host, 'product-category', 'Juices');
      type(host, '#product-price', '9,5');
      typeNumber(host, 'product-order', '2');
      host.querySelector<HTMLInputElement>('p-checkbox #product-pinned')!.click();
      host.querySelector<HTMLInputElement>('app-allergen-picker p-checkbox input')!.click();
      await chooseOption(fixture, host, 'product-group-add', 'Extras');
      await click(fixture, host, 'add-group');
      await chooseOption(fixture, host, 'product-group-add', 'Size');
      await click(fixture, host, 'add-group');
      submit(host);

      const request = http.expectOne(`${API}/admin/products`);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({
        name: 'Orange juice',
        description: 'Fresh',
        categoryId: 'c1',
        price: { amount: '9.50', currency: 'PEN' },
        displayOrder: 2,
        quickSalePinned: true,
        allergens: ['MILK'],
        modifierGroupIds: ['g2', 'g1'],
      });
      request.flush({ ...orange, id: 'p9' }, { status: 201, statusText: 'Created' });
      await fixture.whenStable();

      expect(navigate).toHaveBeenCalledWith(['/admin/catalog/products', 'p9']);
    });

    it('sends no description when it is left blank', async () => {
      const { fixture, host, http } = await render();

      type(host, '#product-name', 'Water');
      await chooseOption(fixture, host, 'product-category', 'Snacks');
      type(host, '#product-price', '3');
      submit(host);

      expect(http.expectOne(`${API}/admin/products`).request.body.description).toBeNull();
    });

    it('offers only the groups that are not attached yet, and attaches each once', async () => {
      const { fixture, host } = await render();
      const offered = () => optionLabels(fixture, host, 'product-group-add');
      expect(await offered()).toEqual(['Size', 'Extras', 'Ice']);

      await chooseOption(fixture, host, 'product-group-add', 'Extras');
      await click(fixture, host, 'add-group');

      expect(await offered()).toEqual(['Size', 'Ice']);
      expect(selectedLabel(host, 'product-group-add')).toBe('Elige un grupo');
      expect(attachedNames(host)).toEqual(['Extras']);
    });

    it('reorders and removes attached groups', async () => {
      const { fixture, host, http } = await render();
      for (const name of ['Size', 'Extras', 'Ice']) {
        await chooseOption(fixture, host, 'product-group-add', name);
        await click(fixture, host, 'add-group');
      }

      await click(fixture, host, 'move-down-0');
      expect(attachedNames(host)).toEqual(['Extras', 'Size', 'Ice']);

      await click(fixture, host, 'move-up-2');
      expect(attachedNames(host)).toEqual(['Extras', 'Ice', 'Size']);

      await click(fixture, host, 'remove-group-0');
      expect(attachedNames(host)).toEqual(['Ice', 'Size']);
      type(host, '#product-name', 'Mix');
      await chooseOption(fixture, host, 'product-category', 'Juices');
      type(host, '#product-price', '5');
      submit(host);

      expect(http.expectOne(`${API}/admin/products`).request.body.modifierGroupIds).toEqual([
        'g3',
        'g1',
      ]);
    });

    it('sends nothing and marks the fields when the basics are missing', async () => {
      const { fixture, host } = await render();

      type(host, '#product-price', '0');
      submit(host);
      await fixture.whenStable();

      for (const id of ['#product-name', '#product-price']) {
        expect(host.querySelector(id)!.getAttribute('aria-invalid')).toBe('true');
      }
      expect(selectIsInvalid(host, 'product-category')).toBe(true);
    });

    it('tells the admin that the image comes after the first save', async () => {
      const { host } = await render();

      expect(host.querySelector('p-fileupload')).toBeNull();
      expect(host.textContent).toContain('después de guardar');
    });

    it('marks the field the server rejected', async () => {
      const { fixture, host, http } = await render();

      type(host, '#product-name', 'Water');
      await chooseOption(fixture, host, 'product-category', 'Snacks');
      type(host, '#product-price', '3');
      submit(host);
      const { body, init } = problem(400, 'common.validation-failed', {
        errors: [{ field: 'name', constraint: 'Size' }],
      });
      http.expectOne(`${API}/admin/products`).flush(body, init);
      await fixture.whenStable();

      expect(host.querySelector('#product-name')!.getAttribute('aria-invalid')).toBe('true');
    });
  });

  describe('editing', () => {
    it('shows the saved product with its groups in order', async () => {
      const { host } = await render(orange);

      expect(host.querySelector<HTMLInputElement>('#product-name')!.value).toBe('Orange juice');
      expect(host.querySelector<HTMLTextAreaElement>('textarea.p-textarea#product-description')!.value).toBe(
        'Fresh',
      );
      expect(selectedLabel(host, 'product-category')).toBe('Juices');
      expect(host.querySelector<HTMLInputElement>('#product-price')!.value).toBe('9.50');
      expect(host.querySelector<HTMLInputElement>('p-inputnumber #product-order')!.value).toBe('2');
      expect(host.querySelector<HTMLInputElement>('p-checkbox #product-pinned')!.checked).toBe(true);
      expect(attachedNames(host)).toEqual(['Extras', 'Size']);
    });

    it('replaces the product with the version it was read with', async () => {
      const { fixture, host, http, navigate } = await render(orange);

      type(host, '#product-price', '10');
      submit(host);
      const request = http.expectOne(`${API}/admin/products/p1`);
      expect(request.request.method).toBe('PUT');
      expect(request.request.headers.get('If-Match')).toBe('"4"');
      expect(request.request.body).toMatchObject({
        price: { amount: '10.00', currency: 'PEN' },
        modifierGroupIds: ['g2', 'g1'],
      });
      request.flush(orange);
      await fixture.whenStable();

      expect(navigate).toHaveBeenCalledWith(['/admin/catalog/products']);
    });

    it('re-reads the product and says so when it changed in the meantime', async () => {
      const { fixture, host, http, messages } = await render(orange);
      const add = vi.spyOn(messages, 'add');

      submit(host);
      const { body, init } = problem(412, 'common.precondition-failed');
      http.expectOne(`${API}/admin/products/p1`).flush(body, init);
      await fixture.whenStable();
      http.expectOne(`${API}/admin/products/p1`).flush({ ...orange, name: 'Juice', etag: '"5"' });
      await fixture.whenStable();

      expect(add).toHaveBeenCalledWith(
        expect.objectContaining({ summary: expect.stringContaining('cambiaron') }),
      );
      expect(host.querySelector<HTMLInputElement>('#product-name')!.value).toBe('Juice');
    });
  });

  describe('image', () => {
    const png = () => new File(['x'], 'juice.png', { type: 'image/png' });

    it('uploads the picked file with the product version and shows the new image', async () => {
      const { fixture, host, http } = await render(orange);

      pickFile(host, png());
      const request = http.expectOne(`${API}/admin/products/p1/image`);
      expect(request.request.method).toBe('PUT');
      expect(request.request.headers.get('If-Match')).toBe('"4"');
      expect((request.request.body as FormData).get('file')).toBeInstanceOf(File);
      request.flush({ ...orange, imageUrl: 'https://cdn.test/products/a.png', etag: '"5"' });
      await fixture.whenStable();

      expect(host.querySelector('img')!.getAttribute('src')).toBe('https://cdn.test/products/a.png');

      submit(host);
      expect(http.expectOne(`${API}/admin/products/p1`).request.headers.get('If-Match')).toBe('"5"');
    });

    it.each([
      ['a file that is not PNG, JPEG or WebP', () => new File(['x'], 'a.gif', { type: 'image/gif' })],
      [
        'a file above 2 MB',
        () => new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'big.png', { type: 'image/png' }),
      ],
    ])('rejects %s without sending it', async (_name, make) => {
      const { fixture, host } = await render(orange);

      pickFile(host, make());
      await fixture.whenStable();

      expect(host.querySelector('p-fileupload p-message')?.textContent).toContain('2 MB');
    });

    it('removes the image with the product version', async () => {
      const withImage = { ...orange, imageUrl: 'https://cdn.test/products/a.png' };
      const { fixture, host, http } = await render(withImage);

      await click(fixture, host, 'remove-image');
      const request = http.expectOne(`${API}/admin/products/p1/image`);
      expect(request.request.method).toBe('DELETE');
      expect(request.request.headers.get('If-Match')).toBe('"4"');
      request.flush({ ...orange, etag: '"5"' });
      await fixture.whenStable();

      expect(host.querySelector('img')).toBeNull();
    });

    it('explains a rejected image', async () => {
      const { fixture, host, http, messages } = await render(orange);
      const add = vi.spyOn(messages, 'add');

      pickFile(host, png());
      const { body, init } = problem(422, 'catalog.invalid-image');
      http.expectOne(`${API}/admin/products/p1/image`).flush(body, init);
      await fixture.whenStable();

      expect(add).toHaveBeenCalledWith(
        expect.objectContaining({ summary: expect.stringContaining('imagen') }),
      );
    });
  });
});
