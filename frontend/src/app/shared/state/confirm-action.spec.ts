import { TestBed } from '@angular/core/testing';
import { Confirmation, ConfirmationService } from 'primeng/api';
import { ConfirmAction } from './confirm-action';

describe('ConfirmAction', () => {
  function setup() {
    const requests: Confirmation[] = [];
    TestBed.configureTestingModule({ providers: [ConfirmationService] });
    TestBed.inject(ConfirmationService).requireConfirmation$.subscribe((c) => c && requests.push(c));
    return { action: TestBed.inject(ConfirmAction), requests };
  }

  it('shows the message with its consequences and resolves true when accepted', async () => {
    const { action, requests } = setup();

    const answer = action.ask({ message: 'Se cerrarán las sesiones.', header: 'Desactivar usuario' });
    expect(requests[0].message).toBe('Se cerrarán las sesiones.');
    expect(requests[0].header).toBe('Desactivar usuario');
    requests[0].accept?.();

    await expect(answer).resolves.toBe(true);
  });

  it('resolves false when rejected or dismissed', async () => {
    const { action, requests } = setup();

    const answer = action.ask({ message: 'Se ocultarán 3 productos.' });
    requests[0].reject?.();

    await expect(answer).resolves.toBe(false);
  });

  it('uses a danger accept button by default and a plain one when asked', () => {
    const { action, requests } = setup();

    void action.ask({ message: 'a' });
    void action.ask({ message: 'b', destructive: false });

    expect(requests[0].acceptButtonProps?.severity).toBe('danger');
    expect(requests[1].acceptButtonProps?.severity).toBeUndefined();
  });
});
