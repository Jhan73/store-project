import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { FormControl, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  IconCheck,
  IconMinus,
  IconPlus,
  TablerIconComponent,
  IconX,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Checkbox } from 'primeng/checkbox';
import { RadioButton } from 'primeng/radiobutton';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import type { LineRequest, MenuModifierGroup, MenuProduct } from '../../../core/api/api-types';
import { formatMoney } from '../../../core/money/money';
import { minimumOf, toggleOption, unitPriceOf } from './configurator-rules';

const MAX_QUANTITY = 99;
const NO_CHOICE = '';
let nextId = 0;

// Presentational: it knows the modifier-group rules of one product and nothing about tickets or carts.
@Component({
  selector: 'app-product-configurator',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    NgTemplateOutlet,
    ButtonDirective,
    Checkbox,
    RadioButton,
    Textarea,
    TablerIconComponent,
    Tooltip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './product-configurator.scss',
  template: `
    <form class="configurator" (submit)="submit($event)">
      <header>
        <h2>{{ product().name }}</h2>
        @if (product().description) {
          <p class="muted">{{ product().description }}</p>
        }
      </header>

      @for (group of product().modifierGroups; track group.id) {
        <fieldset [attr.data-testid]="'group-' + group.id">
          <legend>{{ group.name }}</legend>
          <p class="rule" [class.pending]="!satisfied(group)">{{ ruleOf(group) }}</p>
          @if (group.maxChoices === 1) {
            @if (!group.required) {
              <label class="choice" [for]="prefix + group.id + '-none'">
                <p-radiobutton
                  [inputId]="prefix + group.id + '-none'"
                  [name]="prefix + group.id"
                  [value]="noChoice"
                  [ngModel]="chosenIn(group)"
                  [ngModelOptions]="{ standalone: true }"
                  [attr.data-testid]="'option-none-' + group.id"
                  (ngModelChange)="choose(group, $event)"
                />
                <span class="name" i18n="@@configurator.noChoice">Ninguna</span>
              </label>
            }
            @for (option of group.options; track option.id) {
              <label class="choice" [for]="prefix + option.id">
                <p-radiobutton
                  [inputId]="prefix + option.id"
                  [name]="prefix + group.id"
                  [value]="option.id"
                  [disabled]="!option.available"
                  [ngModel]="chosenIn(group)"
                  [ngModelOptions]="{ standalone: true }"
                  [attr.data-testid]="'option-' + option.id"
                  (ngModelChange)="choose(group, $event)"
                />
                <span class="name">{{ option.name }}</span>
                <ng-container [ngTemplateOutlet]="detail" [ngTemplateOutletContext]="{ $implicit: option }" />
              </label>
            }
          } @else {
            @for (option of group.options; track option.id) {
              <label class="choice" [for]="prefix + option.id">
                <p-checkbox
                  [inputId]="prefix + option.id"
                  [binary]="true"
                  [disabled]="!option.available || (atMaximum(group) && !isChosen(option.id))"
                  [ngModel]="isChosen(option.id)"
                  [ngModelOptions]="{ standalone: true }"
                  [attr.data-testid]="'option-' + option.id"
                  (ngModelChange)="toggle(group, option.id)"
                />
                <span class="name">{{ option.name }}</span>
                <ng-container [ngTemplateOutlet]="detail" [ngTemplateOutletContext]="{ $implicit: option }" />
              </label>
            }
          }
        </fieldset>
      }

      <div class="field">
        <label [for]="prefix + 'note'" i18n="@@configurator.note">Nota (opcional)</label>
        <textarea
          pTextarea
          [id]="prefix + 'note'"
          [formControl]="note"
          maxlength="140"
          rows="2"
          fluid
        ></textarea>
      </div>

      <label class="choice" [for]="prefix + 'allergy'">
        <p-checkbox
          [inputId]="prefix + 'allergy'"
          [binary]="true"
          [formControl]="allergy"
          data-testid="allergy"
        />
        <span class="name" i18n="@@configurator.allergy">Alergia: avisar a cocina</span>
      </label>

      <div class="quantity" role="group" [attr.aria-labelledby]="prefix + 'quantity-label'">
        <span [id]="prefix + 'quantity-label'" i18n="@@configurator.quantity">Cantidad</span>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="quantity-down"
          [disabled]="quantity() <= 1"
          aria-label="Una unidad menos"
          i18n-aria-label="@@configurator.quantity.downLabel"
          pTooltip="Una unidad menos"
          i18n-pTooltip="@@configurator.quantity.down"
          tooltipPosition="top"
          (click)="step(-1)"
        >
          <tabler-icon [icon]="icons.minus" aria-hidden="true" />
        </button>
        <output class="count" data-testid="quantity">{{ quantity() }}</output>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="quantity-up"
          [disabled]="quantity() >= maxQuantity"
          aria-label="Una unidad más"
          i18n-aria-label="@@configurator.quantity.upLabel"
          pTooltip="Una unidad más"
          i18n-pTooltip="@@configurator.quantity.up"
          tooltipPosition="top"
          (click)="step(1)"
        >
          <tabler-icon [icon]="icons.plus" aria-hidden="true" />
        </button>
      </div>

      <p class="price">
        <span i18n="@@configurator.unitPrice">Precio por unidad</span>
        <strong data-testid="unit-price">{{ unitPrice() }}</strong>
      </p>

      <div class="actions">
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="cancel"
          pTooltip="Cerrar sin agregar el producto"
          i18n-pTooltip="@@configurator.cancel.tooltip"
          tooltipPosition="top"
          (click)="cancelled.emit()"
        >
          <tabler-icon [icon]="icons.cancel" aria-hidden="true" />
          <span i18n="@@configurator.cancel">Cancelar</span>
        </button>
        <button
          pButton
          type="submit"
          data-testid="submit"
          [loading]="busy()"
          [disabled]="!valid() || busy()"
          [pTooltip]="submitTooltip()"
          tooltipPosition="top"
        >
          @if (!busy()) {
            <tabler-icon [icon]="icons.confirm" aria-hidden="true" />
          }
          <span>{{ submitLabel() }}</span>
        </button>
      </div>
    </form>

    <ng-template #detail let-option>
      @if (!option.available) {
        <span class="sold-out" i18n="@@configurator.soldOut">Agotado</span>
      } @else if (hasDelta(option.priceDelta)) {
        <span class="delta">+{{ money(option.priceDelta) }}</span>
      }
    </ng-template>
  `,
})
export class ProductConfigurator {
  readonly product = input.required<MenuProduct>();
  readonly busy = input(false);
  readonly submitLabel = input($localize`:@@configurator.submit:Agregar`);
  readonly submitTooltip = input($localize`:@@configurator.submit.tooltip:Agregar el producto con estas opciones`);

