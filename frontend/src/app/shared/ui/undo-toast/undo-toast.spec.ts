import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { UndoNotice } from '../../state/undo-notice';
import { UndoQueue } from '../../state/undo-queue';
import { UndoToast } from './undo-toast';

@Component({ imports: [UndoToast], template: '<app-undo-toast />' })
class Host {}

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

describe('UndoToast', () => {
  async function render() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    return { fixture, notice: TestBed.inject(UndoNotice), root: fixture.nativeElement as HTMLElement };
  }

  it('shows each pending notice with an Undo button that cancels the command', async () => {
    const { fixture, notice, root } = await render();
    const queue = new UndoQueue(30);
    const send = vi.fn();

    notice.schedule(queue, 'line-1', 'Línea quitada', send);
    await fixture.whenStable();
    expect(root.textContent).toContain('Línea quitada');

    root.querySelector<HTMLButtonElement>('[data-testid="undo-action"]')!.click();
    await fixture.whenStable();
    await wait(80);

    expect(send).not.toHaveBeenCalled();
    expect(root.querySelector('[data-testid="undo-action"]')).toBeNull();
  });

  it('removes the notice when the command has been sent', async () => {
    const { fixture, notice, root } = await render();
    const queue = new UndoQueue(30);
    const send = vi.fn();

    notice.schedule(queue, 'line-1', 'Línea quitada', send);
    await wait(80);
    await fixture.whenStable();

    expect(send).toHaveBeenCalledTimes(1);
    expect(root.querySelector('[data-testid="undo-action"]')).toBeNull();
  });
});
