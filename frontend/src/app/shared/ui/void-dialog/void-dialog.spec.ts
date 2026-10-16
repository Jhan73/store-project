import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import type { ActiveReason } from '../../../core/api/api-types';
import { chooseOption } from '../../../testing/primeng-controls';
import { VoidDialog } from './void-dialog';

interface Confirmed {
  reason: string;
  note: string | null;
}

@Component({
  imports: [VoidDialog],
  template: `
    <app-void-dialog
      header="Anular pago"
      confirmLabel="Anular pago"
      confirmTooltip="Anular este pago con el motivo elegido"
      [visible]="visible()"
      [reasons]="reasons()"
      [failed]="failed()"
      [busy]="busy()"
      (confirmed)="confirmed.push($event)"
      (dismissed)="visible.set(false)"
      (retried)="retried = retried + 1"
    />
  `,
})
class Host {
  readonly visible = signal(true);
  readonly reasons = signal<ActiveReason[] | null>([
    { type: 'VOID', code: 'Error' },
    { type: 'VOID', code: 'Other' },
  ] as ActiveReason[]);
  readonly failed = signal(false);
  readonly busy = signal(false);
  readonly confirmed: Confirmed[] = [];
  retried = 0;
}

describe('VoidDialog', () => {
  const inDialog = <T extends HTMLElement>(testId: string) =>
    document.body.querySelector<T>(`[role="dialog"] [data-testid="${testId}"]`);

  async function open() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    return fixture;
  }

  const dialog = () => document.body.querySelector('[role="dialog"]') as HTMLElement;
  const choose = async (fixture: ComponentFixture<Host>, label: string) =>
    chooseOption(fixture, dialog(), 'void-dialog-reason', label);

  it('shows the header and keeps the confirm button off until a reason is chosen', async () => {
    const fixture = await open();

    expect(dialog().textContent).toContain('Anular pago');
    expect(inDialog<HTMLButtonElement>('vd-confirm')!.disabled).toBe(true);

    await choose(fixture, 'Error');

    expect(inDialog<HTMLButtonElement>('vd-confirm')!.disabled).toBe(false);
  });

  it('confirms with the reason and no note', async () => {
    const fixture = await open();
    await choose(fixture, 'Error');

    inDialog<HTMLButtonElement>('vd-confirm')!.click();
    await fixture.whenStable();

    expect(fixture.componentInstance.confirmed).toEqual([{ reason: 'Error', note: null }]);
  });

  it('needs a note for the reason Other and sends it trimmed', async () => {
    const fixture = await open();
    await choose(fixture, 'Otro');
    expect(inDialog<HTMLButtonElement>('vd-confirm')!.disabled).toBe(true);

    const note = inDialog<HTMLTextAreaElement>('vd-note')!;
    expect(note.getAttribute('maxlength')).toBe('280');
    note.value = '  Cliente se fue ';
    note.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    inDialog<HTMLButtonElement>('vd-confirm')!.click();
    await fixture.whenStable();

    expect(fixture.componentInstance.confirmed).toEqual([{ reason: 'Other', note: 'Cliente se fue' }]);
  });

  it('keeps the confirm button off while busy', async () => {
    const fixture = await open();
    await choose(fixture, 'Error');
    fixture.componentInstance.busy.set(true);
    await fixture.whenStable();

    expect(inDialog<HTMLButtonElement>('vd-confirm')!.disabled).toBe(true);
  });

  it('says there are no reasons and offers none to pick', async () => {
    const fixture = await open();
    fixture.componentInstance.reasons.set([]);
    await fixture.whenStable();

    expect(inDialog('vd-no-reasons')).not.toBeNull();
  });

  it('offers a retry when the reasons could not be loaded', async () => {
    const fixture = await open();
    fixture.componentInstance.failed.set(true);
    await fixture.whenStable();

    expect(inDialog('vd-reason-box')).toBeNull();
    inDialog<HTMLButtonElement>('vd-retry')!.click();

    expect(fixture.componentInstance.retried).toBe(1);
  });

  it('asks to be dismissed on cancel', async () => {
    const fixture = await open();

    inDialog<HTMLButtonElement>('vd-cancel')!.click();
    await fixture.whenStable();

    expect(fixture.componentInstance.visible()).toBe(false);
  });
});
