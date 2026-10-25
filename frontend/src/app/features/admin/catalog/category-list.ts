import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  IconPencil,
  IconPlayerPause,
  IconPlayerPlay,
  IconPlus,
  IconTrash,
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
import { ConfirmAction } from '../../../shared/state/confirm-action';
import { PendingIds } from '../../../shared/state/pending-ids';
import { DataColumn, DataTable } from '../../../shared/ui/data-table/data-table';
import { FormDialog } from '../../../shared/ui/form-dialog/form-dialog';
import { FormField } from '../../../shared/ui/form-field/form-field';
import { StatusTag } from '../../../shared/ui/status-tag/status-tag';
import { CatalogApi } from './catalog-api';
import { ApiError } from '../../../core/errors/api-error';
import { isStale, reportFailure } from '../admin-errors';

// The station a category points at changed since the stations were read.
const STATION_CODES: ReadonlySet<string> = new Set([
  'catalog.station-inactive',
  'catalog.unknown-station',
  'catalog.station-not-found',
]);

@Component({
  selector: 'app-category-list',
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    InputNumber,
    InputText,
    Select,
    TablerIconComponent,
    Tooltip,
    DataTable,
    FormDialog,
    FormField,
    StatusTag,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './catalog.scss',
  template: `
    <div class="toolbar">
      <h2 i18n="@@admin.catalog.categories.title">Categorías</h2>
      <button
        pButton
        type="button"
        data-testid="add-category"
        (click)="add()"
        pTooltip="Agregar una categoría nueva"
        i18n-pTooltip="@@admin.catalog.categories.create.tooltip"
        tooltipPosition="top"
      >
        <tabler-icon [icon]="icons.create" aria-hidden="true" />
        <span i18n="@@admin.catalog.categories.create">Agregar categoría</span>
      </button>
    </div>

    <app-data-table
      label="Listado de categorías"
      i18n-label="@@admin.catalog.categories.tableScroll"
      [value]="rowsView()"
      [columns]="columns"
    >
      <ng-template #row let-category>
        <td>{{ category.name }}</td>
        <td>{{ category.stationName }}</td>
        <td>{{ category.displayOrder }}</td>
        <td>
          <app-status-tag
            [value]="category.stateLabel"
            [severity]="category.active ? 'success' : 'secondary'"
          />
        </td>
        <td class="actions">
          <div class="row-actions">
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
            @if (!category.deletable || !category.active) {
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
            }
            @if (category.deletable) {
              <button
                pButton
                type="button"
                severity="danger"
                [size]="'small'"
                [outlined]="true"
                [attr.data-testid]="'delete-' + category.id"
                [disabled]="removing.has(category.id)"
                (click)="remove(category)"
                pTooltip="Eliminar esta categoría, que no tiene productos"
                i18n-pTooltip="@@admin.catalog.categories.delete.tooltip"
                tooltipPosition="top"
              >
                <tabler-icon [icon]="icons.delete" aria-hidden="true" />
                <span i18n="@@admin.catalog.categories.delete">Eliminar</span>
              </button>
            }
          </div>
        </td>
      </ng-template>
    </app-data-table>

    <app-form-dialog
      [header]="editing() ? labels.editHeader : labels.createHeader"
      [visible]="open()"
      [busy]="saving()"
      (saved)="submit()"
      (dismissed)="close()"
    >
      <div [formGroup]="form" class="dialog-fields">
        <app-form-field
          label="Nombre de la categoría"
          i18n-label="@@admin.catalog.categories.nameLabel"
          controlId="category-name"
          [error]="invalid('name') ? labels.nameInvalid : null"
        >
          <input
            pInputText
            id="category-name"
            formControlName="name"
            autocomplete="off"
            maxlength="120"
            [attr.aria-invalid]="invalid('name') ? 'true' : null"
            fluid
          />
        </app-form-field>
        <app-form-field
          label="Orden de aparición"
          i18n-label="@@admin.catalog.categories.orderLabel"
          controlId="category-order"
          [error]="invalid('displayOrder') ? labels.orderInvalid : null"
        >
          <p-inputnumber
            inputId="category-order"
            formControlName="displayOrder"
            [useGrouping]="false"
            [invalid]="invalid('displayOrder')"
            [pt]="numberAriaInvalid(invalid('displayOrder'))"
            fluid
          />
        </app-form-field>
        <app-form-field
          label="Estación de preparación"
          i18n-label="@@admin.catalog.categories.stationLabel"
          controlId="category-station"
        >
          <p-select
            [appendTo]="'body'"
            inputId="category-station"
            [ariaLabel]="labels.station"
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
            fluid
          />
        </app-form-field>
      </div>
    </app-form-dialog>
  `,
})
export class CategoryList {
  protected readonly icons = {
    edit: IconPencil,
    deactivate: IconPlayerPause,
    reactivate: IconPlayerPlay,
    create: IconPlus,
    delete: IconTrash,
  };
  protected readonly tips = {
    deactivate: $localize`:@@admin.catalog.categories.deactivate.tooltip:Ocultar esta categoría del menú`,
    reactivate: $localize`:@@admin.catalog.categories.reactivate.tooltip:Volver a mostrar esta categoría en el menú`,
  };
  protected readonly labels = {
    active: $localize`:@@admin.catalog.active:Activa`,
    inactive: $localize`:@@admin.catalog.inactive:Inactiva`,
    createHeader: $localize`:@@admin.catalog.categories.createHeader:Agregar categoría`,
    editHeader: $localize`:@@admin.catalog.categories.editHeader:Editar categoría`,
    nameInvalid: $localize`:@@admin.catalog.categories.nameInvalid:Escribe un nombre válido.`,
    orderInvalid: $localize`:@@admin.catalog.categories.orderInvalid:Escribe un número entero.`,
    station: $localize`:@@admin.catalog.categories.stationLabel:Estación de preparación`,
  };
  protected readonly columns: readonly DataColumn[] = [
    { field: 'name', header: $localize`:@@admin.catalog.categories.name:Nombre`, sortable: true, filter: true },
    {
      field: 'stationName',
      header: $localize`:@@admin.catalog.categories.station:Estación`,
      sortable: true,
      filter: true,
    },
    { field: 'displayOrder', header: $localize`:@@admin.catalog.categories.order:Orden`, sortable: true },
    { field: 'stateLabel', header: $localize`:@@admin.catalog.categories.state:Estado`, sortable: true },
    { field: '', header: $localize`:@@admin.catalog.actions:Acciones` },
  ];
  private readonly api = inject(CatalogApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly confirm = inject(ConfirmAction);

  protected readonly categories = signal<readonly Category[]>([]);
  protected readonly stations = signal<readonly Station[]>([]);
  protected readonly editing = signal<Category | null>(null);
  protected readonly open = signal(false);
  protected readonly saving = signal(false);
  protected readonly removing = new PendingIds();
  protected readonly numberAriaInvalid = numberAriaInvalid;
  // A category can only be placed on an active station; the one it already has stays so it can be shown.
  protected readonly stationOptions = computed(() => {
    const current = this.editing()?.stationId;
    return this.stations().filter((station) => station.active || station.id === current);
  });
  private readonly stationNames = computed(
    () => new Map(this.stations().map((station) => [station.id, station.name])),
  );
  protected readonly rowsView = computed(() =>
    this.categories().map((category) => ({
      ...category,
      stationName: this.stationNames().get(category.stationId) ?? '',
      stateLabel: category.active ? this.labels.active : this.labels.inactive,
    })),
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
    this.loadStations();
  }

  protected invalid(name: 'name' | 'displayOrder'): boolean {
    const control = this.form.controls[name];
    return control.invalid && control.touched;
  }

  protected add(): void {
    this.editing.set(null);
    this.resetForm();
    this.open.set(true);
  }

  protected edit(category: Category): void {
    this.editing.set(category);
    this.fill(category);
    this.open.set(true);
  }

  protected close(): void {
    this.open.set(false);
    this.editing.set(null);
    this.resetForm();
  }

  protected async toggle(category: Category): Promise<void> {
    if (category.active) {
      const confirmed = await this.confirm.ask({
        header: $localize`:@@admin.catalog.categories.deactivate.header:Desactivar categoría`,
        message: $localize`:@@admin.catalog.categories.deactivate.confirm:La categoría "${category.name}:name:" y sus productos dejarán de mostrarse en el menú.`,
        acceptLabel: $localize`:@@admin.catalog.deactivate:Desactivar`,
      });
      if (!confirmed) {
        return;
      }
    }
    const request = category.active
      ? this.api.deactivateCategory(category.id, category.etag)
      : this.api.reactivateCategory(category.id, category.etag);
    request.subscribe({
      next: (saved) => this.replace(saved),
      error: (error: unknown) => this.fail(error),
    });
  }

  protected async remove(category: Category): Promise<void> {
    if (this.removing.has(category.id)) {
      return;
    }
    const confirmed = await this.confirm.ask({
      header: $localize`:@@admin.catalog.categories.delete.header:Eliminar categoría`,
      message: $localize`:@@admin.catalog.categories.delete.confirm:Se eliminará la categoría "${category.name}:name:" de forma definitiva y no se puede deshacer.`,
      acceptLabel: $localize`:@@admin.catalog.categories.delete:Eliminar`,
    });
    if (!confirmed) {
      return;
    }
    this.removing.add(category.id);
    this.api.deleteCategory(category.id, category.etag).subscribe({
      next: () => {
        this.removing.delete(category.id);
        this.categories.update((list) => list.filter((item) => item.id !== category.id));
        if (this.editing()?.id === category.id) {
          this.close();
        }
      },
      error: (error: unknown) => {
        this.removing.delete(category.id);
        this.fail(error);
      },
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
        this.close();
        this.saving.set(false);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.fail(error, this.form);
      },
    });
  }

