import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { IconRefresh, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { InputNumber } from 'primeng/inputnumber';
import { Select } from 'primeng/select';
import { Tooltip } from 'primeng/tooltip';
import type { Product, StockItem } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { numberAriaInvalid, selectAriaInvalid } from '../../../shared/forms/aria-invalid';
import { FormDialog } from '../../../shared/ui/form-dialog/form-dialog';
import { FormField } from '../../../shared/ui/form-field/form-field';
import { reportFailure } from '../admin-errors';
import { wholeNumber } from '../admin-validators';
import { shortId } from '../audit/audit-labels';
import { StockApi } from './stock-api';

const MAX_THRESHOLD = 1_000_000;

// Starts tracking a product (picked from the untracked ones) or edits the threshold of a tracked one.
@Component({
  selector: 'app-stock-track-dialog',
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    FormDialog,
    FormField,
    InputNumber,
    Select,
    TablerIconComponent,
    Tooltip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .dialog-fields {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
      margin-top: var(--space-3);
    }

    .field {
      display: flex;
      flex-direction: column;
      gap: var(--space-1);
      margin-top: var(--space-3);
    }

    p {
      margin: 0;
    }

    .muted {
      color: var(--muted-foreground);
    }

    .error {
      color: var(--destructive);
    }
  `,
  template: `
    <app-form-dialog
      [header]="header()"
      width="30rem"
      [visible]="visible()"
      [busy]="saving()"
      (saved)="submit()"
      (dismissed)="dismissed.emit()"
    >
      <p class="muted" i18n="@@admin.stock.track.help">
        Al controlar un producto sin unidades disponibles, deja de estar disponible en el menú. Marcarlo
        como agotado a mano sigue siendo independiente.
      </p>
      <div [formGroup]="form" class="dialog-fields">
        @if (target(); as current) {
          <div class="field">
            <span i18n="@@admin.stock.track.product">Producto</span>
            <strong data-testid="track-product">{{ nameOf(current.productId) }}</strong>
          </div>
        } @else if (trackedFailed()) {
          <div class="field" role="alert" data-testid="track-failed">
            <p i18n="@@admin.stock.track.failed">No se pudo cargar la lista de productos con stock.</p>
            <button
              pButton
              type="button"
              severity="secondary"
              data-testid="track-retry"
              (click)="loadTracked()"
              pTooltip="Volver a cargar los productos con stock"
              i18n-pTooltip="@@admin.stock.track.retry.tooltip"
              tooltipPosition="top"
            >
              <tabler-icon [icon]="icons.retry" aria-hidden="true" />
              <span i18n="@@admin.stock.track.retry">Reintentar</span>
            </button>
          </div>
        } @else if (catalogStatus() === 'failed') {
          <div class="field" role="alert" data-testid="track-catalog-failed">
            <p i18n="@@admin.stock.track.catalogFailed">No se pudo cargar el catálogo de productos.</p>
            <button
              pButton
              type="button"
              severity="secondary"
              data-testid="track-catalog-retry"
              (click)="catalogRetried.emit()"
              pTooltip="Volver a cargar el catálogo de productos"
              i18n-pTooltip="@@admin.stock.track.catalogRetry.tooltip"
              tooltipPosition="top"
            >
              <tabler-icon [icon]="icons.retry" aria-hidden="true" />
              <span i18n="@@admin.stock.track.catalogRetry">Reintentar</span>
            </button>
          </div>
        } @else if (catalogStatus() === 'loading') {
          <p class="muted" data-testid="track-catalog-loading" i18n="@@admin.stock.track.catalogLoading">
            Cargando el catálogo…
          </p>
        } @else if (products().length === 0) {
          <p data-testid="track-empty-catalog" i18n="@@admin.stock.track.emptyCatalog">
            El catálogo todavía no tiene productos.
          </p>
        } @else if (trackedIds() !== null && options().length === 0) {
          <p data-testid="track-none" i18n="@@admin.stock.track.none">
            Todos los productos del catálogo ya tienen control de stock.
          </p>
        } @else {
          <app-form-field
            label="Producto"
            i18n-label="@@admin.stock.track.productLabel"
            controlId="stock-track-product"
            [error]="invalid('productId') ? labels.productInvalid : null"
          >
            <p-select
              inputId="stock-track-product"
              [ariaLabel]="labels.product"
              formControlName="productId"
              optionLabel="name"
              optionValue="id"
              [options]="options()"
              [filter]="true"
              filterBy="name"
              [resetFilterOnHide]="true"
              filterPlaceholder="Buscar"
              i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
              placeholder="Elige un producto"
              i18n-placeholder="@@admin.stock.track.product.placeholder"
              [loading]="trackedIds() === null"
              [invalid]="invalid('productId')"
              [pt]="selectAriaInvalid(invalid('productId'))"
              [appendTo]="'body'"
              fluid
            />
          </app-form-field>
        }
        <app-form-field
          label="Avisar cuando queden"
          i18n-label="@@admin.stock.track.threshold"
          controlId="stock-threshold"
          [error]="invalid('lowStockThreshold') ? labels.thresholdInvalid : null"
          [hint]="labels.thresholdHint"
        >
          <p-inputnumber
            inputId="stock-threshold"
            formControlName="lowStockThreshold"
            [useGrouping]="false"
            [invalid]="invalid('lowStockThreshold')"
            [pt]="numberAriaInvalid(invalid('lowStockThreshold'))"
            fluid
          />
        </app-form-field>
      </div>
    </app-form-dialog>
  `,
})
export class StockTrackDialog {
  readonly visible = input.required<boolean>();
  readonly target = input<StockItem | null>(null);
  readonly products = input<readonly Product[]>([]);
  readonly catalogStatus = input<'loading' | 'ready' | 'failed'>('ready');

  readonly saved = output<StockItem>();
  readonly dismissed = output<void>();
  readonly catalogRetried = output<void>();

  private readonly api = inject(StockApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly icons = { retry: IconRefresh };
  protected readonly labels = {
    product: $localize`:@@admin.stock.track.productLabel:Producto`,
    productInvalid: $localize`:@@admin.stock.track.product.invalid:Elige un producto.`,
    thresholdHint: $localize`:@@admin.stock.track.threshold.hint:Unidades disponibles o menos; con 0 solo avisa cuando se agota.`,
    thresholdInvalid: $localize`:@@admin.stock.track.threshold.invalid:Escribe un número entero entre 0 y 1000000.`,
  };
  protected readonly selectAriaInvalid = selectAriaInvalid;
  protected readonly numberAriaInvalid = numberAriaInvalid;
  protected readonly saving = signal(false);
  protected readonly trackedIds = signal<ReadonlySet<string> | null>(null);
  protected readonly trackedFailed = signal(false);

  protected readonly form = new FormGroup({
    productId: new FormControl<string | null>(null, Validators.required),
    lowStockThreshold: new FormControl<number | null>(null, [
      ...wholeNumber(0),
      Validators.max(MAX_THRESHOLD),
    ]),
  });

  private readonly names = computed(() => new Map(this.products().map((item) => [item.id, item.name])));
  protected readonly options = computed(() => {
    const tracked = this.trackedIds();
    return this.products()
      .filter((item) => tracked !== null && !tracked.has(item.id))
      .sort((a, b) => a.name.localeCompare(b.name, 'es'));
  });
  protected readonly header = computed(() =>
    this.target()
      ? $localize`:@@admin.stock.track.title.edit:Umbral de stock bajo`
      : $localize`:@@admin.stock.track.title.create:Controlar un producto`,
  );

  constructor() {
    effect(() => {
      if (!this.visible()) {
        return;
      }
      const target = this.target();
      untracked(() => {
        this.saving.set(false);
        this.form.reset({
          productId: target?.productId ?? null,
          lowStockThreshold: target?.lowStockThreshold ?? null,
        });
        this.trackedIds.set(null);
        if (!target) {
          this.loadTracked();
        }
      });
    });
  }

  protected nameOf(productId: string): string {
    return this.names().get(productId) ?? shortId(productId);
  }

  protected invalid(name: string): boolean {
    const control = this.form.get(name);
    return !!control && control.invalid && control.touched;
  }

  protected loadTracked(): void {
    this.trackedFailed.set(false);
    this.api
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (items) => this.trackedIds.set(new Set(items.map((item) => item.productId))),
        error: (error: unknown) => {
          this.trackedFailed.set(true);
          this.notifier.show(error);
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
    const { productId, lowStockThreshold } = this.form.getRawValue();
    this.saving.set(true);
    this.api
      .track(productId!, { tracked: true, lowStockThreshold: lowStockThreshold ?? 0 })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (item) => {
          this.saving.set(false);
          this.saved.emit(item);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          reportFailure(this.notifier, error, this.form);
        },
      });
  }
}
