import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { WalletProvider } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { ConfirmAction } from '../../../shared/state/confirm-action';
import { SettingsApi } from './settings-api';
import { WalletProviderList } from './wallet-provider-list';

const WALLETS = 'http://api.test/api/v1/admin/wallet-providers';

const yape: WalletProvider = { id: 'w1', name: 'Yape', active: true, etag: '"1"' };
const plin: WalletProvider = { id: 'w2', name: 'Plin', active: true, etag: '"4"' };

const ask = vi.fn(() => Promise.resolve(true));

async function render(providers: WalletProvider[] = [yape, plin]) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      MessageService,
      SettingsApi,
      { provide: ConfirmAction, useValue: { ask } },
      { provide: API_ORIGIN, useValue: 'http://api.test' },
    ],
  });
  const fixture = TestBed.createComponent(WalletProviderList);
  const http = TestBed.inject(HttpTestingController);
  await fixture.whenStable();
  http.expectOne(WALLETS).flush(providers);
  await fixture.whenStable();
  const host = fixture.nativeElement as HTMLElement;
  return { fixture, host, http };
}

function field() {
  return document.body.querySelector<HTMLInputElement>('#wallet-name')!;
}

function type(value: string) {
  field().value = value;
  field().dispatchEvent(new Event('input'));
}

function submit() {
  document.body.querySelector<HTMLFormElement>('[role="dialog"] form')!.dispatchEvent(new Event('submit'));
}

function rows(host: HTMLElement) {
  return Array.from(host.querySelectorAll('tbody tr')).map((row) => row.textContent ?? '');
}

describe('WalletProviderList', () => {
  beforeEach(() => ask.mockReset().mockResolvedValue(true));
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows the state of each provider with a toggle on every row', async () => {
    const { host } = await render([yape, { ...plin, active: false }]);

    const [first, second] = rows(host);
    expect(first).toContain('Activa');
    expect(second).toContain('Inactiva');
    expect(host.querySelector('[data-testid="toggle-w1"]')).toBeTruthy();
    expect(host.querySelector('[data-testid="toggle-w2"]')).toBeTruthy();
    expect(host.querySelector('[data-testid^="delete-"]')).toBeNull();
  });

  it('asks before deactivating and sends the version', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="toggle-w2"]')!.click();
    await fixture.whenStable();
    expect(ask).toHaveBeenCalledWith(expect.objectContaining({ message: expect.stringContaining('Plin') }));
    const request = http.expectOne(`${WALLETS}/w2/deactivate`);
    expect(request.request.headers.get('If-Match')).toBe('"4"');
    request.flush({ ...plin, active: false, etag: '"5"' });
    await fixture.whenStable();

    expect(rows(host)[1]).toContain('Inactiva');
  });

  it('sends nothing when the deactivation is declined', async () => {
    ask.mockResolvedValue(false);
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="toggle-w2"]')!.click();
    await fixture.whenStable();

    http.expectNone(`${WALLETS}/w2/deactivate`);
  });

  it('reactivates straight away without asking', async () => {
    const { fixture, host, http } = await render([yape, { ...plin, active: false }]);

    host.querySelector<HTMLButtonElement>('[data-testid="toggle-w2"]')!.click();
    await fixture.whenStable();
    expect(ask).not.toHaveBeenCalled();
    http.expectOne(`${WALLETS}/w2/reactivate`).flush({ ...plin, active: true, etag: '"6"' });
    await fixture.whenStable();

    expect(rows(host)[1]).toContain('Activa');
  });

  it('creates a provider with the trimmed name', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="add-wallet"]')!.click();
    await fixture.whenStable();
    type('  Tunki ');
    submit();
    const request = http.expectOne(WALLETS);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Tunki' });
    request.flush({ id: 'w3', name: 'Tunki', active: true, etag: '"0"' });
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(3);
    expect(rows(host)[2]).toContain('Tunki');
  });

  it('refuses a blank name without sending anything', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="add-wallet"]')!.click();
    await fixture.whenStable();
    type('   ');
    submit();
    await fixture.whenStable();

    http.expectNone(WALLETS);
    expect(document.body.textContent).toContain('Escribe un nombre válido.');
  });

  it('renames with the version of the row', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="edit-w2"]')!.click();
    await fixture.whenStable();
    expect(field().value).toBe('Plin');
    type('Plin Pay');
    submit();
    const request = http.expectOne(`${WALLETS}/w2`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.headers.get('If-Match')).toBe('"4"');
    expect(request.request.body).toEqual({ name: 'Plin Pay' });
    request.flush({ ...plin, name: 'Plin Pay', etag: '"5"' });
    await fixture.whenStable();

    expect(rows(host)[1]).toContain('Plin Pay');
  });

  it('re-reads the list when the version is stale', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="edit-w2"]')!.click();
    await fixture.whenStable();
    type('Plin Pay');
    submit();
    http
      .expectOne(`${WALLETS}/w2`)
      .flush(
        { type: 'about:blank', title: 'Precondition failed', status: 412, code: 'common.precondition-failed' },
        { status: 412, statusText: 'Precondition Failed' },
      );
    await fixture.whenStable();

    http.expectOne(WALLETS).flush([yape, { ...plin, name: 'Plin 2', etag: '"9"' }]);
    await fixture.whenStable();
    expect(rows(host)[1]).toContain('Plin 2');
  });
});
