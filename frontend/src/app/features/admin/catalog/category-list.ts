import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  IconDeviceFloppy,
  IconPencil,
  IconPlayerPause,
  IconPlayerPlay,
  IconPlus,
  IconX,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { InputNumber } from 'primeng/inputnumber';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Tooltip } from 'primeng/tooltip';
import { numberAriaInvalid } from '../../../shared/forms/aria-invalid';
import type { Category, Station } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { CatalogApi } from './catalog-api';
import { isStale, reportFailure } from '../admin-errors';
import { TableScroll } from '../../../shared/ui/table-scroll/table-scroll';

@Component({
  selector: 'app-category-list',
  imports: [
    TableScroll,
    ReactiveFormsModule,
    ButtonDirective,
    InputNumber,
    InputText,
    Select,
    TablerIconComponent,
    Tooltip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './catalog.scss',
  template: `
    <h2 i18n="@@admin.catalog.categories.title">Categorías</h2>

    <app-table-scroll label="Listado de categorías" i18n-label="@@admin.catalog.categories.tableScroll">
      <table>
      <thead>
        <tr>
          <th i18n="@@admin.catalog.categories.name">Nombre</th>
          <th i18n="@@admin.catalog.categories.station">Estación</th>
          <th i18n="@@admin.catalog.categories.order">Orden</th>
          <th i18n="@@admin.catalog.categories.state">Estado</th>
          <th><span class="sr-only" i18n="@@admin.catalog.actions">Acciones</span></th>
        </tr>
      </thead>
      <tbody>
        @for (category of categories(); track category.id) {
          <tr>
            <td>{{ category.name }}</td>
            <td>{{ stationName(category.stationId) }}</td>
            <td>{{ category.displayOrder }}</td>
            <td>
              @if (category.active) {
                <span i18n="@@admin.catalog.active">Activa</span>
              } @else {
                <span class="muted" i18n="@@admin.catalog.inactive">Inactiva</span>
              }
            </td>
            <td class="actions">
              <button
                pButton
                type="button"
                severity="secondary"
                [size]="'small'"
                [attr.data-testid]="'edit-' + category.id"
                (click)="edit(category)"
                pTooltip="Editar los datos de esta categoría"
                i18n-pTooltip="@@admin.catalog.categories.edit.tooltip"
                tooltipPosition="top"
              >
                <tabler-icon [icon]="icons.edit" aria-hidden="true" />
                <span i18n="@@admin.catalog.edit">Editar</span>
              </button>
              <button
                pButton
                type="button"
                severity="secondary"
                [size]="'small'"
                [outlined]="true"
                [attr.data-testid]="'toggle-' + category.id"
                (click)="toggle(category)"
                [pTooltip]="category.active ? tips.deactivate : tips.reactivate"
                tooltipPosition="top"
              >
                @if (category.active) {
                  <tabler-icon [icon]="icons.deactivate" aria-hidden="true" />
                  <ng-container i18n="@@admin.catalog.deactivate">Desactivar</ng-container>
                } @else {
                  <tabler-icon [icon]="icons.reactivate" aria-hidden="true" />
                  <ng-container i18n="@@admin.catalog.reactivate">Reactivar</ng-container>
                }
              </button>
            </td>
          </tr>
        }
      </tbody>
      </table>
    </app-table-scroll>

    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <div class="field wide">
        <label for="category-name" i18n="@@admin.catalog.categories.nameLabel">Nombre de la categoría</label>
        <input
          pInputText
          id="category-name"
          formControlName="name"
          autocomplete="off"
          [attr.aria-invalid]="invalid('name') ? 'true' : null"
          fluid
        />
        @if (invalid('name')) {
          <small class="error" i18n="@@admin.catalog.categories.nameInvalid">Escribe un nombre válido.</small>
        }
      </div>
      <div class="field">
        <label for="category-order" i18n="@@admin.catalog.categories.orderLabel">Orden de aparición</label>
        <p-inputnumber
          inputId="category-order"
          formControlName="displayOrder"
          [useGrouping]="false"
          [invalid]="invalid('displayOrder')"
          [pt]="numberAriaInvalid(invalid('displayOrder'))"
        />
        @if (invalid('displayOrder')) {
          <small class="error" i18n="@@admin.catalog.categories.orderInvalid">Escribe un número entero.</small>
        }
      </div>
      <div class="field">
        <label
          for="category-station"
          id="category-station-label"
          i18n="@@admin.catalog.categories.stationLabel"
          >Estación de preparación</label
        >
        <p-select
          inputId="category-station"
          [ariaLabelledBy]="'category-station-label'"
          formControlName="stationId"
          placeholder="Estación predeterminada"
          i18n-placeholder="@@admin.catalog.categories.defaultStation"
          optionLabel="name"
          optionValue="id"
          [options]="stationOptions()"
          [filter]="true"
          filterBy="name"
          [resetFilterOnHide]="true"
          filterPlaceholder="Buscar"
          i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
          [showClear]="!editing()"
        />
      </div>
      <div class="actions">
        <button
          pButton
          type="submit"
          [loading]="saving()"
          [pTooltip]="editing() ? tips.save : tips.create"
          tooltipPosition="top"
        >
          @if (editing()) {
            @if (!saving()) {
              <tabler-icon [icon]="icons.save" aria-hidden="true" />
            }
            <ng-container i18n="@@admin.catalog.categories.save">Guardar cambios</ng-container>
          } @else {
            @if (!saving()) {
              <tabler-icon [icon]="icons.create" aria-hidden="true" />
            }
            <ng-container i18n="@@admin.catalog.categories.create">Agregar categoría</ng-container>
          }
        </button>
        @if (editing()) {
          <button
            pButton
            type="button"
            severity="secondary"
            (click)="cancel()"
            pTooltip="Descartar los cambios y cerrar el formulario"
            i18n-pTooltip="@@admin.catalog.cancel.tooltip"
            tooltipPosition="top"
          >
            <tabler-icon [icon]="icons.cancel" aria-hidden="true" />
            <span i18n="@@admin.catalog.cancel">Cancelar</span>
          </button>
        }
      </div>
    </form>
  `,
})
export class CategoryList {
  protected readonly icons = {
    edit: IconPencil,
    deactivate: IconPlayerPause,
    reactivate: IconPlayerPlay,
    save: IconDeviceFloppy,
    create: IconPlus,
    cancel: IconX,
  };
  protected readonly tips = {
    deactivate: $localize`:@@admin.catalog.categories.deactivate.tooltip:Ocultar esta categoría del menú`,
    reactivate: $localize`:@@admin.catalog.categories.reactivate.tooltip:Volver a mostrar esta categoría en el menú`,
    save: $localize`:@@admin.catalog.categories.save.tooltip:Guardar los cambios de la categoría`,
    create: $localize`:@@admin.catalog.categories.create.tooltip:Agregar una categoría nueva`,
  };
  private readonly api = inject(CatalogApi);
  private readonly notifier = inject(ErrorNotifier);

  protected readonly categories = signal<readonly Category[]>([]);
  protected readonly stations = signal<readonly Station[]>([]);
  protected readonly editing = signal<Category | null>(null);
  protected readonly saving = signal(false);
  protected readonly numberAriaInvalid = numberAriaInvalid;
  protected readonly stationOptions = computed(() => [...this.stations()]);
  private readonly stationNames = computed(
    () => new Map(this.stations().map((station) => [station.id, station.name])),
  );
  protected readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/)],
    }),
    displayOrder: new FormControl<number | null>(0, [Validators.required, Validators.min(0)]),
    stationId: new FormControl<string | null>(null),
  });

  constructor() {
    this.load();
    this.api.stations().subscribe({
      next: (list) => this.stations.set(list),
      error: (error: unknown) => this.notifier.show(error),
    });
  }

  protected stationName(id: string): string {
    return this.stationNames().get(id) ?? '';
  }

  protected invalid(name: 'name' | 'displayOrder'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  protected edit(category: Category): void {
    this.editing.set(category);
    this.form.setValue({
      name: category.name,
      displayOrder: category.displayOrder,
      stationId: category.stationId,
    });
  }

  protected cancel(): void {
    this.editing.set(null);
    this.form.reset({ name: '', displayOrder: 0, stationId: null });
  }

  protected toggle(category: Category): void {
    const request = category.active
      ? this.api.deactivateCategory(category.id, category.etag)
      : this.api.reactivateCategory(category.id, category.etag);
    request.subscribe({
      next: (saved) => this.replace(saved),
      error: (error: unknown) => this.fail(error),
    });
  }

  protected submit(): void {
    if (this.saving()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { name, displayOrder, stationId } = this.form.getRawValue();
    const target = this.editing();
    const base = { name: name.trim(), displayOrder: displayOrder ?? 0 };
    const request = target
      ? this.api.changeCategory(target.id, target.etag, {
          ...base,
          stationId: stationId ?? target.stationId,
        })
      : this.api.createCategory(stationId ? { ...base, stationId } : base);
    this.saving.set(true);
    request.subscribe({
      next: (saved) => {
        if (target) {
          this.replace(saved);
        } else {
          this.categories.update((list) => [...list, saved]);
        }
        this.cancel();
        this.saving.set(false);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.fail(error, this.form);
      },
    });
  }

  private replace(saved: Category): void {
    this.categories.update((list) => list.map((item) => (item.id === saved.id ? saved : item)));
    if (this.editing()?.id === saved.id) {
      this.editing.set(saved);
    }
  }

  private fail(error: unknown, form?: FormGroup): void {
    reportFailure(this.notifier, error, form);
    if (isStale(error)) {
      this.load();
    }
  }

  private load(): void {
    this.api.categories().subscribe({
      next: (list) => {
        this.categories.set(list);
        const current = this.editing();
        const fresh = current && list.find((item) => item.id === current.id);
        if (fresh) {
          this.edit(fresh);
        } else if (current) {
          this.cancel();
        }
      },
      error: (error: unknown) => this.notifier.show(error),
    });
  }
}
