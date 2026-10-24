import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormDialog } from './form-dialog';

@Component({
  imports: [FormDialog],
  template: `
    <app-form-dialog
      header="Nueva zona"
      [visible]="visible()"
      [busy]="busy()"
      [saveDisabled]="saveDisabled()"
      [saveLabel]="saveLabel()"
      [saveTooltip]="saveTooltip()"
      [saveSeverity]="severity()"
      [cancelLabel]="cancelLabel()"
      [disabledReason]="reason()"
      (saved)="saves = saves + 1"
      (dismissed)="dismissals = dismissals + 1; visible.set(false)"
    >
      <input id="zone-name" />
    </app-form-dialog>
  `,
})
class Host {
  readonly visible = signal(true);
  readonly busy = signal(false);
  readonly saveDisabled = signal(false);
  readonly saveLabel = signal<string | null>(null);
  readonly saveTooltip = signal<string | null>(null);
  readonly severity = signal<'primary' | 'danger'>('primary');
  readonly cancelLabel = signal<string | null>(null);
  readonly reason = signal<string | null>(null);
  saves = 0;
  dismissals = 0;
}

describe('FormDialog', () => {
  const dialog = () => document.body.querySelector<HTMLElement>('[role="dialog"]');
  const byId = <T extends HTMLElement>(id: string) => dialog()!.querySelector<T>(`[data-testid="${id}"]`)!;

  async function open() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    return fixture;
  }

  it('shows the header and the projected form fields', async () => {
    await open();

    expect(dialog()!.textContent).toContain('Nueva zona');
    expect(dialog()!.querySelector('form #zone-name')).not.toBeNull();
  });

  it('emits saved from the Save button and from submitting the form, and dismissed from Cancel', async () => {
    const fixture = await open();

    byId<HTMLButtonElement>('fd-save').click();
    dialog()!.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    byId<HTMLButtonElement>('fd-cancel').click();
    await fixture.whenStable();

    expect(fixture.componentInstance.saves).toBe(2);
    expect(fixture.componentInstance.dismissals).toBe(1);
  });

  it('does not save while the save is disabled', async () => {
    const fixture = await open();
    fixture.componentInstance.saveDisabled.set(true);
    await fixture.whenStable();

    expect(byId<HTMLButtonElement>('fd-save').disabled).toBe(true);
  });

  it('while busy it is aria-busy, blocks every way to close and ignores repeated saves', async () => {
    const fixture = await open();
    fixture.componentInstance.busy.set(true);
    await fixture.whenStable();

    expect(dialog()!.querySelector('form')!.getAttribute('aria-busy')).toBe('true');
    expect(byId<HTMLButtonElement>('fd-cancel').disabled).toBe(true);
    expect(dialog()!.querySelector('.p-dialog-close-button')).toBeNull();

    dialog()!
      .querySelector('form')!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    dialog()!.querySelector('form')!.dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();

    expect(fixture.componentInstance.dismissals).toBe(0);
    expect(fixture.componentInstance.saves).toBe(0);
    expect(dialog()).not.toBeNull();
  });

  it('closes on Escape from inside the form when idle', async () => {
    const fixture = await open();

    dialog()!
      .querySelector('form')!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await fixture.whenStable();

    expect(fixture.componentInstance.dismissals).toBe(1);
  });

  it('is not aria-busy when idle', async () => {
    await open();

    expect(dialog()!.querySelector('form')!.getAttribute('aria-busy')).toBe('false');
  });

  it('uses the labels the owner gives to the buttons and keeps the defaults otherwise', async () => {
    const fixture = await open();
    expect(byId('fd-save').textContent).toContain('Guardar');
    expect(byId('fd-cancel').textContent).toContain('Cancelar');

    fixture.componentInstance.saveLabel.set('Anular línea');
    fixture.componentInstance.cancelLabel.set('Volver');
    await fixture.whenStable();

    expect(byId('fd-save').textContent).toContain('Anular línea');
    expect(byId('fd-cancel').textContent).toContain('Volver');
  });

  it('marks a destructive save with the danger severity', async () => {
    const fixture = await open();
    expect(byId('fd-save').classList.contains('p-button-danger')).toBe(false);

    fixture.componentInstance.severity.set('danger');
    await fixture.whenStable();

    expect(byId('fd-save').classList.contains('p-button-danger')).toBe(true);
  });

  it('says why the save is disabled and ties the text to the button', async () => {
    const fixture = await open();
    fixture.componentInstance.reason.set('Elige un motivo para continuar.');
    await fixture.whenStable();
    expect(dialog()!.querySelector('[data-testid="fd-reason"]')).toBeNull();

    fixture.componentInstance.saveDisabled.set(true);
    await fixture.whenStable();

    const why = dialog()!.querySelector<HTMLElement>('[data-testid="fd-reason"]')!;
    expect(why.textContent).toContain('Elige un motivo');
    expect(byId('fd-save').getAttribute('aria-describedby')).toBe(why.id);
  });
});
