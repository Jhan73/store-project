import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  IconDeviceFloppy,
  IconPencil,
  IconPlayerPause,
  IconPlayerPlay,
  IconPlus,
  IconX,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { InputNumber } from 'primeng/inputnumber';
import { InputText } from 'primeng/inputtext';
import { Tooltip } from 'primeng/tooltip';
import { numberAriaInvalid } from '../../../shared/forms/aria-invalid';
import type { AdminTable } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { isStale, reportFailure } from '../admin-errors';
import { wholeNumber } from '../admin-validators';
import { PendingIds } from '../../../shared/state/pending-ids';
import { TablesApi } from './tables-api';

const MAX_LENGTH = 60;

function byDisplayOrder(a: AdminTable, b: AdminTable): number {
  return a.displayOrder - b.displayOrder || a.name.localeCompare(b.name);
}

@Component({
  selector: 'app-table-list',
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    InputNumber,
    InputText,
    TablerIconComponent,
    Tooltip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../admin-section.scss',
  template: `
    <h1 i18n="@@admin.tables.title">Mesas</h1>

    @if (tables().length === 0) {
      <p class="muted" i18n="@@admin.tables.empty">Todavía no hay mesas.</p>
    } @else {
      <table>
        <thead>
          <tr>
            <th i18n="@@admin.tables.name">Nombre</th>
            <th i18n="@@admin.tables.area">Zona</th>
            <th i18n="@@admin.tables.order">Orden</th>
            <th i18n="@@admin.tables.state">Estado</th>
            <th><span class="sr-only" i18n="@@admin.tables.actions">Acciones</span></th>
          </tr>
        </thead>
        <tbody>
          @for (table of tables(); track table.id) {
            <tr>
              <td>{{ table.name }}</td>
              <td>{{ table.area }}</td>
              <td>{{ table.displayOrder }}</td>
              <td>
                @if (table.active) {
                  <span i18n="@@admin.tables.active">Activa</span>
                } @else {
                  <span class="muted" i18n="@@admin.tables.inactive">Inactiva</span>
                }
              </td>
              <td class="actions">
                <button
                  pButton
                  type="button"
                  severity="secondary"
                  [size]="'small'"
                  [disabled]="saving()"
                [attr.data-testid]="'edit-' + table.id"
                  (click)="edit(table)"
                  pTooltip="Editar los datos de esta mesa"
                  i18n-pTooltip="@@admin.tables.edit.tooltip"
                  tooltipPosition="top"
                >
                  <tabler-icon [icon]="icons.edit" aria-hidden="true" />
                  <span i18n="@@admin.tables.edit">Editar</span>
                </button>
                <button
                  pButton
                  type="button"
                  severity="secondary"
                  [size]="'small'"
                  [outlined]="true"
                  [disabled]="pending.has(table.id)"
                  [attr.data-testid]="'toggle-' + table.id"
                  (click)="toggle(table)"
                  [pTooltip]="table.active ? tips.deactivate : tips.reactivate"
                  tooltipPosition="top"
                >
                  @if (table.active) {
                    <tabler-icon [icon]="icons.deactivate" aria-hidden="true" />
                    <ng-container i18n="@@admin.tables.deactivate">Desactivar</ng-container>
                  } @else {
                    <tabler-icon [icon]="icons.reactivate" aria-hidden="true" />
                    <ng-container i18n="@@admin.tables.reactivate">Reactivar</ng-container>
                  }
                </button>
              </td>
            </tr>
          }
        </tbody>
      </table>
    }

    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <div class="field wide">
        <label for="table-name" i18n="@@admin.tables.nameLabel">Nombre de la mesa</label>
        <input
          pInputText
          id="table-name"
          formControlName="name"
          autocomplete="off"
          [attr.aria-invalid]="invalid('name') ? 'true' : null"
          fluid
        />
        @if (invalid('name')) {
          <small class="error" i18n="@@admin.tables.nameInvalid">Escribe un nombre de hasta 60 caracteres.</small>
        }
      </div>
      <div class="field wide">
        <label for="table-area" i18n="@@admin.tables.areaLabel">Zona (opcional)</label>
        <input
          pInputText
          id="table-area"
          formControlName="area"
          autocomplete="off"
          [attr.aria-invalid]="invalid('area') ? 'true' : null"
          fluid
        />
        @if (invalid('area')) {
          <small class="error" i18n="@@admin.tables.areaInvalid">La zona admite hasta 60 caracteres.</small>
        }
      </div>
      <div class="field">
        <label for="table-order" i18n="@@admin.tables.orderLabel">Orden de aparición</label>
        <p-inputnumber
          inputId="table-order"
          formControlName="displayOrder"
          [useGrouping]="false"
          [invalid]="invalid('displayOrder')"
          [pt]="numberAriaInvalid(invalid('displayOrder'))"
        />
        @if (invalid('displayOrder')) {
          <small class="error" i18n="@@admin.tables.orderInvalid">Escribe un número entero.</small>
        }
      </div>
      <div class="actions">
        <button
          pButton
          type="submit"
          [loading]="saving()"
          [pTooltip]="editing() ? tips.save : tips.create"
          tooltipPosition="top"
        >
          @if (editing()) {
            @if (!saving()) {
              <tabler-icon [icon]="icons.save" aria-hidden="true" />
            }
            <ng-container i18n="@@admin.tables.save">Guardar cambios</ng-container>
          } @else {
            @if (!saving()) {
              <tabler-icon [icon]="icons.create" aria-hidden="true" />
            }
            <ng-container i18n="@@admin.tables.create">Agregar mesa</ng-container>
          }
        </button>
        @if (editing()) {
          <button
            pButton
            type="button"
            severity="secondary"
            (click)="cancel()"
            pTooltip="Descartar los cambios y cerrar el formulario"
            i18n-pTooltip="@@admin.tables.cancel.tooltip"
            tooltipPosition="top"
          >
            <tabler-icon [icon]="icons.cancel" aria-hidden="true" />
            <span i18n="@@admin.tables.cancel">Cancelar</span>
          </button>
        }
      </div>
    </form>
  `,
})
export class TableList {
  protected readonly icons = {
    edit: IconPencil,
    deactivate: IconPlayerPause,
    reactivate: IconPlayerPlay,
    save: IconDeviceFloppy,
    create: IconPlus,
    cancel: IconX,
  };
  protected readonly tips = {
    deactivate: $localize`:@@admin.tables.deactivate.tooltip:Ocultar esta mesa sin borrarla`,
    reactivate: $localize`:@@admin.tables.reactivate.tooltip:Volver a activar esta mesa`,
    save: $localize`:@@admin.tables.save.tooltip:Guardar los cambios de la mesa`,
    create: $localize`:@@admin.tables.create.tooltip:Agregar una mesa nueva`,
  };
  private readonly api = inject(TablesApi);
  private readonly notifier = inject(ErrorNotifier);

