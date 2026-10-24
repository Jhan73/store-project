import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormField } from './form-field';

@Component({
  imports: [FormField],
  template: `
    <app-form-field label="Nombre" controlId="name" [error]="error()" [hint]="hint()">
      <input id="name" />
    </app-form-field>
  `,
})
class Host {
  readonly error = signal<string | null>(null);
  readonly hint = signal<string | null>(null);
}

describe('FormField', () => {
  async function render() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      label: () => root.querySelector('label')!,
      input: () => root.querySelector('input')!,
      message: () => root.querySelector<HTMLElement>('.message')!,
    };
  }

  it('labels the projected control', async () => {
    const { label } = await render();

    expect(label().textContent?.trim()).toBe('Nombre');
    expect(label().getAttribute('for')).toBe('name');
  });

  it('always renders the message slot so the layout does not shift when an error appears', async () => {
    const { fixture, message } = await render();
    const idle = message();

    expect(idle).not.toBeNull();
    expect(idle.textContent?.trim()).toBe('');

    fixture.componentInstance.error.set('Campo obligatorio');
    await fixture.whenStable();

    expect(message()).toBe(idle);
    expect(message().textContent).toContain('Campo obligatorio');
    expect(message().classList).toContain('error');
  });

  it('describes the control by the message only while there is one', async () => {
    const { fixture, input, message } = await render();
    expect(input().hasAttribute('aria-describedby')).toBe(false);

    fixture.componentInstance.hint.set('Máximo 40 caracteres');
    await fixture.whenStable();
    expect(input().getAttribute('aria-describedby')).toBe(message().id);
    expect(message().classList).not.toContain('error');

    fixture.componentInstance.error.set('Campo obligatorio');
    await fixture.whenStable();
    expect(input().getAttribute('aria-describedby')).toBe(message().id);
    expect(message().textContent).toContain('Campo obligatorio');

    fixture.componentInstance.error.set(null);
    fixture.componentInstance.hint.set(null);
    await fixture.whenStable();
    expect(input().hasAttribute('aria-describedby')).toBe(false);
  });

  it('keeps an aria-describedby the control already had', async () => {
    const fixture = TestBed.createComponent(Host);
    const input = (fixture.nativeElement as HTMLElement).querySelector('input')!;
    input.setAttribute('aria-describedby', 'other');
    fixture.componentInstance.hint.set('Ayuda');
    await fixture.whenStable();

    const ids = input.getAttribute('aria-describedby')!.split(' ');
    expect(ids).toContain('other');
    expect(ids).toHaveLength(2);
  });
});
