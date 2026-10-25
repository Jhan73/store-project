import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { FormControl, FormsModule, NgModel, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { tap } from 'rxjs';
import {
  IconPencil,
  IconPlayerPause,
  IconPlayerPlay,
  IconPlus,
  IconTrash,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Select } from 'primeng/select';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { Tooltip } from 'primeng/tooltip';
import type { Category, Product } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { formatMoneyOrDash, safeMinorUnits } from '../../../core/money/money';
import { ConfirmAction } from '../../../shared/state/confirm-action';
import { UndoNotice } from '../../../shared/state/undo-notice';
import { UndoableChanges } from '../../../shared/state/undoable-changes';
import { DataColumn, DataTable } from '../../../shared/ui/data-table/data-table';
import { FormField } from '../../../shared/ui/form-field/form-field';
import { StatusTag } from '../../../shared/ui/status-tag/status-tag';
import { CatalogApi } from './catalog-api';
import { PendingIds } from '../../../shared/state/pending-ids';
import { isStale } from '../admin-errors';

@Component({
  selector: 'app-product-list',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    RouterLink,
    ButtonDirective,
    Select,
    ToggleSwitch,
    TablerIconComponent,
    Tooltip,
    DataTable,
    FormField,
    StatusTag,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './catalog.scss',
  template: `
    <div class="toolbar">
      <h2 i18n="@@admin.catalog.products.title">Productos</h2>
      <a
        pButton
        routerLink="/admin/catalog/products/new"
        pTooltip="Crear un producto nuevo"
        i18n-pTooltip="@@admin.catalog.products.new.tooltip"
        tooltipPosition="top"
      >
        <tabler-icon [icon]="icons.create" aria-hidden="true" />
        <span i18n="@@admin.catalog.products.new">Nuevo producto</span>
      </a>
    </div>

    <app-form-field
      label="Categoría"
      i18n-label="@@admin.catalog.products.filter"
      controlId="product-category-filter"
    >
      <p-select
        [appendTo]="'body'"
        inputId="product-category-filter"
        [ariaLabel]="labels.category"
        placeholder="Todas"
        i18n-placeholder="@@admin.catalog.products.allCategories"
        optionLabel="name"
        optionValue="id"
        [options]="categoryOptions()"
        [filter]="true"
        filterBy="name"
        [resetFilterOnHide]="true"
        filterPlaceholder="Buscar"
        i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
        [showClear]="true"
        [formControl]="categoryFilter"
        (onChange)="categoryId.set($event.value ?? '')"
      />
    </app-form-field>

    <app-data-table
      label="Listado de productos"
      i18n-label="@@admin.catalog.products.tableScroll"
      emptyText="Todavía no hay productos."
      i18n-emptyText="@@admin.catalog.products.empty"
      [value]="rowsView()"
      [columns]="columns"
      [rows]="20"
    >
      <ng-template #row let-product>
        <td>
          @if (product.imageUrl) {
            <img
              [src]="product.imageUrl"
              [alt]="product.name"
              width="48"
              height="48"
              [attr.loading]="'lazy'"
            />
          }
        </td>
        <td>{{ product.name }}</td>
        <td>{{ product.categoryName }}</td>
        <td>{{ product.priceText }}</td>
        <td>
          <app-status-tag
            [value]="product.stateLabel"
            [severity]="product.shownActive ? 'success' : 'secondary'"
          />
        </td>
        <td>
          <p-toggleswitch
            #availability="ngModel"
            [ngModel]="product.available"
            [disabled]="savingAvailability().has(product.id)"
            [attr.data-testid]="'available-' + product.id"
            [ariaLabel]="product.name"
            (onChange)="setAvailability(product, $event.checked, availability)"
          />
        </td>
        <td class="actions">
          <div class="row-actions">
            <a
              pButton
              severity="secondary"
              [size]="'small'"
              [routerLink]="['/admin/catalog/products', product.id]"
              pTooltip="Editar los datos de este producto"
              i18n-pTooltip="@@admin.catalog.products.edit.tooltip"
              tooltipPosition="top"
            >
              <tabler-icon [icon]="icons.edit" aria-hidden="true" />
              <span i18n="@@admin.catalog.edit">Editar</span>
            </a>
            @if (!product.deletable || !product.shownActive) {
              <button
                pButton
                type="button"
                severity="secondary"
                [outlined]="true"
                [size]="'small'"
                [attr.data-testid]="'toggle-' + product.id"
                [disabled]="changes.has(product.id)"
                (click)="toggle(product)"
                [pTooltip]="product.shownActive ? tips.deactivate : tips.reactivate"
                tooltipPosition="top"
              >
                @if (product.shownActive) {
                  <tabler-icon [icon]="icons.deactivate" aria-hidden="true" />
                  <ng-container i18n="@@admin.catalog.deactivate">Desactivar</ng-container>
                } @else {
                  <tabler-icon [icon]="icons.reactivate" aria-hidden="true" />
                  <ng-container i18n="@@admin.catalog.reactivate">Reactivar</ng-container>
                }
              </button>
            }
            @if (product.deletable) {
              <button
                pButton
                type="button"
                severity="danger"
                [outlined]="true"
                [size]="'small'"
                [attr.data-testid]="'delete-' + product.id"
                [disabled]="changes.has(product.id) || removing.has(product.id)"
                (click)="remove(product)"
                pTooltip="Eliminar este producto, que nunca se usó en una cuenta"
                i18n-pTooltip="@@admin.catalog.products.delete.tooltip"
                tooltipPosition="top"
              >
                <tabler-icon [icon]="icons.delete" aria-hidden="true" />
                <span i18n="@@admin.catalog.products.delete">Eliminar</span>
              </button>
            }
          </div>
        </td>
      </ng-template>
    </app-data-table>
  `,
})
export class ProductList {
  protected readonly icons = {
    create: IconPlus,
    edit: IconPencil,
    deactivate: IconPlayerPause,
    reactivate: IconPlayerPlay,
    delete: IconTrash,
  };
  protected readonly tips = {
    deactivate: $localize`:@@admin.catalog.products.deactivate.tooltip:Ocultar este producto del menú`,
    reactivate: $localize`:@@admin.catalog.products.reactivate.tooltip:Volver a mostrar este producto en el menú`,
  };
  protected readonly labels = {
    category: $localize`:@@admin.catalog.products.filter:Categoría`,
    active: $localize`:@@admin.catalog.products.active:Activo`,
    inactive: $localize`:@@admin.catalog.products.inactive:Inactivo`,
  };
  protected readonly columns: readonly DataColumn[] = [
    { field: '', header: $localize`:@@admin.catalog.products.image:Imagen` },
    { field: 'name', header: $localize`:@@admin.catalog.products.name:Nombre`, sortable: true, filter: true },
    {
      field: 'categoryName',
      header: $localize`:@@admin.catalog.products.category:Categoría`,
      sortable: true,
      filter: true,
    },
    {
      field: 'priceMinor',
      header: $localize`:@@admin.catalog.products.price:Precio`,
      sortable: true,
      searchable: false,
    },
    { field: 'stateLabel', header: $localize`:@@admin.catalog.products.state:Estado`, sortable: true },
    { field: '', header: $localize`:@@admin.catalog.products.available:Disponible` },
    { field: '', header: $localize`:@@admin.catalog.actions:Acciones` },
  ];
  private readonly api = inject(CatalogApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly confirm = inject(ConfirmAction);

  protected readonly changes = new UndoableChanges<boolean>(inject(UndoNotice));
  protected readonly removing = new PendingIds();
  protected readonly categories = signal<readonly Category[]>([]);
  protected readonly categoryOptions = computed(() => [...this.categories()]);
  protected readonly categoryId = signal('');
  protected readonly categoryFilter = new FormControl<string | null>(null);
  protected readonly products = signal<readonly Product[]>([]);
  protected readonly savingAvailability = signal<ReadonlySet<string>>(new Set());
  private lastRequest = 0;
  private readonly categoryNames = computed(
    () => new Map(this.categories().map((category) => [category.id, category.name])),
  );
  protected readonly rowsView = computed(() =>
    this.products()
      .filter((product) => !this.categoryId() || product.categoryId === this.categoryId())
      .map((product) => {
        const shownActive = this.changes.pending(product.id) ?? product.active;
        return {
          ...product,
          categoryName: this.categoryNames().get(product.categoryId) ?? '',
          priceText: formatMoneyOrDash(product.price),
          priceMinor: safeMinorUnits(product.price) ?? 0,
          shownActive,
          stateLabel: shownActive ? this.labels.active : this.labels.inactive,
        };
      }),
  );

  constructor() {
    inject(DestroyRef).onDestroy(() => this.changes.flush());
    this.api.categories().subscribe({
      next: (list) => this.categories.set(list),
      error: (error: unknown) => this.notifier.show(error),
    });
    this.load();
  }

  protected setAvailability(product: Product, available: boolean, control: NgModel): void {
    this.markSaving(product.id, true);
    this.api.setProductAvailability(product.id, available).subscribe({
      next: (result) => {
        this.markSaving(product.id, false);
        this.patch(product.id, { available: result.available });
      },
      error: (error: unknown) => {
        this.markSaving(product.id, false);
        // The switch already flipped on screen; put it back to what the list holds now.
        const held = this.products().find((item) => item.id === product.id);
        control.control.setValue(held?.available ?? product.available);
        this.notifier.show(error);
      },
    });
  }

  protected async remove(product: Product): Promise<void> {
    if (this.changes.has(product.id) || this.removing.has(product.id)) {
      return;
    }
    const confirmed = await this.confirm.ask({
      header: $localize`:@@admin.catalog.products.delete.header:Eliminar producto`,
      message: $localize`:@@admin.catalog.products.delete.confirm:Se eliminará el producto "${product.name}:name:" y su imagen de forma definitiva y no se puede deshacer.`,
      acceptLabel: $localize`:@@admin.catalog.products.delete:Eliminar`,
    });
    if (!confirmed) {
      return;
    }
    this.removing.add(product.id);
    this.api.deleteProduct(product.id, product.etag).subscribe({
      next: () => {
        this.removing.delete(product.id);
        this.products.update((list) => list.filter((item) => item.id !== product.id));
      },
      error: (error: unknown) => {
        this.removing.delete(product.id);
        this.fail(error);
      },
    });
  }

  protected toggle(product: Product): void {
    if (!product.active) {
      this.api.reactivateProduct(product.id, product.etag).subscribe({
        next: (saved) => this.replace(saved),
        error: (error: unknown) => this.fail(error),
      });
      return;
    }
    this.changes.schedule(
      product.id,
      false,
      $localize`:@@admin.catalog.products.deactivated:Producto "${product.name}:name:" desactivado`,
      () =>
        this.api
          .deactivateProduct(product.id, product.etag)
          .pipe(tap((saved) => this.replace(saved))),
      (error) => this.fail(error),
    );
  }

  private load(): void {
    const request = ++this.lastRequest;
    this.api.allProducts().subscribe({
      next: (list) => {
        if (request === this.lastRequest) {
          this.products.set(list);
        }
      },
      error: (error: unknown) => {
        if (request === this.lastRequest) {
          this.notifier.show(error);
        }
      },
    });
  }

  private fail(error: unknown): void {
    this.notifier.show(error);
    if (isStale(error)) {
      this.load();
    }
  }

  private markSaving(id: string, saving: boolean): void {
    this.savingAvailability.update((ids) => {
      const next = new Set(ids);
      if (saving) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }

  private patch(id: string, changes: Partial<Product>): void {
    this.products.update((list) => list.map((item) => (item.id === id ? { ...item, ...changes } : item)));
  }

  private replace(saved: Product): void {
    this.products.update((list) => list.map((item) => (item.id === saved.id ? saved : item)));
  }
}
