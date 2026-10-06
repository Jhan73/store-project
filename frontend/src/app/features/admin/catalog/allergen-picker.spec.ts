import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type { Allergen } from '../../../core/api/api-types';
import { AllergenPicker } from './allergen-picker';

@Component({
  selector: 'app-picker-harness',
  imports: [AllergenPicker],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-allergen-picker
      [allergens]="['MILK', 'PEANUTS', 'TREE_NUTS']"
      [selected]="selected()"
      (selectedChange)="selected.set($event)"
      >Alérgenos</app-allergen-picker
    >
  `,
})
class Harness {
  readonly selected = signal<readonly Allergen[]>(['PEANUTS']);
}

describe('AllergenPicker', () => {
  async function render() {
    const fixture = TestBed.createComponent(Harness);
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;
    const boxes = () => Array.from(host.querySelectorAll<HTMLInputElement>('input[type="checkbox"]'));
    return { fixture, host, boxes };
  }

  it('offers one labelled checkbox per allergen under the given legend', async () => {
    const { host, boxes } = await render();

    expect(host.querySelector('legend')?.textContent).toContain('Alérgenos');
    expect(boxes()).toHaveLength(3);
    expect(host.querySelector('label[for="' + boxes()[0].id + '"]')?.textContent).toContain('Leche');
    expect(host.textContent).toContain('Maní');
    expect(host.textContent).toContain('Frutos secos');
  });

  it('checks the selected allergens', async () => {
    const { boxes } = await render();

    expect(boxes().map((box) => box.checked)).toEqual([false, true, false]);
  });

  it('emits the selection with the toggled allergen added or removed', async () => {
    const { fixture, boxes } = await render();

    boxes()[0].click();
    await fixture.whenStable();
    expect(fixture.componentInstance.selected()).toEqual(['PEANUTS', 'MILK']);

    boxes()[1].click();
    await fixture.whenStable();
    expect(fixture.componentInstance.selected()).toEqual(['MILK']);
  });
});
