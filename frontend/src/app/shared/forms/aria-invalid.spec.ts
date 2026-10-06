import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { InputNumber } from 'primeng/inputnumber';
import { Password } from 'primeng/password';
import { Select } from 'primeng/select';
import { numberAriaInvalid, passwordAriaInvalid, selectAriaInvalid } from './aria-invalid';

@Component({
  selector: 'app-aria-invalid-host',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Select, InputNumber, Password],
  template: `
    <p-select inputId="sel" [options]="options" [invalid]="invalid()" [pt]="selectPt()" />
    <p-inputnumber inputId="num" [invalid]="invalid()" [pt]="numberPt()" />
    <p-password inputId="pwd" [feedback]="false" [invalid]="invalid()" [pt]="passwordPt()" />
  `,
})
class Host {
  protected readonly options = [{ label: 'A', value: 'a' }];
  readonly invalid = signal(false);
  protected selectPt() {
    return selectAriaInvalid(this.invalid());
  }
  protected numberPt() {
    return numberAriaInvalid(this.invalid());
  }
  protected passwordPt() {
    return passwordAriaInvalid(this.invalid());
  }
}

async function render() {
  const fixture = TestBed.createComponent(Host);
  await fixture.whenStable();
  const host = fixture.nativeElement as HTMLElement;
  return {
    fixture,
    combobox: () => host.querySelector('p-select [role="combobox"]')!,
    number: () => host.querySelector('p-inputnumber #num')!,
    password: () => host.querySelector('p-password #pwd')!,
  };
}

describe('aria-invalid pass-through', () => {
  it('does not announce the focusable elements as invalid while valid', async () => {
    const { combobox, number, password } = await render();

    expect(combobox().getAttribute('aria-invalid')).not.toBe('true');
    expect(number().getAttribute('aria-invalid')).not.toBe('true');
    expect(password().getAttribute('aria-invalid')).not.toBe('true');
  });

  it('puts aria-invalid="true" on the combobox, the number input and the password input', async () => {
    const { fixture, combobox, number, password } = await render();

    fixture.componentInstance.invalid.set(true);
    await fixture.whenStable();

    expect(combobox().getAttribute('aria-invalid')).toBe('true');
    expect(number().getAttribute('aria-invalid')).toBe('true');
    expect(password().getAttribute('aria-invalid')).toBe('true');
  });

  it('stops announcing the elements as invalid once the control becomes valid', async () => {
    const { fixture, combobox, number, password } = await render();
    fixture.componentInstance.invalid.set(true);
    await fixture.whenStable();

    fixture.componentInstance.invalid.set(false);
    await fixture.whenStable();

    expect(combobox().getAttribute('aria-invalid')).not.toBe('true');
    expect(number().getAttribute('aria-invalid')).not.toBe('true');
    expect(password().getAttribute('aria-invalid')).not.toBe('true');
  });

  it('returns a stable reference for each state', () => {
    expect(selectAriaInvalid(true)).toBe(selectAriaInvalid(true));
    expect(numberAriaInvalid(false)).toBe(numberAriaInvalid(false));
  });
});
