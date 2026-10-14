import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type { LineRequest, MenuModifierGroup, MenuProduct } from '../../../core/api/api-types';
import { ProductConfigurator } from './product-configurator';

const pen = (amount: string) => ({ amount, currency: 'PEN' });

function option(id: string, name: string, delta = '0.00', available = true) {
  return { id, name, available, allergens: [], priceDelta: pen(delta) };
}

const size: MenuModifierGroup = {
  id: 'size',
  name: 'Tamaño',
  required: true,
  minChoices: 1,
  maxChoices: 1,
  options: [option('small', 'Chico'), option('large', 'Grande', '2.00')],
};
const sweetener: MenuModifierGroup = {
  id: 'sweetener',
  name: 'Endulzante',
  required: false,
  minChoices: 0,
  maxChoices: 1,
  options: [option('honey', 'Miel'), option('sugar', 'Azúcar', '0.00', false)],
};
const extras: MenuModifierGroup = {
  id: 'extras',
  name: 'Extras',
  required: false,
  minChoices: 0,
  maxChoices: 2,
  options: [option('ginger', 'Jengibre', '1.00'), option('mint', 'Menta'), option('ice', 'Hielo')],
};

const juice: MenuProduct = {
  id: 'p1',
  name: 'Naranja',
  available: true,
  description: null,
  imageUrl: null,
  allergens: [],
  price: pen('10.00'),
  modifierGroups: [size, sweetener, extras],
};

@Component({
  selector: 'app-configurator-harness',
  imports: [ProductConfigurator],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-product-configurator
      [product]="product()"
      [busy]="busy()"
      (confirmed)="confirmed.set($event)"
      (cancelled)="cancelled.set(true)"
    />
  `,
})
class Harness {
  readonly product = signal(juice);
  readonly busy = signal(false);
  readonly confirmed = signal<LineRequest | null>(null);
  readonly cancelled = signal(false);
}

describe('ProductConfigurator', () => {
  async function render(product: MenuProduct = juice) {
    const fixture = TestBed.createComponent(Harness);
    fixture.componentInstance.product.set(product);
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;
    const input = (id: string) => host.querySelector<HTMLInputElement>(`[data-testid="option-${id}"] input`)!;
    const submit = () => host.querySelector<HTMLButtonElement>('[data-testid="submit"]')!;
    const choose = async (id: string) => {
      input(id).click();
      await fixture.whenStable();
    };
    const press = async (testId: string) => {
      host.querySelector<HTMLElement>(`[data-testid="${testId}"]`)!.click();
      await fixture.whenStable();
    };
    return { fixture, host, input, submit, choose, press, harness: fixture.componentInstance };
  }

  it('shows the product, its groups with their rules and the price of each option', async () => {
    const { host } = await render();

    expect(host.querySelector('h2')?.textContent).toContain('Naranja');
    expect(host.querySelector('[data-testid="group-size"] legend')?.textContent).toContain('Tamaño');
    expect(host.querySelector('[data-testid="group-size"]')?.textContent).toContain('Elige una opción');
    expect(host.querySelector('[data-testid="group-extras"]')?.textContent).toContain('hasta 2');
    expect(host.querySelector('[data-testid="option-large"]')?.parentElement?.textContent).toContain('2.00');
  });

  it('keeps the submit button off until every required group is answered', async () => {
    const { submit, choose } = await render();
    expect(submit().disabled).toBe(true);

    await choose('large');

    expect(submit().disabled).toBe(false);
  });

  it('swaps the choice of a single-choice group', async () => {
    const { input, choose } = await render();

    await choose('small');
    await choose('large');

    expect(input('small').checked).toBe(false);
    expect(input('large').checked).toBe(true);
  });

  it('marks an unavailable option as sold out and does not let it be chosen', async () => {
    const { host, input } = await render();

    expect(input('sugar').disabled).toBe(true);
    expect(host.querySelector('[data-testid="group-sweetener"]')?.textContent).toContain('Agotado');
  });

  it('stops offering more options once a multiple-choice group reaches its maximum', async () => {
    const { input, choose } = await render();

    await choose('ginger');
    await choose('mint');
    await choose('ice');

    expect(input('ginger').checked).toBe(true);
    expect(input('mint').checked).toBe(true);
    expect(input('ice').checked).toBe(false);
  });

  it('previews the unit price with the chosen options', async () => {
    const { host, choose } = await render();
    expect(host.querySelector('[data-testid="unit-price"]')?.textContent).toContain('10.00');

    await choose('large');
    await choose('ginger');

    expect(host.querySelector('[data-testid="unit-price"]')?.textContent).toContain('13.00');
  });

  it('emits the line with the chosen options, quantity, note and allergy flag', async () => {
    const { host, harness, choose, press, submit, fixture } = await render();
    await choose('large');
    await choose('honey');
    await press('quantity-up');
    await press('quantity-up');
    await press('quantity-down');
    const note = host.querySelector<HTMLTextAreaElement>('textarea')!;
    note.value = 'Sin hielo';
    note.dispatchEvent(new Event('input'));
    host.querySelector<HTMLInputElement>('[data-testid="allergy"] input')!.click();
    await fixture.whenStable();

    submit().click();
    await fixture.whenStable();

    expect(harness.confirmed()).toEqual({
      productId: 'p1',
      quantity: 2,
      modifierOptionIds: ['large', 'honey'],
      note: 'Sin hielo',
      allergy: true,
    });
  });

  it('sends a null note when it was left blank', async () => {
    const { harness, choose, submit, fixture } = await render();
    await choose('small');

    submit().click();
    await fixture.whenStable();

    expect(harness.confirmed()).toMatchObject({ note: null, quantity: 1, allergy: false });
  });

  it('keeps the quantity between 1 and 99', async () => {
    const { host, press } = await render();

    await press('quantity-down');
    expect(host.querySelector('[data-testid="quantity"]')?.textContent?.trim()).toBe('1');

    for (let index = 0; index < 100; index++) {
      await press('quantity-up');
    }
    expect(host.querySelector('[data-testid="quantity"]')?.textContent?.trim()).toBe('99');
  });

  it('limits the note to 140 characters', async () => {
    const { host } = await render();

    expect(host.querySelector('textarea')?.getAttribute('maxlength')).toBe('140');
  });

  it('cannot be submitted while busy', async () => {
    const { harness, choose, submit, fixture } = await render();
    await choose('small');

    harness.busy.set(true);
    await fixture.whenStable();

    expect(submit().disabled).toBe(true);
  });

  it('emits cancelled', async () => {
    const { harness, press } = await render();

    await press('cancel');

    expect(harness.cancelled()).toBe(true);
  });

  it('works for a product without modifier groups', async () => {
    const { harness, submit, fixture } = await render({ ...juice, modifierGroups: [] });
    expect(submit().disabled).toBe(false);

    submit().click();
    await fixture.whenStable();

    expect(harness.confirmed()).toMatchObject({ productId: 'p1', modifierOptionIds: [] });
  });
});
