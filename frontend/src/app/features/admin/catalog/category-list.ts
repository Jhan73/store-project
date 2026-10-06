import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import type { Category, Station } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { CatalogApi } from './catalog-api';
import { isStale, reportFailure } from './catalog-errors';

@Component({
  selector: 'app-category-list',
  imports: [ReactiveFormsModule, ButtonDirective, InputText],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './catalog.scss',
  template: `
    <h2 i18n="@@admin.catalog.categories.title">Categorías</h2>

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
                i18n="@@admin.catalog.edit"
              >
                Editar
              </button>
              <button
                pButton
                type="button"
                severity="secondary"
                [size]="'small'"
                [outlined]="true"
                [attr.data-testid]="'toggle-' + category.id"
                (click)="toggle(category)"
              >
                @if (category.active) {
                  <ng-container i18n="@@admin.catalog.deactivate">Desactivar</ng-container>
                } @else {
                  <ng-container i18n="@@admin.catalog.reactivate">Reactivar</ng-container>
                }
              </button>
            </td>
          </tr>
        }
      </tbody>
    </table>

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
        <input
          pInputText
          id="category-order"
          type="number"
          min="0"
          formControlName="displayOrder"
          [attr.aria-invalid]="invalid('displayOrder') ? 'true' : null"
        />
        @if (invalid('displayOrder')) {
          <small class="error" i18n="@@admin.catalog.categories.orderInvalid">Escribe un número entero.</small>
        }
      </div>
      <div class="field">
        <label for="category-station" i18n="@@admin.catalog.categories.stationLabel">Estación de preparación</label>
        <select id="category-station" formControlName="stationId">
          @if (!editing()) {
            <option value="" i18n="@@admin.catalog.categories.defaultStation">Estación predeterminada</option>
          }
          @for (station of stations(); track station.id) {
            <option [value]="station.id">{{ station.name }}</option>
          }
        </select>
      </div>
      <div class="actions">
        <button pButton type="submit" [loading]="saving()">
          @if (editing()) {
            <ng-container i18n="@@admin.catalog.categories.save">Guardar cambios</ng-container>
          } @else {
            <ng-container i18n="@@admin.catalog.categories.create">Agregar categoría</ng-container>
          }
        </button>
        @if (editing()) {
          <button pButton type="button" severity="secondary" (click)="cancel()" i18n="@@admin.catalog.cancel">
            Cancelar
          </button>
        }
      </div>
    </form>
  `,
})
export class CategoryList {
  private readonly api = inject(CatalogApi);
  private readonly notifier = inject(ErrorNotifier);

  protected readonly categories = signal<readonly Category[]>([]);
  protected readonly stations = signal<readonly Station[]>([]);
  protected readonly editing = signal<Category | null>(null);
  protected readonly saving = signal(false);
  private readonly stationNames = computed(
    () => new Map(this.stations().map((station) => [station.id, station.name])),
  );
  protected readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/)],
    }),
    displayOrder: new FormControl<number | null>(0, [Validators.required, Validators.min(0)]),
    stationId: new FormControl('', { nonNullable: true }),
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
    this.form.reset({ name: '', displayOrder: 0, stationId: '' });
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
      ? this.api.changeCategory(target.id, target.etag, { ...base, stationId })
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