  private fill(category: Category): void {
    this.form.reset({
      name: category.name,
      displayOrder: category.displayOrder,
      stationId: category.stationId,
    });
  }

  private resetForm(): void {
    this.form.reset({ name: '', displayOrder: 0, stationId: null });
  }

  private replace(saved: Category): void {
    this.categories.update((list) => list.map((item) => (item.id === saved.id ? saved : item)));
    if (this.editing()?.id === saved.id) {
      this.editing.set(saved);
    }
  }

  private fail(error: unknown, form?: FormGroup): void {
    reportFailure(this.notifier, error, form);
    const stationChanged = error instanceof ApiError && STATION_CODES.has(error.code ?? '');
    if (isStale(error) || stationChanged) {
      this.load();
    }
    if (stationChanged) {
      this.loadStations();
    }
  }

  private loadStations(): void {
    this.api.stations().subscribe({
      next: (list) => this.stations.set(list),
      error: (error: unknown) => this.notifier.show(error),
    });
  }

  private load(): void {
    this.api.categories().subscribe({
      next: (list) => {
        this.categories.set(list);
        const current = this.editing();
        const fresh = current && list.find((item) => item.id === current.id);
        if (fresh) {
          this.editing.set(fresh);
          this.fill(fresh);
        } else if (current) {
          this.close();
        }
      },
      error: (error: unknown) => this.notifier.show(error),
    });
  }
}
