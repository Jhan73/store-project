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
import { IconDeviceFloppy, IconRefresh, IconX, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputNumber } from 'primeng/inputnumber';
import { Select } from 'primeng/select';
import { Tooltip } from 'primeng/tooltip';
import type { Product, StockItem } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { numberAriaInvalid, selectAriaInvalid } from '../../../shared/forms/aria-invalid';
import { reportFailure } from '../admin-errors';
import { wholeNumber } from '../admin-validators';
import { shortId } from '../audit/audit-labels';
import { StockApi } from './stock-api';

const MAX_THRESHOLD = 1_000_000;

// Starts tracking a product (picked from the untracked ones) or edits the threshold of a tracked one.
@Component({
  selector: 'app-stock-track-dialog',
  imports: [ReactiveFormsModule, ButtonDirective, Dialog, InputNumber, Select, TablerIconComponent, Tooltip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
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
    <p-dialog
      [header]="header()"
      [modal]="true"
      [draggable]="false"
      [style]="{ width: 'min(30rem, calc(100vw - 1rem))' }"
      [visible]="visible()"
      (visibleChange)="visibleChange($event)"
    >
      <p class="muted" i18n="@@admin.stock.track.help">
        Al controlar un producto sin unidades disponibles, deja de estar disponible en el menú. Marcarlo
        como agotado a mano sigue siendo independiente.
      </p>
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
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
        } @else if (trackedIds() !== null && options().length === 0) {
          <p data-testid="track-none" i18n="@@admin.stock.track.none">
            Todos los productos del catálogo ya tienen control de stock.
          </p>
        } @else {
          <div class="field">
            <label for="stock-track-product" id="stock-track-product-label" i18n="@@admin.stock.track.productLabel"
              >Producto</label
            >
            <p-select
              inputId="stock-track-product"
              [ariaLabelledBy]="'stock-track-product-label'"
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
            @if (invalid('productId')) {
              <small class="error" i18n="@@admin.stock.track.product.invalid">Elige un producto.</small>
            }
          </div>
        }
        <div class="field">
          <label for="stock-threshold" i18n="@@admin.stock.track.threshold">Avisar cuando queden</label>
          <p-inputnumber
            inputId="stock-threshold"
            formControlName="lowStockThreshold"
            [useGrouping]="false"
            [min]="0"
            [max]="maxThreshold"
            [invalid]="invalid('lowStockThreshold')"
            [pt]="numberAriaInvalid(invalid('lowStockThreshold'))"
          />
          <small class="muted" i18n="@@admin.stock.track.threshold.hint">
            Unidades disponibles o menos; con 0 solo avisa cuando se agota.
          </small>
          @if (invalid('lowStockThreshold')) {
            <small class="error" i18n="@@admin.stock.track.threshold.invalid">
              Escribe un número entero entre 0 y 1000000.
            </small>
          }
        </div>
      </form>
      <ng-template #footer>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="track-cancel"
          (click)="dismissed.emit()"
          pTooltip="Cerrar sin guardar"
          i18n-pTooltip="@@admin.stock.track.cancel.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.cancel" aria-hidden="true" />
          <span i18n="@@admin.stock.track.cancel">Cancelar</span>
        </button>
        <button
          pButton
          type="button"
          data-testid="track-save"
          [loading]="saving()"
          (click)="submit()"
          [pTooltip]="target() ? tips.edit : tips.create"
          tooltipPosition="top"
        >
          @if (!saving()) {
            <tabler-icon [icon]="icons.save" aria-hidden="true" />
          }
          <span>{{ target() ? labels.edit : labels.create }}</span>
        </button>
      </ng-template>
    </p-dialog>
  `,
})
export class StockTrackDialog {
  readonly visible = input.required<boolean>();
  readonly target = input<StockItem | null>(null);
  readonly products = input<readonly Product[]>([]);

  readonly saved = output<StockItem>();
  readonly dismissed = output<void>();

  private readonly api = inject(StockApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly icons = { save: IconDeviceFloppy, cancel: IconX, retry: IconRefresh };
  protected readonly tips = {
    create: $localize`:@@admin.stock.track.create.tooltip:Empezar a controlar el stock de este producto`,
    edit: $localize`:@@admin.stock.track.edit.tooltip:Guardar el nuevo umbral de stock bajo`,
  };
  protected readonly labels = {
    create: $localize`:@@admin.stock.track.create:Controlar stock`,
    edit: $localize`:@@admin.stock.track.edit:Guardar umbral`,
  };
  protected readonly maxThreshold = MAX_THRESHOLD;
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

  protected visibleChange(visible: boolean): void {
    if (!visible) {
      this.dismissed.emit();
    }
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
