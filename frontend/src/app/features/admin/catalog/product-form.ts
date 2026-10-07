import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MessageService } from 'primeng/api';
import {
  IconArrowDown,
  IconArrowLeft,
  IconArrowUp,
  IconDeviceFloppy,
  IconPlus,
  IconTrash,
  IconUpload,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Checkbox } from 'primeng/checkbox';
import { FileSelectEvent, FileUpload } from 'primeng/fileupload';
import { InputNumber } from 'primeng/inputnumber';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import { selectAriaInvalid } from '../../../shared/forms/aria-invalid';
import { forkJoin, of } from 'rxjs';
import type {
  Allergen,
  Category,
  ModifierGroup,
  Product,
  SaveProductRequest,
} from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { parseAmount } from '../../../core/money/money';
import { AllergenPicker } from './allergen-picker';
import { CatalogApi } from './catalog-api';
import { isStale, reportFailure } from '../admin-errors';

const IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/webp'];
const MAX_IMAGE_BYTES = 2 * 1024 * 1024;

function positiveAmount(control: AbstractControl): ValidationErrors | null {
  const amount = parseAmount(String(control.value ?? ''));
  return amount === null || amount === '0.00' ? { amount: true } : null;
}

@Component({
  selector: 'app-product-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    ButtonDirective,
    Checkbox,
    FileUpload,
    InputNumber,
    InputText,
    Select,
    Textarea,
    AllergenPicker,
    TablerIconComponent,
    Tooltip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './catalog.scss',
  template: `
    <div class="toolbar">
      <h2>
        @if (productId) {
          <ng-container i18n="@@admin.catalog.productForm.editTitle">Editar producto</ng-container>
        } @else {
          <ng-container i18n="@@admin.catalog.productForm.newTitle">Nuevo producto</ng-container>
        }
      </h2>
      <a
        pButton
        severity="secondary"
        routerLink="/admin/catalog/products"
        pTooltip="Volver a la lista de productos"
        i18n-pTooltip="@@admin.catalog.productForm.back.tooltip"
        tooltipPosition="top"
      >
        <tabler-icon [icon]="icons.back" aria-hidden="true" />
        <span i18n="@@admin.catalog.back">Volver a la lista</span>
      </a>
    </div>

    @if (ready()) {
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <div class="field wide">
          <label for="product-name" i18n="@@admin.catalog.productForm.name">Nombre</label>
          <input
            pInputText
            id="product-name"
            formControlName="name"
            autocomplete="off"
            [attr.aria-invalid]="invalid(form.controls.name) ? 'true' : null"
            fluid
          />
          @if (invalid(form.controls.name)) {
            <small class="error" i18n="@@admin.catalog.productForm.nameInvalid">Escribe un nombre válido.</small>
          }
        </div>

        <div class="field wide">
          <label for="product-description" i18n="@@admin.catalog.productForm.description">Descripción (opcional)</label>
          <textarea
            pTextarea
            id="product-description"
            formControlName="description"
            rows="2"
            fluid
          ></textarea>
        </div>

        <div class="field">
          <label
            for="product-category"
            id="product-category-label"
            i18n="@@admin.catalog.productForm.category"
            >Categoría</label
          >
          <p-select
            inputId="product-category"
            [ariaLabelledBy]="'product-category-label'"
            formControlName="categoryId"
            placeholder="Elige una categoría"
            i18n-placeholder="@@admin.catalog.productForm.categoryPlaceholder"
            optionLabel="name"
            optionValue="id"
            [options]="categoryOptions()"
            [filter]="true"
            filterBy="name"
            [resetFilterOnHide]="true"
            filterPlaceholder="Buscar"
            i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
            [invalid]="invalid(form.controls.categoryId)"
            [pt]="selectAriaInvalid(invalid(form.controls.categoryId))"
          />
          @if (invalid(form.controls.categoryId)) {
            <small class="error" i18n="@@admin.catalog.productForm.categoryInvalid">Elige una categoría.</small>
          }
        </div>

        <div class="field">
          <label for="product-price" i18n="@@admin.catalog.productForm.price">Precio ({{ currency() }})</label>
          <input
            pInputText
            id="product-price"
            formControlName="price"
            [attr.inputmode]="'decimal'"
            autocomplete="off"
            [attr.aria-invalid]="invalid(form.controls.price) ? 'true' : null"
          />
          @if (invalid(form.controls.price)) {
            <small class="error" i18n="@@admin.catalog.productForm.priceInvalid">Escribe un monto mayor que cero, como 9.50.</small>
          }
        </div>

        <div class="field">
          <label for="product-order" i18n="@@admin.catalog.productForm.order">Orden de aparición</label>
          <p-inputnumber
            inputId="product-order"
            formControlName="displayOrder"
            [useGrouping]="false"
          />
        </div>

        <span class="check">
          <p-checkbox inputId="product-pinned" formControlName="quickSalePinned" [binary]="true" />
          <label for="product-pinned" i18n="@@admin.catalog.productForm.pinned">Fijar en venta rápida</label>
        </span>

        <app-allergen-picker
          [allergens]="allergens()"
          [selected]="form.controls.allergens.value"
          (selectedChange)="form.controls.allergens.setValue($event)"
          i18n="@@admin.catalog.productForm.allergens"
          >Alérgenos del producto</app-allergen-picker
        >

        <fieldset class="groups">
          <legend i18n="@@admin.catalog.productForm.groups">Grupos de modificadores, en orden</legend>
          <ol>
            @for (id of groupIds(); track id; let i = $index; let last = $last) {
              <li [attr.data-testid]="'group-' + i" class="actions">
                <span class="group-name">{{ groupName(id) }}</span>
                <button
                  pButton
                  type="button"
                  severity="secondary"
                  [size]="'small'"
                  [disabled]="i === 0"
                  [attr.data-testid]="'move-up-' + i"
                  (click)="move(i, -1)"
                  pTooltip="Subir este grupo una posición"
                  i18n-pTooltip="@@admin.catalog.productForm.moveUp.tooltip"
                  tooltipPosition="top"
                >
                  <tabler-icon [icon]="icons.up" aria-hidden="true" />
                  <span i18n="@@admin.catalog.productForm.moveUp">Subir</span>
                </button>
                <button
                  pButton
                  type="button"
                  severity="secondary"
                  [size]="'small'"
                  [disabled]="last"
                  [attr.data-testid]="'move-down-' + i"
                  (click)="move(i, 1)"
                  pTooltip="Bajar este grupo una posición"
                  i18n-pTooltip="@@admin.catalog.productForm.moveDown.tooltip"
                  tooltipPosition="top"
                >
                  <tabler-icon [icon]="icons.down" aria-hidden="true" />
                  <span i18n="@@admin.catalog.productForm.moveDown">Bajar</span>
                </button>
                <button
                  pButton
                  type="button"
                  severity="secondary"
                  [outlined]="true"
                  [size]="'small'"
                  [attr.data-testid]="'remove-group-' + i"
                  (click)="detach(i)"
                  pTooltip="Quitar este grupo del producto"
                  i18n-pTooltip="@@admin.catalog.productForm.removeGroup.tooltip"
                  tooltipPosition="top"
                >
                  <tabler-icon [icon]="icons.remove" aria-hidden="true" />
                  <span i18n="@@admin.catalog.productForm.removeGroup">Quitar</span>
                </button>
              </li>
            }
          </ol>
          <div class="actions">
            <label
              for="product-group-add"
              id="product-group-add-label"
              class="sr-only"
              i18n="@@admin.catalog.productForm.addGroupLabel"
              >Grupo a agregar</label
            >
            <p-select
              inputId="product-group-add"
              [ariaLabelledBy]="'product-group-add-label'"
              placeholder="Elige un grupo"
              i18n-placeholder="@@admin.catalog.productForm.groupPlaceholder"
              optionLabel="name"
              optionValue="id"
              [options]="detached()"
              [filter]="true"
              filterBy="name"
              [resetFilterOnHide]="true"
              filterPlaceholder="Buscar"
              i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
              [formControl]="groupPick"
            />
            <button
              pButton
              type="button"
              severity="secondary"
              data-testid="add-group"
              (click)="attach()"
              pTooltip="Agregar el grupo elegido al producto"
              i18n-pTooltip="@@admin.catalog.productForm.addGroup.tooltip"
              tooltipPosition="top"
            >
              <tabler-icon [icon]="icons.add" aria-hidden="true" />
              <span i18n="@@admin.catalog.productForm.addGroup">Agregar grupo</span>
            </button>
          </div>
        </fieldset>

        <div class="actions">
          <button
            pButton
            type="submit"
            [loading]="saving()"
            pTooltip="Guardar los datos del producto"
            i18n-pTooltip="@@admin.catalog.productForm.save.tooltip"
            tooltipPosition="top"
          >
            @if (!saving()) {
              <tabler-icon [icon]="icons.save" aria-hidden="true" />
            }
            <span i18n="@@admin.catalog.productForm.save">Guardar producto</span>
          </button>
        </div>
      </form>

      <section class="image">
        <h3 i18n="@@admin.catalog.productForm.imageTitle">Imagen</h3>
        @if (product(); as saved) {
          @if (saved.imageUrl) {
            <img [src]="saved.imageUrl" [alt]="saved.name" width="96" height="96" />
            <button
              pButton
              type="button"
              severity="secondary"
              [outlined]="true"
              data-testid="remove-image"
              (click)="removeImage()"
              pTooltip="Quitar la imagen del producto"
              i18n-pTooltip="@@admin.catalog.productForm.removeImage.tooltip"
              tooltipPosition="top"
            >
              <tabler-icon [icon]="icons.remove" aria-hidden="true" />
              <span i18n="@@admin.catalog.productForm.removeImage">Quitar imagen</span>
            </button>
          }
          <div class="field">
            <p-fileupload
              [mode]="'basic'"
              [auto]="true"
              [customUpload]="true"
              chooseLabel="Elegir una imagen nueva"
              i18n-chooseLabel="@@admin.catalog.productForm.imagePick"
              [accept]="imageAccept"
              [maxFileSize]="maxImageBytes"
              [invalidFileTypeMessageSummary]="imageInvalid"
              invalidFileTypeMessageDetail=""
              [invalidFileSizeMessageSummary]="imageInvalid"
              invalidFileSizeMessageDetail=""
              (onSelect)="upload($event)"
              pTooltip="Subir una imagen para el producto"
              i18n-pTooltip="@@admin.catalog.productForm.imagePick.tooltip"
              tooltipPosition="top"
            >
              <ng-template #chooseicon>
                <tabler-icon [icon]="icons.upload" aria-hidden="true" />
              </ng-template>
            </p-fileupload>
            <small class="muted" i18n="@@admin.catalog.productForm.imageHint">PNG, JPG o WebP, de hasta 2 MB.</small>
          </div>
        } @else {
          <p class="muted" i18n="@@admin.catalog.productForm.imageLater">
            Podrás subir la imagen después de guardar el producto.
          </p>
        }
      </section>
    }
  `,
})
export class ProductForm {
  protected readonly icons = {
    back: IconArrowLeft,
    up: IconArrowUp,
    down: IconArrowDown,
    remove: IconTrash,
    add: IconPlus,
    save: IconDeviceFloppy,
    upload: IconUpload,
  };
  private readonly api = inject(CatalogApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly messages = inject(MessageService);
  private readonly router = inject(Router);

  protected readonly productId = inject(ActivatedRoute).snapshot.paramMap.get('id');
  protected readonly ready = signal(false);
  protected readonly selectAriaInvalid = selectAriaInvalid;
  protected readonly saving = signal(false);
  protected readonly imageAccept = IMAGE_TYPES.join(',');
  protected readonly maxImageBytes = MAX_IMAGE_BYTES;
  protected readonly imageInvalid = $localize`:@@admin.catalog.productForm.imageInvalid:La imagen debe ser PNG, JPG o WebP y pesar como máximo 2 MB.`;
  private readonly uploader = viewChild(FileUpload);
  protected readonly currency = signal('');
  protected readonly allergens = signal<readonly Allergen[]>([]);
  protected readonly categories = signal<readonly Category[]>([]);
  protected readonly groups = signal<readonly ModifierGroup[]>([]);
  protected readonly groupIds = signal<readonly string[]>([]);
  protected readonly product = signal<Product | null>(null);
  protected readonly categoryOptions = computed(() => [...this.categories()]);
  protected readonly groupPick = new FormControl<string | null>(null);
  protected readonly detached = computed(() =>
    this.groups().filter((group) => !this.groupIds().includes(group.id)),
  );
  protected readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/)],
    }),
    description: new FormControl('', { nonNullable: true }),
    categoryId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    price: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, positiveAmount],
    }),
    displayOrder: new FormControl<number | null>(0, [Validators.required, Validators.min(0)]),
    quickSalePinned: new FormControl(false, { nonNullable: true }),
    allergens: new FormControl<readonly Allergen[]>([], { nonNullable: true }),
  });

  constructor() {
    forkJoin({
      currency: this.api.currency(),
      allergens: this.api.allergens(),
      categories: this.api.categories(),
      groups: this.api.modifierGroups(),
      product: this.productId ? this.api.product(this.productId) : of(null),
    }).subscribe({
      next: ({ currency, allergens, categories, groups, product }) => {
        this.currency.set(currency);
        this.allergens.set(allergens);
        this.categories.set(categories);
        this.groups.set(groups);
        if (product) {
          this.apply(product);
        }
        this.ready.set(true);
      },
      error: (error: unknown) => this.notifier.show(error),
    });
  }

  protected invalid(control: AbstractControl): boolean {
    return control.invalid && control.touched;
  }

  protected groupName(id: string): string {
    return this.groups().find((group) => group.id === id)?.name ?? '';
  }

  protected attach(): void {
    const id = this.groupPick.value;
    if (id && !this.groupIds().includes(id)) {
      this.groupIds.update((ids) => [...ids, id]);
    }
    this.groupPick.setValue(null);
  }

  protected detach(index: number): void {
    this.groupIds.update((ids) => ids.filter((_, position) => position !== index));
  }

  protected move(index: number, step: -1 | 1): void {
    this.groupIds.update((ids) => {
      const target = index + step;
      if (target < 0 || target >= ids.length) {
        return ids;
      }
      const next = [...ids];
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  }

  protected submit(): void {
    if (this.saving()) {
      return;
    }
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      return;
    }
    const body = this.requestBody();
    const current = this.product();
    const request = current
      ? this.api.changeProduct(current.id, current.etag, body)
      : this.api.createProduct(body);
    this.saving.set(true);
    request.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.messages.add({
          severity: 'success',
          summary: $localize`:@@admin.catalog.saved:Cambios guardados.`,
        });
        void this.router.navigate(
          current ? ['/admin/catalog/products'] : ['/admin/catalog/products', saved.id],
        );
      },
      error: (error: unknown) => {
        this.saving.set(false);
        reportFailure(this.notifier, error, this.form);
        if (isStale(error)) {
          this.reload();
        }
      },
    });
  }

  // The uploader keeps only the files that pass its type and size checks and shows its message for the rest.
  protected upload(event: FileSelectEvent): void {
    const file = event.currentFiles[0];
    const current = this.product();
    if (!file || !current) {
      return;
    }
    this.api.replaceProductImage(current.id, current.etag, file).subscribe({
      next: (saved) => this.product.set(saved),
      error: (error: unknown) => this.failImage(error),
    });
  }

  protected removeImage(): void {
    const current = this.product();
    if (!current) {
      return;
    }
    this.uploader()?.clear();
    this.api.removeProductImage(current.id, current.etag).subscribe({
      next: (saved) => this.product.set(saved),
      error: (error: unknown) => this.failImage(error),
    });
  }

  private failImage(error: unknown): void {
    this.notifier.show(error);
    if (isStale(error)) {
      this.reload();
    }
  }

  private requestBody(): SaveProductRequest {
    const value = this.form.getRawValue();
    return {
      name: value.name.trim(),
      description: value.description.trim() || null,
      categoryId: value.categoryId,
      price: { amount: parseAmount(value.price) ?? '0.00', currency: this.currency() },
      displayOrder: value.displayOrder ?? 0,
      quickSalePinned: value.quickSalePinned,
      allergens: [...value.allergens],
      modifierGroupIds: [...this.groupIds()],
    };
  }

  private reload(): void {
    if (!this.productId) {
      return;
    }
    this.api.product(this.productId).subscribe({
      next: (product) => this.apply(product),
      error: (error: unknown) => this.notifier.show(error),
    });
  }

  private apply(product: Product): void {
    this.product.set(product);
    this.groupIds.set(product.modifierGroupIds);
    this.form.setValue({
      name: product.name,
      description: product.description ?? '',
      categoryId: product.categoryId,
      price: product.price.amount,
      displayOrder: product.displayOrder,
      quickSalePinned: product.quickSalePinned,
      allergens: product.allergens,
    });
  }
}
