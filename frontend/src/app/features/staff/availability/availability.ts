import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormsModule, NgModel } from '@angular/forms';
import { IconRefresh, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { Tooltip } from 'primeng/tooltip';
import { AvailabilityStore, ItemKind } from './availability-store';

@Component({
  selector: 'app-availability',
  imports: [FormsModule, ButtonDirective, ToggleSwitch, TablerIconComponent, Tooltip],
  providers: [AvailabilityStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './availability.scss',
  template: `
    <h1 i18n="@@staff.availability.title">Disponibilidad</h1>

    @if (store.failed()) {
      <div class="offline" role="status" data-testid="offline-banner">
        <ng-container i18n="@@staff.availability.failed"
          >No se pudo actualizar la disponibilidad. Los cambios están deshabilitados.</ng-container
        >
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="retry"
          (click)="store.load()"
          pTooltip="Volver a cargar la disponibilidad"
          i18n-pTooltip="@@staff.availability.retry.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.retry" aria-hidden="true" />
          <span i18n="@@staff.availability.retry">Reintentar</span>
        </button>
      </div>
    } @else if (!store.connected()) {
      <div class="offline" role="status" data-testid="offline-banner">
        <ng-container i18n="@@staff.availability.offline"
          >Sin conexión en tiempo real. Los cambios se habilitan al reconectar.</ng-container
        >
      </div>
    } @else if (!store.enabled()) {
      <div class="offline" role="status" data-testid="offline-banner">
        <ng-container i18n="@@staff.availability.updating"
          >Actualizando la disponibilidad. Los cambios se habilitan en unos segundos.</ng-container
        >
      </div>
    }

    @if (store.menu(); as menu) {
      <section>
        <h2 i18n="@@staff.availability.products">Productos</h2>
        @for (category of menu.categories; track category.id) {
          <h3>{{ category.name }}</h3>
          <ul>
            @for (product of category.products; track product.id) {
              <li>
                <div class="row">
                  <p-toggleswitch
                    #toggle="ngModel"
                    [inputId]="'product-' + product.id"
                    [ariaLabelledBy]="'product-name-' + product.id"
                    [ngModel]="product.available"
                    [disabled]="!store.enabled() || store.pending.has(product.id)"
                    [attr.data-testid]="'product-' + product.id"
                    (onChange)="change('product', product.id, $event.checked, toggle)"
                  />
                  <span class="name" [id]="'product-name-' + product.id">{{ product.name }}</span>
                  <span class="state" [attr.data-testid]="'state-' + product.id">
                    @if (product.available) {
                      <ng-container i18n="@@staff.availability.available">Disponible</ng-container>
                    } @else {
                      <ng-container i18n="@@staff.availability.unavailable">Agotado</ng-container>
                    }
                  </span>
                </div>
              </li>
            } @empty {
              <li class="muted" i18n="@@staff.availability.emptyCategory">
                Esta categoría no tiene productos.
              </li>
            }
          </ul>
        } @empty {
          <p class="muted" i18n="@@staff.availability.empty">Todavía no hay productos.</p>
        }
      </section>

      @if (store.groups().length > 0) {
        <section>
          <h2 i18n="@@staff.availability.options">Opciones</h2>
          @for (group of store.groups(); track group.id) {
            <h3>{{ group.name }}</h3>
            <ul>
              @for (option of group.options; track option.id) {
                <li>
                  <div class="row">
                    <p-toggleswitch
                      #toggle="ngModel"
                      [inputId]="'option-' + option.id"
                      [ariaLabelledBy]="'option-name-' + option.id"
                      [ngModel]="option.available"
                      [disabled]="!store.enabled() || store.pending.has(option.id)"
                      [attr.data-testid]="'option-' + option.id"
                      (onChange)="change('option', option.id, $event.checked, toggle)"
                    />
                    <span class="name" [id]="'option-name-' + option.id">{{ option.name }}</span>
                    <span class="state" [attr.data-testid]="'state-' + option.id">
                      @if (option.available) {
                        <ng-container i18n="@@staff.availability.available"
                          >Disponible</ng-container
                        >
                      } @else {
                        <ng-container i18n="@@staff.availability.unavailable">Agotado</ng-container>
                      }
                    </span>
                  </div>
                </li>
              }
            </ul>
          }
        </section>
      }
    } @else if (!store.failed()) {
      <p class="muted" i18n="@@staff.availability.loading">Cargando…</p>
    }
  `,
})
export class Availability {
  protected readonly icons = { retry: IconRefresh };
  protected readonly store = inject(AvailabilityStore);

  protected change(kind: ItemKind, id: string, available: boolean, control: NgModel): void {
    this.store.set(kind, id, available, () => {
      // The switch flipped on its own; after a refusal it must show the value the screen holds now.
      control.control.setValue(this.store.availableOf(id) ?? control.value);
    });
  }
}