  protected readonly tables = signal<readonly AdminTable[]>([]);
  protected readonly editing = signal<AdminTable | null>(null);
  protected readonly saving = signal(false);
  protected readonly numberAriaInvalid = numberAriaInvalid;
  protected readonly pending = new PendingIds();
  private lastLoad = 0;
  private reconcile = false;
  protected readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/), Validators.maxLength(MAX_LENGTH)],
    }),
    area: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(MAX_LENGTH)] }),
    displayOrder: new FormControl<number | null>(0, wholeNumber(0)),
  });

  constructor() {
    this.load();
  }

  protected invalid(name: string): boolean {
    const control = this.form.get(name);
    return !!control && control.invalid && control.touched;
  }

  protected edit(table: AdminTable): void {
    this.editing.set(table);
    this.form.setValue({
      name: table.name,
      area: table.area ?? '',
      displayOrder: table.displayOrder,
    });
  }

  protected cancel(): void {
    this.editing.set(null);
    this.form.reset({ name: '', area: '', displayOrder: 0 });
  }

  protected toggle(table: AdminTable): void {
    if (this.pending.has(table.id)) {
      return;
    }
    this.pending.add(table.id);
    const request = table.active
      ? this.api.deactivate(table.id, table.etag)
      : this.api.reactivate(table.id, table.etag);
    request.subscribe({
      next: (saved) => {
        this.pending.delete(table.id);
        this.replace(saved);
      },
      error: (error: unknown) => {
        this.pending.delete(table.id);
        this.fail(error, undefined, table.id);
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
    const raw = this.form.getRawValue();
    const body = {
      name: raw.name.trim(),
      area: raw.area.trim() === '' ? null : raw.area.trim(),
      displayOrder: raw.displayOrder ?? 0,
    };
    const target = this.editing();
    const request = target
      ? this.api.change(target.id, target.etag, body)
      : this.api.create(body);
    this.saving.set(true);
    request.subscribe({
      next: (saved) => {
        if (target) {
          this.replace(saved);
        } else {
          this.tables.update((list) => [...list, saved].sort(byDisplayOrder));
        }
        this.cancel();
        this.saving.set(false);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.fail(error, this.form, target?.id);
      },
    });
  }

  private replace(saved: AdminTable): void {
    this.tables.update((list) =>
      list.map((item) => (item.id === saved.id ? saved : item)).sort(byDisplayOrder),
    );
    if (this.editing()?.id === saved.id) {
      this.editing.set(saved);
    }
  }

  private fail(error: unknown, form?: FormGroup, id?: string): void {
    reportFailure(this.notifier, error, form);
    if (isStale(error)) {
      this.reconcile ||= id !== undefined && id === this.editing()?.id;
      this.load();
    }
  }

  private load(): void {
    const load = ++this.lastLoad;
    this.api.tables().subscribe({
      next: (list) => {
        if (load !== this.lastLoad) {
          return;
        }
        this.tables.set(list);
        const reconcile = this.reconcile;
        this.reconcile = false;
        const current = this.editing();
        if (!reconcile || !current) {
          return;
        }
        const fresh = list.find((item) => item.id === current.id);
        if (fresh) {
          this.edit(fresh);
        } else {
          this.cancel();
        }
      },
      error: (error: unknown) => this.notifier.show(error),
    });
  }
}
