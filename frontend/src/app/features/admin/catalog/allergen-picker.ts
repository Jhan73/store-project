import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Checkbox } from 'primeng/checkbox';
import type { Allergen } from '../../../core/api/api-types';
import { ALLERGEN_LABELS } from './allergen-labels';

let nextId = 0;

// Presentational: the legend is projected so each screen words it for its own context.
@Component({
  selector: 'app-allergen-picker',
  imports: [FormsModule, Checkbox],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    fieldset {
      display: flex;
      flex-wrap: wrap;
      gap: var(--space-2) var(--space-4);
      margin: 0;
      padding: var(--space-2) var(--space-3);
      border: 1px solid var(--border);
      border-radius: var(--radius);
    }

    label {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
    }
  `,
  template: `
    <fieldset>
      <legend><ng-content /></legend>
      @for (allergen of allergens(); track allergen) {
        <label [for]="prefix + allergen">
          <p-checkbox
            [inputId]="prefix + allergen"
            [value]="allergen"
            [ngModel]="selected()"
            (ngModelChange)="selectedChange.emit($event)"
          />
          {{ labels[allergen] }}
        </label>
      }
    </fieldset>
  `,
})
export class AllergenPicker {
  readonly allergens = input.required<readonly Allergen[]>();
  readonly selected = input.required<readonly Allergen[]>();
  readonly selectedChange = output<readonly Allergen[]>();

  protected readonly labels = ALLERGEN_LABELS;
  protected readonly prefix = `allergen-${nextId++}-`;
}
