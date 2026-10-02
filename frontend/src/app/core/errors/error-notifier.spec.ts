import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { ApiError } from './api-error';
import { ErrorNotifier } from './error-notifier';

describe('ErrorNotifier', () => {
  function setup() {
    TestBed.configureTestingModule({ providers: [MessageService] });
    const add = vi.spyOn(TestBed.inject(MessageService), 'add');
    return { notifier: TestBed.inject(ErrorNotifier), add };
  }

  it('shows the localized message, never the backend text', () => {
    const { notifier, add } = setup();

    notifier.show(
      new ApiError({
        status: 404,
        code: 'catalog.product-not-found',
        correlationId: null,
        fieldErrors: [],
        properties: {},
      }),
    );

    expect(add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', summary: 'El producto no existe.' }),
    );
  });

  it('adds the correlation id so support can find the logs', () => {
    const { notifier, add } = setup();

    notifier.show(
      new ApiError({
        status: 500,
        code: 'common.internal-error',
        correlationId: 'abc-123',
        fieldErrors: [],
        properties: {},
      }),
    );

    expect(add).toHaveBeenCalledWith(expect.objectContaining({ detail: expect.stringContaining('abc-123') }));
  });

  it('copes with errors that are not ApiError', () => {
    const { notifier, add } = setup();

    notifier.show(new Error('boom'));

    expect(add).toHaveBeenCalledWith(
      expect.objectContaining({ summary: 'No pudimos completar la operación. Inténtalo de nuevo.' }),
    );
  });
});
