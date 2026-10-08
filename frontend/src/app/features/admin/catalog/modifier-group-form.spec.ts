import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { Allergen, ModifierGroup } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { typeNumber } from '../../../testing/primeng-controls';
import { CatalogApi } from './catalog-api';
import { ModifierGroupForm } from './modifier-group-form';

const API = 'http://api.test/api/v1';
const ALLERGENS: Allergen[] = ['MILK', 'PEANUTS'];

const size: ModifierGroup = {
  id: 'g1',
  name: 'Size',
  required: true,
  minChoices: 1,
  maxChoices: 1,
  etag: '"7"',
  options: [
    {
      id: 'o1',
      name: 'Small',
      priceDelta: { amount: '0.00', currency: 'PEN' },
      allergens: [],
      available: true,
    },
    {
      id: 'o2',
      name: 'Large',
      priceDelta: { amount: '2.00', currency: 'PEN' },
      allergens: ['MILK'],
      available: false,
    },
  ],
};

async function render(group?: ModifierGroup) {
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
        useValue: { snapshot: { paramMap: convertToParamMap(group ? { id: group.id } : {}) } },
      },
    ],
  });
  const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  const fixture = TestBed.createComponent(ModifierGroupForm);
  const http = TestBed.inject(HttpTestingController);
  await fixture.whenStable();
  http.expectOne(`${API}/admin/settings`).flush({ currency: 'PEN' });
  http.expectOne(`${API}/admin/allergens`).flush(ALLERGENS);
  if (group) {
    http.expectOne(`${API}/admin/modifier-groups/${group.id}`).flush(group);
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

const switchOf = (host: HTMLElement, id: string) =>
  host.querySelector<HTMLInputElement>(`p-toggleswitch #${id}`)!;

function type(host: HTMLElement, selector: string, value: string) {
  const input = host.querySelector<HTMLInputElement>(selector)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function submit(host: HTMLElement) {
  host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
}

function problem(status: number, code: string, extra: object = {}) {
  return {
    body: { type: 'about:blank', status, code, correlationId: 'c-1', ...extra },
    init: { status, statusText: 'x' },
  };
}

describe('ModifierGroupForm', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  describe('creating', () => {
    it('starts with one empty option and sends the group with the store currency', async () => {
      const { fixture, host, http, navigate } = await render();
      expect(host.querySelectorAll('[data-testid^="option-"]')).toHaveLength(1);

      type(host, '#group-name', 'Extras');
      typeNumber(host, 'group-max', '2');
      type(host, '#option-0-name', 'Honey');
      type(host, '#option-0-price', '1,5');
      submit(host);
      const request = http.expectOne(`${API}/admin/modifier-groups`);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({
        name: 'Extras',
        required: false,
        minChoices: 0,
        maxChoices: 2,
        options: [
          { name: 'Honey', priceDelta: { amount: '1.50', currency: 'PEN' }, allergens: [] },
        ],
      });
      request.flush({ ...size, name: 'Extras', required: false, minChoices: 0, maxChoices: 2 });
      await fixture.whenStable();

      expect(navigate).toHaveBeenCalledWith(['/admin/catalog/modifier-groups']);
    });

    it('sends the allergens ticked on an option', async () => {
      const { fixture, host, http } = await render();

      type(host, '#group-name', 'Extras');
      type(host, '#option-0-name', 'Peanut butter');
      host
        .querySelector<HTMLInputElement>('[data-testid="option-0"] app-allergen-picker input')!
        .click();
      await fixture.whenStable();
      submit(host);

      const body = http.expectOne(`${API}/admin/modifier-groups`).request.body;
      expect(body.options[0].allergens).toEqual(['MILK']);
    });

    it('adds and removes options', async () => {
      const { fixture, host } = await render();

      host.querySelector<HTMLButtonElement>('[data-testid="add-option"]')!.click();
      await fixture.whenStable();
      expect(host.querySelectorAll('[data-testid^="option-"]')).toHaveLength(2);

      host.querySelector<HTMLButtonElement>('[data-testid="remove-option-0"]')!.click();
      await fixture.whenStable();
      expect(host.querySelectorAll('[data-testid^="option-"]')).toHaveLength(1);
    });

    it('raises the minimum to one when the group becomes required, and back to zero when not', async () => {
      const { fixture, host } = await render();
      const min = () => host.querySelector<HTMLInputElement>('p-inputnumber #group-min')!.value;

      host.querySelector<HTMLInputElement>('p-checkbox #group-required')!.click();
      await fixture.whenStable();
      expect(min()).toBe('1');

      host.querySelector<HTMLInputElement>('p-checkbox #group-required')!.click();
      await fixture.whenStable();
      expect(min()).toBe('0');
    });

    it('explains a broken selection rule and sends nothing', async () => {
      const { fixture, host } = await render();

      type(host, '#group-name', 'Size');
      typeNumber(host, 'group-max', '1');
      typeNumber(host, 'group-min', '3');
      type(host, '#option-0-name', 'Small');
      submit(host);
      await fixture.whenStable();

      expect(host.querySelector('[data-testid="violations"]')?.textContent).toContain(
        'más opciones',
      );
    });

    it('does not send a blank option name or a malformed price', async () => {
      const { fixture, host } = await render();

      type(host, '#group-name', 'Extras');
      type(host, '#option-0-price', '1.234');
      submit(host);
      await fixture.whenStable();

      expect(host.querySelector('#option-0-name')!.getAttribute('aria-invalid')).toBe('true');
      expect(host.querySelector('#option-0-price')!.getAttribute('aria-invalid')).toBe('true');
    });

    it('marks the option field the server rejected', async () => {
      const { fixture, host, http } = await render();

      type(host, '#group-name', 'Extras');
      type(host, '#option-0-name', 'Honey');
      submit(host);
      const { body, init } = problem(400, 'common.validation-failed', {
        errors: [{ field: 'options[0].name', constraint: 'Size' }],
      });
      http.expectOne(`${API}/admin/modifier-groups`).flush(body, init);
      await fixture.whenStable();

      expect(host.querySelector('#option-0-name')!.getAttribute('aria-invalid')).toBe('true');
    });
  });

  describe('editing', () => {
    it('shows the saved group with its option versions untouched', async () => {
      const { host } = await render(size);

      expect(host.querySelector<HTMLInputElement>('#group-name')!.value).toBe('Size');
      expect(host.querySelector<HTMLInputElement>('p-checkbox #group-required')!.checked).toBe(true);
      expect(host.querySelector<HTMLInputElement>('#option-1-name')!.value).toBe('Large');
      expect(host.querySelector<HTMLInputElement>('#option-1-price')!.value).toBe('2.00');
      expect(switchOf(host, 'option-0-available').checked).toBe(true);
      expect(switchOf(host, 'option-1-available').checked).toBe(false);
    });

    it('replaces the group with the version it was read with, keeping option ids', async () => {
      const { fixture, host, http, navigate } = await render(size);

      host.querySelector<HTMLButtonElement>('[data-testid="remove-option-0"]')!.click();
      await fixture.whenStable();
      type(host, '#option-0-price', '2.5');
      submit(host);
      const request = http.expectOne(`${API}/admin/modifier-groups/g1`);
      expect(request.request.method).toBe('PUT');
      expect(request.request.headers.get('If-Match')).toBe('"7"');
      expect(request.request.body).toEqual({
        name: 'Size',
        required: true,
        minChoices: 1,
        maxChoices: 1,
        options: [
          {
            id: 'o2',
            name: 'Large',
            priceDelta: { amount: '2.50', currency: 'PEN' },
            allergens: ['MILK'],
          },
        ],
      });
      request.flush(size);
      await fixture.whenStable();

      expect(navigate).toHaveBeenCalledWith(['/admin/catalog/modifier-groups']);
    });

    it('re-reads the group and says so when it changed in the meantime', async () => {
      const { fixture, host, http, messages } = await render(size);
      const add = vi.spyOn(messages, 'add');

      type(host, '#group-name', 'Sizes');
      submit(host);
      const { body, init } = problem(412, 'common.precondition-failed');
      http.expectOne(`${API}/admin/modifier-groups/g1`).flush(body, init);
      await fixture.whenStable();
      http
        .expectOne(`${API}/admin/modifier-groups/g1`)
        .flush({ ...size, name: 'Cup size', etag: '"8"' });
      await fixture.whenStable();

      expect(add).toHaveBeenCalledWith(
        expect.objectContaining({ summary: expect.stringContaining('cambiaron') }),
      );
      expect(host.querySelector<HTMLInputElement>('#group-name')!.value).toBe('Cup size');
    });

    it('marks an option available or not right away, without saving the group', async () => {
      const { fixture, host, http } = await render(size);

      switchOf(host, 'option-1-available').click();
      const request = http.expectOne(`${API}/catalog/modifier-options/o2/availability`);
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toEqual({ available: true });
      request.flush({ id: 'o2', available: true });
      await fixture.whenStable();

      expect(switchOf(host, 'option-1-available').checked).toBe(true);
    });

    it('blocks the option switch while its change is being saved', async () => {
      const { fixture, host, http } = await render(size);
      const box = () => switchOf(host, 'option-1-available');

      box().click();
      await fixture.whenStable();
      expect(box().disabled).toBe(true);
      box().click();
      http
        .expectOne(`${API}/catalog/modifier-options/o2/availability`)
        .flush({ id: 'o2', available: true });
      await fixture.whenStable();

      expect(box().disabled).toBe(false);
      expect(box().checked).toBe(true);
    });

    it('puts the availability switch back when the change fails', async () => {
      const { fixture, host, http, messages } = await render(size);
      const add = vi.spyOn(messages, 'add');

      switchOf(host, 'option-1-available').click();
      await fixture.whenStable();
      const { body, init } = problem(404, 'catalog.modifier-option-not-found');
      http.expectOne(`${API}/catalog/modifier-options/o2/availability`).flush(body, init);
      await fixture.whenStable();

      expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
      expect(switchOf(host, 'option-1-available').checked).toBe(false);
    });
  });
});
