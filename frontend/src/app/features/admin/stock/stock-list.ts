import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { IconAlertTriangle, IconRefresh, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { Tooltip } from 'primeng/tooltip';
import type { Product, StockItem } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { TableScroll } from '../../../shared/ui/table-scroll/table-scroll';
import { shortId } from '../audit/audit-labels';
import { StockApi } from './stock-api';

type Status = 'loading' | 'ready' | 'error';

interface StockRow {
  readonly item: StockItem;
  readonly name: string;
}

const NUMBERS = new Intl.NumberFormat('es-PE');

@Component({
  selector: 'app-stock-list',
  imports: [FormsModule, ButtonDirective, TableScroll, TablerIconComponent, ToggleSwitch, Tooltip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../admin-section.scss', './stock-list.scss'],
  template: `
    <h1 i18n="@@admin.stock.title">Inventario</h1>

    <div class="toolbar">
      <div class="check">
        <p-toggleswitch
          inputId="stock-low-only"
          data-testid="low-only"
          [ngModel]="lowOnly()"
          (onChange)="setLowOnly($event.checked)"
        />
        <label for="stock-low-only" i18n="@@admin.stock.lowOnly">Solo stock bajo</label>
      </div>
    </div>

    @switch (status()) {
      @case ('loading') {
        <p class="muted" i18n="@@admin.stock.loading">Cargando…</p>
      }
      @case ('error') {
        <div class="state" role="alert">
          <p i18n="@@admin.stock.error">No se pudo cargar el inventario.</p>
          <button
            pButton
            type="button"
            severity="secondary"
            data-testid="retry"
            (click)="retry()"
            pTooltip="Volver a cargar el inventario"
            i18n-pTooltip="@@admin.stock.retry.tooltip"
            tooltipPosition="top"
          >
            <tabler-icon [icon]="icons.retry" aria-hidden="true" />
            <span i18n="@@admin.stock.retry">Reintentar</span>
          </button>
        </div>
      }
      @case ('ready') {
        @if (rows().length === 0) {
          <p class="muted" data-testid="empty">
            @if (lowOnly()) {
              <ng-container i18n="@@admin.stock.emptyLow">Ningún producto tiene el stock bajo.</ng-container>
            } @else {
              <ng-container i18n="@@admin.stock.empty">
                Todavía no hay productos con control de stock.
              </ng-container>
            }
          </p>
        } @else {
          <app-table-scroll label="Inventario de productos" i18n-label="@@admin.stock.tableScroll">
            <table>
              <thead>
                <tr>
                  <th i18n="@@admin.stock.col.product">Producto</th>
                  <th i18n="@@admin.stock.col.available">Disponible</th>
                  <th i18n="@@admin.stock.col.onHand">En existencia</th>
                  <th i18n="@@admin.stock.col.reserved">Reservado</th>
                  <th i18n="@@admin.stock.col.threshold">Umbral de stock bajo</th>
                  <th i18n="@@admin.stock.col.state">Estado</th>
                </tr>
              </thead>
              <tbody>
                @for (row of rows(); track row.item.productId) {
                  <tr>
                    <td>{{ row.name }}</td>
                    <td>{{ count(row.item.available) }}</td>
                    <td>{{ count(row.item.onHand) }}</td>
                    <td>{{ count(row.item.reserved) }}</td>
                    <td>{{ count(row.item.lowStockThreshold) }}</td>
                    <td>
                      @if (row.item.low) {
                        <span class="badge low" data-testid="low-badge">
                          <tabler-icon [icon]="icons.low" aria-hidden="true" />
                          <span i18n="@@admin.stock.low">Stock bajo</span>
                        </span>
                      } @else {
                        <span class="muted" i18n="@@admin.stock.normal">Normal</span>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </app-table-scroll>
        }
      }
    }
  `,
})
export class StockList {
  private readonly api = inject(StockApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly icons = { retry: IconRefresh, low: IconAlertTriangle };
  protected readonly status = signal<Status>('loading');
  protected readonly lowOnly = signal(false);
  private readonly items = signal<readonly StockItem[]>([]);
  private readonly products = signal<readonly Product[]>([]);
  private lastRequest = 0;

  private readonly names = computed(
    () => new Map(this.products().map((product) => [product.id, product.name])),
  );
  protected readonly rows = computed<StockRow[]>(() => {
    const rows = this.items().map((item) => ({
      item,
      name: this.names().get(item.productId) ?? shortId(item.productId),
    }));
    // The low list already comes emptiest first.
    return this.lowOnly() ? rows : rows.sort((a, b) => a.name.localeCompare(b.name, 'es'));
  });

  constructor() {
    this.load();
    this.loadProducts();
  }

  protected count(quantity: number): string {
    return NUMBERS.format(quantity);
  }

  protected setLowOnly(lowOnly: boolean): void {
    this.lowOnly.set(lowOnly);
    this.load();
  }

  protected retry(): void {
    this.load();
    this.loadProducts();
  }

  private load(): void {
    const request = ++this.lastRequest;
    this.status.set('loading');
    (this.lowOnly() ? this.api.low() : this.api.list())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (items) => {
          if (request === this.lastRequest) {
            this.items.set(items);
            this.status.set('ready');
          }
        },
        error: (error: unknown) => {
          if (request === this.lastRequest) {
            this.status.set('error');
            this.notifier.show(error);
          }
        },
      });
  }

  private loadProducts(): void {
    this.api
      .catalogProducts()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (products) => this.products.set(products),
        error: (error: unknown) => this.notifier.show(error),
      });
  }
}
