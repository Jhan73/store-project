import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { forkJoin, map, of } from 'rxjs';
import type {
  Allergen,
  ModifierGroup,
  ModifierOption,
  SaveModifierGroupRequest,
} from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { parseAmount } from '../../../core/money/money';
import { amountValidator } from './amount-validator';
import { AllergenPicker } from './allergen-picker';
import { CatalogApi } from './catalog-api';
import { isStale, reportFailure } from './catalog-errors';
import { modifierGroupViolations } from './modifier-group-rules';

function optionForm(option?: ModifierOption) {
  return new FormGroup({
    id: new FormControl<string | null>(option?.id ?? null),
    name: new FormControl(option?.name ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/)],
    }),
    price: new FormControl(option?.priceDelta.amount ?? '0.00', {
      nonNullable: true,
      validators: [Validators.required, amountValidator],
    }),
    allergens: new FormControl<readonly Allergen[]>(option?.allergens ?? [], { nonNullable: true }),
    available: new FormControl(option?.available ?? true, { nonNullable: true }),
  });
}

type OptionForm = ReturnType<typeof optionForm>;

@Component({
  selector: 'app-modifier-group-form',
  imports: [ReactiveFormsModule, RouterLink, ButtonDirective, InputText, AllergenPicker],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './catalog.scss',
  template: `
    <div class="toolbar">
      <h2>
        @if (groupId) {
          <ng-container i18n="@@admin.catalog.groupForm.editTitle">Editar grupo de modificadores</ng-container>
        } @else {
          <ng-container i18n="@@admin.catalog.groupForm.newTitle">Nuevo grupo de modificadores</ng-container>
        }
      </h2>
      <a pButton severity="secondary" routerLink="/admin/catalog/modifier-groups" i18n="@@admin.catalog.back"
        >Volver a la lista</a
      >
    </div>

    @if (ready()) {
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="field wide">
          <label for="group-name" i18n="@@admin.catalog.groupForm.name">Nombre del grupo</label>
          <input
            pInputText
            id="group-name"
            formControlName="name"
            autocomplete="off"
            [attr.aria-invalid]="invalid(form.controls.name) ? 'true' : null"
            fluid
          />
          @if (invalid(form.controls.name)) {
            <small class="error" i18n="@@admin.catalog.groupForm.nameInvalid">Escribe un nombre válido.</small>
          }
        </div>

        <div class="field">
          <span class="check">
            <input type="checkbox" id="group-required" formControlName="required" />
            <label for="group-required" i18n="@@admin.catalog.groupForm.required">Elección obligatoria</label>
          </span>
        </div>
        <div class="field">
          <label for="group-min" i18n="@@admin.catalog.groupForm.min">Mínimo de opciones</label>
          <input pInputText id="group-min" type="number" min="0" formControlName="minChoices" />
        </div>
        <div class="field">
          <label for="group-max" i18n="@@admin.catalog.groupForm.max">Máximo de opciones</label>
          <input pInputText id="group-max" type="number" min="1" formControlName="maxChoices" />
        </div>

        @if (attempted() && violations().length > 0) {
          <ul class="error" role="alert" data-testid="violations">
            @for (violation of violations(); track violation) {
              <li>
                @switch (violation) {
                  @case ('no-options') {
                    <ng-container i18n="@@admin.catalog.groupForm.violation.noOptions">Agrega al menos una opción.</ng-container>
                  }
                  @case ('max-too-low') {
                    <ng-container i18n="@@admin.catalog.groupForm.violation.maxTooLow"
                      >El máximo debe ser al menos 1 y no menor que el mínimo.</ng-container
                    >
                  }
                  @case ('min-required') {
                    <ng-container i18n="@@admin.catalog.groupForm.violation.minRequired"
                      >Un grupo obligatorio necesita un mínimo de al menos 1.</ng-container
                    >
                  }
                  @case ('min-optional') {
                    <ng-container i18n="@@admin.catalog.groupForm.violation.minOptional"
                      >Un grupo opcional debe tener un mínimo de 0.</ng-container
                    >
                  }
                  @case ('min-exceeds-options') {
                    <ng-container i18n="@@admin.catalog.groupForm.violation.minExceedsOptions"
                      >Agrega más opciones o baja el mínimo de elecciones.</ng-container
                    >
                  }
                  @case ('duplicate-option-names') {
                    <ng-container i18n="@@admin.catalog.groupForm.violation.duplicateNames"
                      >Los nombres de las opciones no pueden repetirse.</ng-container
                    >
                  }
                }
              </li>
            }
          </ul>
        }

        <div formArrayName="options" class="options">
          @for (option of options.controls; track option; let i = $index) {
            <fieldset [formGroupName]="i" [attr.data-testid]="'option-' + i">
              <legend i18n="@@admin.catalog.groupForm.optionLegend">Opción {{ i + 1 }}</legend>
              <div class="field wide">
                <label [for]="'option-' + i + '-name'" i18n="@@admin.catalog.groupForm.optionName">Nombre de la opción</label>
                <input
                  pInputText
                  [id]="'option-' + i + '-name'"
                  formControlName="name"
                  autocomplete="off"
                  [attr.aria-invalid]="invalid(option.controls.name) ? 'true' : null"
                  fluid
                />
              </div>
              <div class="field">
                <label [for]="'option-' + i + '-price'" i18n="@@admin.catalog.groupForm.optionPrice">Precio adicional ({{ currency() }})</label>
                <input
                  pInputText
                  [id]="'option-' + i + '-price'"
                  formControlName="price"
                  [attr.inputmode]="'decimal'"
                  autocomplete="off"
                  [attr.aria-invalid]="invalid(option.controls.price) ? 'true' : null"
                />
                @if (invalid(option.controls.price)) {
                  <small class="error" i18n="@@admin.catalog.groupForm.optionPriceInvalid">Escribe un monto como 1.50.</small>
                }
              </div>
              @if (option.controls.id.value) {
                <span class="check">
                  <input
                    type="checkbox"
                    [id]="'option-' + i + '-available'"
                    [checked]="option.controls.available.value"
                    (change)="setAvailability(option, $event)"
                  />
                  <label [for]="'option-' + i + '-available'" i18n="@@admin.catalog.groupForm.optionAvailable">Disponible</label>
                </span>
              }
              <app-allergen-picker
                [allergens]="allergens()"
                [selected]="option.controls.allergens.value"
                (selectedChange)="option.controls.allergens.setValue($event)"
                i18n="@@admin.catalog.groupForm.optionAllergens"
                >Alérgenos de esta opción</app-allergen-picker
              >
              <button
                pButton
                type="button"
                severity="secondary"
                [outlined]="true"
                [size]="'small'"
                [attr.data-testid]="'remove-option-' + i"
                (click)="removeOption(i)"
                i18n="@@admin.catalog.groupForm.removeOption"
              >
                Quitar opción
              </button>
            </fieldset>
          }
        </div>

        <div class="actions">
          <button
            pButton
            type="button"
            severity="secondary"
            data-testid="add-option"
            (click)="addOption()"
            i18n="@@admin.catalog.groupForm.addOption"
          >
            Agregar opción
          </button>
          <button pButton type="submit" [loading]="saving()" i18n="@@admin.catalog.groupForm.save">
            Guardar grupo
          </button>
        </div>
      </form>
    }
  `,
})
export class ModifierGroupForm {
  private readonly api = inject(CatalogApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly messages = inject(MessageService);
  private readonly router = inject(Router);

  protected readonly groupId = inject(ActivatedRoute).snapshot.paramMap.get('id');
  protected readonly ready = signal(false);
  protected readonly saving = signal(false);
  protected readonly attempted = signal(false);
  protected readonly currency = signal('');
  protected readonly allergens = signal<readonly Allergen[]>([]);

  private etag = '';
  protected readonly options = new FormArray<OptionForm>([optionForm()]);
  protected readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/)],
    }),
    required: new FormControl(false, { nonNullable: true }),
    minChoices: new FormControl<number | null>(0),
    maxChoices: new FormControl<number | null>(1),
    options: this.options,
  });
  protected readonly violations = toSignal(
    this.form.valueChanges.pipe(
      map(() =>
        modifierGroupViolations({
          required: this.form.controls.required.value,
          minChoices: this.form.controls.minChoices.value ?? 0,
          maxChoices: this.form.controls.maxChoices.value ?? 0,
          optionNames: this.options.controls.map((option) => option.controls.name.value),
        }),
      ),
    ),
    { initialValue: [] },
  );

  constructor() {
    this.form.controls.required.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((required) => {
        const min = this.form.controls.minChoices;
        min.setValue(required ? Math.max(1, min.value ?? 0) : 0);
      });
    forkJoin({
      currency: this.api.currency(),
      allergens: this.api.allergens(),
      group: this.groupId ? this.api.modifierGroup(this.groupId) : of(null),
    }).subscribe({
      next: ({ currency, allergens, group }) => {
        this.currency.set(currency);
        this.allergens.set(allergens);
        if (group) {
          this.apply(group);
        }
        this.ready.set(true);
      },
      error: (error: unknown) => this.notifier.show(error),
    });
  }

  protected invalid(control: { invalid: boolean; touched: boolean }): boolean {
    return control.invalid && control.touched;
  }

  protected addOption(): void {
    this.options.push(optionForm());
  }

  protected removeOption(index: number): void {
    this.options.removeAt(index);
  }

  protected setAvailability(option: OptionForm, event: Event): void {
    const id = option.controls.id.value;
    const available = (event.target as HTMLInputElement).checked;
    if (!id) {
      return;
    }
    this.api.setOptionAvailability(id, available).subscribe({
      next: (result) => option.controls.available.setValue(result.available),
      error: (error: unknown) => {
        // The checkbox already flipped on screen; put it back to what the server still holds.
        (event.target as HTMLInputElement).checked = option.controls.available.value;
        this.notifier.show(error);
      },
    });
  }

  protected submit(): void {
    if (this.saving()) {
      return;
    }
    this.attempted.set(true);
    this.form.markAllAsTouched();
    if (this.form.invalid || this.violations().length > 0) {
      return;
    }
    const body = this.requestBody();
    const request = this.groupId
      ? this.api.changeModifierGroup(this.groupId, this.etag, body)
      : this.api.createModifierGroup(body);
    this.saving.set(true);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.messages.add({
          severity: 'success',
          summary: $localize`:@@admin.catalog.saved:Cambios guardados.`,
        });
        void this.router.navigate(['/admin/catalog/modifier-groups']);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        reportFailure(this.notifier, error, this.form);
        if (this.groupId && isStale(error)) {
          this.api.modifierGroup(this.groupId).subscribe({
            next: (group) => this.apply(group),
            error: (failure: unknown) => this.notifier.show(failure),
          });
        }
      },
    });
  }

  private requestBody(): SaveModifierGroupRequest {
    const value = this.form.getRawValue();
    return {
      name: value.name.trim(),
      required: value.required,
      minChoices: value.minChoices ?? 0,
      maxChoices: value.maxChoices ?? 0,
      options: value.options.map((option) => ({
        ...(option.id && { id: option.id }),
        name: option.name.trim(),
        priceDelta: { amount: parseAmount(option.price) ?? '0.00', currency: this.currency() },
        allergens: [...option.allergens],
      })),
    };
  }

  private apply(group: ModifierGroup): void {
    this.etag = group.etag;
    this.options.clear({ emitEvent: false });
    group.options.forEach((option) => this.options.push(optionForm(option), { emitEvent: false }));
    this.form.controls.required.setValue(group.required, { emitEvent: false });
    this.form.patchValue({
      name: group.name,
      minChoices: group.minChoices,
      maxChoices: group.maxChoices,
    });
  }
}
