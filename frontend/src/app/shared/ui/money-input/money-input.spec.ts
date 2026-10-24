import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MoneyInput } from './money-input';

@Component({
  imports: [ReactiveFormsModule, MoneyInput],
  template: `
    <app-money-input inputId="price" currency="PEN" [formControl]="price" />
    <app-money-input inputId="tip" [optional]="true" [formControl]="tip" />
  `,
})
class Host {
  readonly price = new FormControl('', { nonNullable: true });
  readonly tip = new FormControl('', { nonNullable: true });
}

describe('MoneyInput', () => {
  async function render() {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;
    return { fixture, root, input: (id: string) => root.querySelector<HTMLInputElement>(`#${id}`)! };
  }

  it('is a decimal text input with the currency as a suffix', async () => {
    const { root, input } = await render();

    expect(input('price').getAttribute('inputmode')).toBe('decimal');
    expect(input('price').type).toBe('text');
    expect(root.querySelector('.suffix')?.textContent).toBe('PEN');
    expect(root.querySelectorAll('.suffix')).toHaveLength(1);
  });

  it('writes what the person types to the form control as text', async () => {
    const { fixture, input } = await render();

    input('price').value = '9,5';
    input('price').dispatchEvent(new Event('input'));

    expect(fixture.componentInstance.price.value).toBe('9,5');
  });

  it('shows the value set from the form', async () => {
    const { fixture, input } = await render();

    fixture.componentInstance.price.setValue('12.50');
    await fixture.whenStable();

    expect(input('price').value).toBe('12.50');
  });

  it('validates with the amount rules, required unless optional', async () => {
    const { fixture, input } = await render();
    const { price, tip } = fixture.componentInstance;

    expect(price.errors).toEqual({ amount: true });
    expect(tip.errors).toBeNull();

    input('price').value = '9.5';
    input('price').dispatchEvent(new Event('input'));
    input('tip').value = 'abc';
    input('tip').dispatchEvent(new Event('input'));

    expect(price.errors).toBeNull();
    expect(tip.errors).toEqual({ amount: true });
  });

  it('marks the input invalid for assistive technology once the control is touched and invalid', async () => {
    const { fixture, input } = await render();
    expect(input('price').getAttribute('aria-invalid')).toBe('false');

    input('price').dispatchEvent(new Event('blur'));
    await fixture.whenStable();

    expect(fixture.componentInstance.price.touched).toBe(true);
    expect(input('price').getAttribute('aria-invalid')).toBe('true');
  });

  it('can be disabled from the form', async () => {
    const { fixture, input } = await render();

    fixture.componentInstance.price.disable();
    await fixture.whenStable();

    expect(input('price').disabled).toBe(true);
  });
});
