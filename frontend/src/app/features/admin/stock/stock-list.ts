import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule, NgModel } from '@angular/forms';
import {
  IconAdjustmentsHorizontal,
  IconAlertTriangle,
  IconListDetails,
  IconPackageImport,
  IconPencil,
  IconPlus,
  IconRefresh,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { Tooltip } from 'primeng/tooltip';
import type { Product, StockItem } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { PendingIds } from '../../../shared/state/pending-ids';
import { TableScroll } from '../../../shared/ui/table-scroll/table-scroll';
import { shortId } from '../audit/audit-labels';
import { StockAdjustmentDialog } from './stock-adjustment-dialog';
import { StockApi } from './stock-api';
import { StockEntryDialog } from './stock-entry-dialog';
import { StockMovementsDialog } from './stock-movements-dialog';
import { StockTrackDialog } from './stock-track-dialog';

type Status = 'loading' | 'ready' | 'error';
type CatalogStatus = 'loading' | 'ready' | 'failed';

interface StockRow {
  readonly item: StockItem;
  readonly name: string;
}

const NUMBERS = new Intl.NumberFormat('es-PE');

@Component({
  selector: 'app-stock-list',
  imports: [
    FormsModule,
    ButtonDirective,
    StockAdjustmentDialog,
    StockEntryDialog,
    StockMovementsDialog,
    StockTrackDialog,
    TableScroll,
    TablerIconComponent,
    ToggleSwitch,
    Tooltip,
  ],
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
      <button
        pButton
        type="button"
        data-testid="track-new"
        (click)="openTrack(null)"
        pTooltip="Empezar a controlar el stock de un producto"
        i18n-pTooltip="@@admin.stock.track.new.tooltip"
        tooltipPosition="top"
      >
        <tabler-icon [icon]="icons.create" aria-hidden="true" />
        <span i18n="@@admin.stock.track.new">Controlar un producto</span>
      </button>
    </div>

    @if (productsStatus() === 'failed') {
      <div class="state" role="alert" data-testid="catalog-failed">
        <p i18n="@@admin.stock.catalogFailed">No se pudieron cargar los nombres de los productos.</p>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="catalog-retry"
          (click)="loadProducts()"
          pTooltip="Volver a cargar los nombres de los productos"
          i18n-pTooltip="@@admin.stock.catalogRetry.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.retry" aria-hidden="true" />
          <span i18n="@@admin.stock.catalogRetry">Reintentar</span>
        </button>
      </div>
    }

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
                  <th i18n="@@admin.stock.col.tracked">Controlado</th>
                  <th><span class="sr-only" i18n="@@admin.stock.col.actions">Acciones</span></th>
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
                    <td>
                      <p-toggleswitch
                        #tracked="ngModel"
                        [ngModel]="row.item.tracked"
                        [disabled]="pending.has(row.item.productId)"
                        [attr.data-testid]="'tracked-' + row.item.productId"
                        [ariaLabel]="row.name"
                        (onChange)="setTracked(row.item, $event.checked, tracked)"
                      />
                    </td>
                    <td class="actions">
                      <button
                        pButton
                        type="button"
                        [size]="'small'"
                        [attr.data-testid]="'entry-' + row.item.productId"
                        (click)="openEntry(row.item)"
                        pTooltip="Registrar unidades que entraron de un proveedor"
                        i18n-pTooltip="@@admin.stock.entry.tooltip"
                        tooltipPosition="top"
                      >
                        <tabler-icon [icon]="icons.entry" aria-hidden="true" />
                        <span i18n="@@admin.stock.entry">Entrada</span>
                      </button>
                      <button
                        pButton
                        type="button"
                        severity="secondary"
                        [size]="'small'"
                        [attr.data-testid]="'adjust-' + row.item.productId"
                        (click)="openAdjustment(row.item)"
                        pTooltip="Corregir las unidades de este producto con un motivo"
                        i18n-pTooltip="@@admin.stock.adjust.tooltip"
                        tooltipPosition="top"
                      >
                        <tabler-icon [icon]="icons.adjust" aria-hidden="true" />
                        <span i18n="@@admin.stock.adjust">Ajustar</span>
                      </button>
                      <button
                        pButton
                        type="button"
                        severity="secondary"
                        [outlined]="true"
                        [size]="'small'"
                        [attr.data-testid]="'movements-' + row.item.productId"
                        (click)="openMovements(row.item)"
                        pTooltip="Ver los movimientos de este producto"
                        i18n-pTooltip="@@admin.stock.movements.tooltip"
                        tooltipPosition="top"
                      >
                        <tabler-icon [icon]="icons.movements" aria-hidden="true" />
                        <span i18n="@@admin.stock.movements">Movimientos</span>
                      </button>
                      <button
                        pButton
                        type="button"
                        severity="secondary"
                        [outlined]="true"
                        [size]="'small'"
                        [attr.data-testid]="'threshold-' + row.item.productId"
                        (click)="openTrack(row.item)"
                        pTooltip="Cambiar el umbral de stock bajo de este producto"
                        i18n-pTooltip="@@admin.stock.threshold.tooltip"
                        tooltipPosition="top"
                      >
                        <tabler-icon [icon]="icons.edit" aria-hidden="true" />
                        <span i18n="@@admin.stock.threshold">Umbral</span>
                      </button>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </app-table-scroll>
        }
      }
    }

    <app-stock-movements-dialog
      [visible]="movementsOpen()"
      [item]="movementsTarget()"
      [productName]="movementsName()"
      (dismissed)="closeMovements()"
    />

    <app-stock-adjustment-dialog
      [visible]="adjustOpen()"
      [item]="adjustTarget()"
      [productName]="adjustName()"
      (saved)="adjustSaved($event)"
      (stale)="load(true)"
      (dismissed)="closeAdjustment()"
    />

    <app-stock-entry-dialog
      [visible]="entryOpen()"
      [item]="entryTarget()"
      [productName]="entryName()"
      (saved)="entrySaved($event)"
      (dismissed)="closeEntry()"
    />

    <app-stock-track-dialog
      [visible]="trackOpen()"
      [target]="trackTarget()"
      [products]="products()"
      [catalogStatus]="productsStatus()"
      (catalogRetried)="loadProducts()"
      (saved)="trackSaved($event)"
      (dismissed)="closeTrack()"
    />
  `,
})
export class StockList {
  private readonly api = inject(StockApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly icons = {
    retry: IconRefresh,
    low: IconAlertTriangle,
    create: IconPlus,
    edit: IconPencil,
    entry: IconPackageImport,
    adjust: IconAdjustmentsHorizontal,
    movements: IconListDetails,
  };
  protected readonly status = signal<Status>('loading');
  protected readonly lowOnly = signal(false);
  protected readonly movementsOpen = signal(false);
  protected readonly movementsTarget = signal<StockItem | null>(null);
  protected readonly adjustOpen = signal(false);
  protected readonly adjustTarget = signal<StockItem | null>(null);
  protected readonly entryOpen = signal(false);
  protected readonly entryTarget = signal<StockItem | null>(null);
  protected readonly trackOpen = signal(false);
  protected readonly trackTarget = signal<StockItem | null>(null);
  protected readonly products = signal<readonly Product[]>([]);
  protected readonly productsStatus = signal<CatalogStatus>('loading');
  protected readonly pending = new PendingIds();
  private readonly items = signal<readonly StockItem[]>([]);
  private lastRequest = 0;

  private readonly names = computed(
    () => new Map(this.products().map((product) => [product.id, product.name])),
  );
  protected readonly rows = computed<StockRow[]>(() => {
    const rows = this.items().map((item) => ({
      item,
      name: this.nameOf(item.productId),
    }));
    // The low list already comes emptiest first.
    return this.lowOnly() ? rows : rows.sort((a, b) => a.name.localeCompare(b.name, 'es'));
  });

  protected readonly movementsName = computed(() => {
    const target = this.movementsTarget();
    return target ? this.nameOf(target.productId) : '';
  });
  protected readonly adjustName = computed(() => {
    const target = this.adjustTarget();
    return target ? this.nameOf(target.productId) : '';
  });
  protected readonly entryName = computed(() => {
    const target = this.entryTarget();
    return target ? this.nameOf(target.productId) : '';
  });

  constructor() {
    this.load();
    this.loadProducts();
  }

  protected nameOf(productId: string): string {
    return this.names().get(productId) ?? shortId(productId);
  }

  protected count(quantity: number): string {
    return NUMBERS.format(quantity);
  }

  protected setLowOnly(lowOnly: boolean): void {
    this.lowOnly.set(lowOnly);
    this.load();
  }

  protected openMovements(item: StockItem): void {
    this.movementsTarget.set(item);
    this.movementsOpen.set(true);
  }

  protected closeMovements(): void {
    this.movementsOpen.set(false);
  }

  protected openAdjustment(item: StockItem): void {
    this.adjustTarget.set(item);
    this.adjustOpen.set(true);
  }

  protected closeAdjustment(): void {
    this.adjustOpen.set(false);
  }

  // A late answer for a product that is no longer the open one must not close the open dialog.
  protected adjustSaved(saved: StockItem): void {
    if (this.adjustTarget()?.productId === saved.productId) {
      this.closeAdjustment();
    }
    this.load(true);
  }

  protected openEntry(item: StockItem): void {
    this.entryTarget.set(item);
    this.entryOpen.set(true);
  }

  protected closeEntry(): void {
    this.entryOpen.set(false);
  }

  protected entrySaved(saved: StockItem): void {
    if (this.entryTarget()?.productId === saved.productId) {
      this.closeEntry();
    }
    this.load(true);
  }

  protected openTrack(target: StockItem | null): void {
    this.trackTarget.set(target);
    this.trackOpen.set(true);
  }

  protected closeTrack(): void {
    this.trackOpen.set(false);
  }

  protected trackSaved(saved: StockItem): void {
    const target = this.trackTarget();
    if (!target || target.productId === saved.productId) {
      this.closeTrack();
    }
    this.load(true);
  }

  // The list only holds tracked items, so turning the switch off drops the row.
  protected setTracked(item: StockItem, tracked: boolean, control: NgModel): void {
    if (tracked) {
      return;
    }
    const id = item.productId;
    this.pending.add(id);
    this.api
      .track(id, { tracked: false, lowStockThreshold: item.lowStockThreshold })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.pending.delete(id);
          // An older read still in flight would bring the row back.
          this.lastRequest++;
          this.items.update((items) => items.filter((entry) => entry.productId !== id));
          if (this.status() === 'loading') {
            this.load();
          }
        },
        error: (error: unknown) => {
          this.pending.delete(id);
          control.control.setValue(true);
          this.notifier.show(error);
        },
      });
  }

  protected retry(): void {
    this.load();
    this.loadProducts();
  }

  protected load(quiet = false): void {
    const request = ++this.lastRequest;
    if (!quiet) {
      this.status.set('loading');
    }
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

  protected loadProducts(): void {
    this.productsStatus.set('loading');
    this.api
      .catalogProducts()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (products) => {
          this.products.set(products);
          this.productsStatus.set('ready');
        },
        error: (error: unknown) => {
          this.productsStatus.set('failed');
          this.notifier.show(error);
        },
      });
  }
}
