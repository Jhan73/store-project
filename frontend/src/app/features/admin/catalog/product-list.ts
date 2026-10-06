import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormsModule, NgModel, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonDirective } from 'primeng/button';
import { Select } from 'primeng/select';
import { ToggleSwitch } from 'primeng/toggleswitch';
import type { Category, Product, ProductPage } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { formatMoney } from '../../../core/money/money';
import { CatalogApi } from './catalog-api';
import { isStale } from '../admin-errors';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-product-list',
  imports: [FormsModule, ReactiveFormsModule, RouterLink, ButtonDirective, Select, ToggleSwitch],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './catalog.scss',
  template: `
    <div class="toolbar">
      <h2 i18n="@@admin.catalog.products.title">Productos</h2>
      <a pButton routerLink="/admin/catalog/products/new" i18n="@@admin.catalog.products.new"
        >Nuevo producto</a
      >
    </div>

    <div class="field">
      <label
        for="product-category-filter"
        id="product-category-filter-label"
        i18n="@@admin.catalog.products.filter"
        >Categoría</label
      >
      <p-select
        inputId="product-category-filter"
        [ariaLabelledBy]="'product-category-filter-label'"
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
        (onChange)="filter($event.value)"
      />
    </div>

    @if (current().content.length === 0) {
      <p class="muted" i18n="@@admin.catalog.products.empty">Todavía no hay productos.</p>
    } @else {
      <table>
        <thead>
          <tr>
            <th><span class="sr-only" i18n="@@admin.catalog.products.image">Imagen</span></th>
            <th i18n="@@admin.catalog.products.name">Nombre</th>
            <th i18n="@@admin.catalog.products.category">Categoría</th>
            <th i18n="@@admin.catalog.products.price">Precio</th>
            <th i18n="@@admin.catalog.products.state">Estado</th>
            <th i18n="@@admin.catalog.products.available">Disponible</th>
            <th><span class="sr-only" i18n="@@admin.catalog.actions">Acciones</span></th>
          </tr>
        </thead>
        <tbody>
          @for (product of current().content; track product.id) {
            <tr>
              <td>
                @if (product.imageUrl) {
                  <img [src]="product.imageUrl" [alt]="product.name" width="48" height="48" [attr.loading]="'lazy'" />
                }
              </td>
              <td>{{ product.name }}</td>
              <td>{{ categoryName(product.categoryId) }}</td>
              <td>{{ price(product) }}</td>
              <td>
                @if (product.active) {
                  <span i18n="@@admin.catalog.products.active">Activo</span>
                } @else {
                  <span class="muted" i18n="@@admin.catalog.products.inactive">Inactivo</span>
                }
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
                <a
                  pButton
                  severity="secondary"
                  [size]="'small'"
                  [routerLink]="['/admin/catalog/products', product.id]"
                  i18n="@@admin.catalog.edit"
                >
                  Editar
                </a>
                <button
                  pButton
                  type="button"
                  severity="secondary"
                  [outlined]="true"
                  [size]="'small'"
                  [attr.data-testid]="'toggle-' + product.id"
                  (click)="toggle(product)"
                >
                  @if (product.active) {
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
    }

    @if (current().totalPages > 1) {
      <nav class="toolbar" aria-label="Paginación" i18n-aria-label="@@admin.catalog.products.paging">
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="previous-page"
          [disabled]="current().page === 0"
          (click)="go(current().page - 1)"
          i18n="@@admin.catalog.products.previous"
        >
          Anterior
        </button>
        <span i18n="@@admin.catalog.products.pageOf"
          >Página {{ current().page + 1 }} de {{ current().totalPages }}</span
        >
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="next-page"
          [disabled]="current().page + 1 >= current().totalPages"
          (click)="go(current().page + 1)"
          i18n="@@admin.catalog.products.next"
        >
          Siguiente
        </button>
      </nav>
    }
  `,
})
export class ProductList {
  private readonly api = inject(CatalogApi);
  private readonly notifier = inject(ErrorNotifier);

  protected readonly categories = signal<readonly Category[]>([]);
  protected readonly categoryOptions = computed(() => [...this.categories()]);
  protected readonly categoryId = signal('');
  protected readonly categoryFilter = new FormControl<string | null>(null);
  protected readonly current = signal<ProductPage>({
    content: [],
    page: 0,
    size: PAGE_SIZE,
    totalElements: 0,
    totalPages: 0,
  });
  protected readonly savingAvailability = signal<ReadonlySet<string>>(new Set());
  private lastRequest = 0;
  private readonly categoryNames = computed(
    () => new Map(this.categories().map((category) => [category.id, category.name])),
  );

  constructor() {
    this.api.categories().subscribe({
      next: (list) => this.categories.set(list),
      error: (error: unknown) => this.notifier.show(error),
    });
    this.go(0);
  }

  protected categoryName(id: string): string {
    return this.categoryNames().get(id) ?? '';
  }

  protected price(product: Product): string {
    return formatMoney(product.price);
  }

  protected filter(categoryId: string | null): void {
    this.categoryId.set(categoryId ?? '');
    this.go(0);
  }

  protected go(page: number): void {
    const request = ++this.lastRequest;
    this.api
      .products({
        ...(this.categoryId() && { categoryId: this.categoryId() }),
        page,
        size: PAGE_SIZE,
      })
      .subscribe({
        next: (result) => {
          if (request === this.lastRequest) {
            this.current.set(result);
          }
        },
        error: (error: unknown) => {
          if (request === this.lastRequest) {
            this.notifier.show(error);
          }
        },
      });
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
        const held = this.current().content.find((item) => item.id === product.id);
        control.control.setValue(held?.available ?? product.available);
        this.notifier.show(error);
      },
    });
  }

  protected toggle(product: Product): void {
    const request = product.active
      ? this.api.deactivateProduct(product.id, product.etag)
      : this.api.reactivateProduct(product.id, product.etag);
    request.subscribe({
      next: (saved) => this.replace(saved),
      error: (error: unknown) => {
        this.notifier.show(error);
        if (isStale(error)) {
          this.go(this.current().page);
        }
      },
    });
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
    this.current.update((page) => ({
      ...page,
      content: page.content.map((item) => (item.id === id ? { ...item, ...changes } : item)),
    }));
  }

  private replace(saved: Product): void {
    this.current.update((page) => ({
      ...page,
      content: page.content.map((item) => (item.id === saved.id ? saved : item)),
    }));
  }
}