  readonly confirmed = output<LineRequest>();
  readonly cancelled = output<void>();

  protected readonly icons = { minus: IconMinus, plus: IconPlus, confirm: IconCheck, cancel: IconX };
  protected readonly prefix = `configurator-${nextId++}-`;
  protected readonly noChoice = NO_CHOICE;
  protected readonly maxQuantity = MAX_QUANTITY;
  protected readonly note = new FormControl('', {
    nonNullable: true,
    validators: [Validators.maxLength(140)],
  });
  protected readonly allergy = new FormControl(false, { nonNullable: true });
  protected readonly quantity = signal(1);

  private readonly selection = signal<readonly string[]>([]);

  protected readonly unitPrice = computed(() => formatMoney(unitPriceOf(this.product(), this.selection())));
  protected readonly valid = computed(() =>
    this.product().modifierGroups.every((group) => this.satisfied(group)),
  );

  protected money = formatMoney;

  protected hasDelta(delta: { amount: string }): boolean {
    return delta.amount !== '0.00';
  }

  protected isChosen(optionId: string): boolean {
    return this.selection().includes(optionId);
  }

  protected chosenIn(group: MenuModifierGroup): string {
    return group.options.find((option) => this.isChosen(option.id))?.id ?? NO_CHOICE;
  }

  protected atMaximum(group: MenuModifierGroup): boolean {
    return group.options.filter((option) => this.isChosen(option.id)).length >= group.maxChoices;
  }

  protected satisfied(group: MenuModifierGroup): boolean {
    const count = group.options.filter((option) => this.isChosen(option.id)).length;
    return count >= minimumOf(group) && count <= group.maxChoices;
  }

  protected ruleOf(group: MenuModifierGroup): string {
    const min = minimumOf(group);
    const max = group.maxChoices;
    if (max === 1) {
      return group.required
        ? $localize`:@@configurator.rule.one:Elige una opción`
        : $localize`:@@configurator.rule.optionalOne:Opcional: elige una opción`;
    }
    if (min === 0) {
      return $localize`:@@configurator.rule.upTo:Opcional: hasta ${max}:max: opciones`;
    }
    return min === max
      ? $localize`:@@configurator.rule.exactly:Elige ${max}:max: opciones`
      : $localize`:@@configurator.rule.between:Elige entre ${min}:min: y ${max}:max: opciones`;
  }

  protected choose(group: MenuModifierGroup, optionId: string): void {
    const others = this.selection().filter((id) => !group.options.some((option) => option.id === id));
    this.selection.set(optionId === NO_CHOICE ? others : toggleOption(group, others, optionId));
  }

  protected toggle(group: MenuModifierGroup, optionId: string): void {
    this.selection.update((current) => toggleOption(group, current, optionId));
  }

  protected step(delta: number): void {
    this.quantity.update((current) => Math.min(MAX_QUANTITY, Math.max(1, current + delta)));
  }

  protected submit(event: Event): void {
    event.preventDefault();
    if (!this.valid() || this.busy()) {
      return;
    }
    const note = this.note.value.trim();
    this.confirmed.emit({
      productId: this.product().id,
      quantity: this.quantity(),
      modifierOptionIds: [...this.selection()],
      note: note === '' ? null : note,
      allergy: this.allergy.value,
    });
  }
}
