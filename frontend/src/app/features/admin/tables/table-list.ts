import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  IconPencil,
  IconPlayerPause,
  IconPlayerPlay,
  IconPlus,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { InputNumber } from 'primeng/inputnumber';
import { InputText } from 'primeng/inputtext';
import { Tooltip } from 'primeng/tooltip';
import { tap } from 'rxjs';
import { numberAriaInvalid } from '../../../shared/forms/aria-invalid';
import type { AdminTable } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { isStale, reportFailure } from '../admin-errors';
import { wholeNumber } from '../admin-validators';
import { PendingIds } from '../../../shared/state/pending-ids';
import { UndoNotice } from '../../../shared/state/undo-notice';
import { UndoableChanges } from '../../../shared/state/undoable-changes';
import { DataColumn, DataTable } from '../../../shared/ui/data-table/data-table';
import { FormDialog } from '../../../shared/ui/form-dialog/form-dialog';
import { FormField } from '../../../shared/ui/form-field/form-field';
import { StatusTag } from '../../../shared/ui/status-tag/status-tag';
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
    DataTable,
    FormDialog,
    FormField,
    StatusTag,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../admin-section.scss',
  template: `
    <div class="toolbar">
      <h1 i18n="@@admin.tables.title">Mesas</h1>
      <button
        pButton
        type="button"
        data-testid="add-table"
        [disabled]="saving()"
        (click)="add()"
        pTooltip="Agregar una mesa nueva"
        i18n-pTooltip="@@admin.tables.create.tooltip"
        tooltipPosition="top"
      >
        <tabler-icon [icon]="icons.create" aria-hidden="true" />
        <span i18n="@@admin.tables.create">Agregar mesa</span>
      </button>
    </div>

    <app-data-table
      label="Listado de mesas"
      i18n-label="@@admin.tables.tableScroll"
      emptyText="Todavía no hay mesas."
      i18n-emptyText="@@admin.tables.empty"
      [value]="rowsView()"
      [columns]="columns"
    >
      <ng-template #row let-table>
        <td>{{ table.name }}</td>
        <td>{{ table.area }}</td>
        <td>{{ table.displayOrder }}</td>
        <td>
          <app-status-tag
            [value]="table.stateLabel"
            [severity]="table.shownActive ? 'success' : 'secondary'"
          />
        </td>
        <td class="actions">
          <div class="row-actions">
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
              [disabled]="pending.has(table.id) || changes.has(table.id)"
              [attr.data-testid]="'toggle-' + table.id"
              (click)="toggle(table)"
              [pTooltip]="table.shownActive ? tips.deactivate : tips.reactivate"
              tooltipPosition="top"
            >
              @if (table.shownActive) {
                <tabler-icon [icon]="icons.deactivate" aria-hidden="true" />
                <ng-container i18n="@@admin.tables.deactivate">Desactivar</ng-container>
              } @else {
                <tabler-icon [icon]="icons.reactivate" aria-hidden="true" />
                <ng-container i18n="@@admin.tables.reactivate">Reactivar</ng-container>
              }
            </button>
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
          label="Nombre de la mesa"
          i18n-label="@@admin.tables.nameLabel"
          controlId="table-name"
          [error]="invalid('name') ? labels.nameInvalid : null"
        >
          <input
            pInputText
            id="table-name"
            formControlName="name"
            autocomplete="off"
            [attr.aria-invalid]="invalid('name') ? 'true' : null"
            fluid
          />
        </app-form-field>
        <app-form-field
          label="Zona (opcional)"
          i18n-label="@@admin.tables.areaLabel"
          controlId="table-area"
          [error]="invalid('area') ? labels.areaInvalid : null"
        >
          <input
            pInputText
            id="table-area"
            formControlName="area"
            autocomplete="off"
            [attr.aria-invalid]="invalid('area') ? 'true' : null"
            fluid
          />
        </app-form-field>
        <app-form-field
          label="Orden de aparición"
          i18n-label="@@admin.tables.orderLabel"
          controlId="table-order"
          [error]="invalid('displayOrder') ? labels.orderInvalid : null"
        >
          <p-inputnumber
            inputId="table-order"
            formControlName="displayOrder"
            [useGrouping]="false"
            [invalid]="invalid('displayOrder')"
            [pt]="numberAriaInvalid(invalid('displayOrder'))"
            fluid
          />
        </app-form-field>
      </div>
    </app-form-dialog>
  `,
})
export class TableList {
  protected readonly icons = {
    edit: IconPencil,
    deactivate: IconPlayerPause,
    reactivate: IconPlayerPlay,
    create: IconPlus,
  };
  protected readonly tips = {
    deactivate: $localize`:@@admin.tables.deactivate.tooltip:Ocultar esta mesa sin borrarla`,
    reactivate: $localize`:@@admin.tables.reactivate.tooltip:Volver a activar esta mesa`,
  };
  protected readonly labels = {
    active: $localize`:@@admin.tables.active:Activa`,
    inactive: $localize`:@@admin.tables.inactive:Inactiva`,
    createHeader: $localize`:@@admin.tables.createHeader:Agregar mesa`,
    editHeader: $localize`:@@admin.tables.editHeader:Editar mesa`,
    nameInvalid: $localize`:@@admin.tables.nameInvalid:Escribe un nombre de hasta 60 caracteres.`,
    areaInvalid: $localize`:@@admin.tables.areaInvalid:La zona admite hasta 60 caracteres.`,
    orderInvalid: $localize`:@@admin.tables.orderInvalid:Escribe un número entero.`,
  };
  protected readonly columns: readonly DataColumn[] = [
    { field: 'name', header: $localize`:@@admin.tables.name:Nombre`, sortable: true, filter: true },
    { field: 'area', header: $localize`:@@admin.tables.area:Zona`, sortable: true, filter: true },
    { field: 'displayOrder', header: $localize`:@@admin.tables.order:Orden`, sortable: true },
    { field: 'stateLabel', header: $localize`:@@admin.tables.state:Estado`, sortable: true },
    { field: '', header: $localize`:@@admin.tables.actions:Acciones` },
  ];
  private readonly api = inject(TablesApi);
  private readonly notifier = inject(ErrorNotifier);

  protected readonly changes = new UndoableChanges<boolean>(inject(UndoNotice));
  protected readonly tables = signal<readonly AdminTable[]>([]);
  protected readonly editing = signal<AdminTable | null>(null);
  protected readonly open = signal(false);
  protected readonly saving = signal(false);
  protected readonly numberAriaInvalid = numberAriaInvalid;
  protected readonly pending = new PendingIds();
  protected readonly rowsView = computed(() =>
    this.tables().map((table) => {
      const shownActive = this.changes.pending(table.id) ?? table.active;
      return {
        ...table,
        area: table.area ?? '',
        shownActive,
        stateLabel: shownActive ? this.labels.active : this.labels.inactive,
      };
    }),
  );
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
    inject(DestroyRef).onDestroy(() => this.changes.flush());
    this.load();
  }

  protected invalid(name: string): boolean {
    const control = this.form.get(name);
    return !!control && control.invalid && control.touched;
  }

  protected add(): void {
    this.editing.set(null);
    this.resetForm();
    this.open.set(true);
  }

  protected edit(table: AdminTable): void {
    this.editing.set(table);
    this.fill(table);
    this.open.set(true);
  }

  protected close(): void {
    this.open.set(false);
    this.editing.set(null);
    this.resetForm();
  }

  protected toggle(table: AdminTable): void {
    if (this.pending.has(table.id) || this.changes.has(table.id)) {
      return;
    }
    if (table.active) {
      this.changes.schedule(
        table.id,
        false,
        $localize`:@@admin.tables.deactivated:Mesa "${table.name}:name:" desactivada`,
        () => this.api.deactivate(table.id, table.etag).pipe(tap((saved) => this.replace(saved))),
        (error) => this.fail(error, undefined, table.id),
      );
      return;
    }
    this.pending.add(table.id);
    this.api.reactivate(table.id, table.etag).subscribe({
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
        this.close();
        this.saving.set(false);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.fail(error, this.form, target?.id);
      },
    });
  }

  private fill(table: AdminTable): void {
    this.form.reset({
      name: table.name,
      area: table.area ?? '',
      displayOrder: table.displayOrder,
    });
  }

  private resetForm(): void {
    this.form.reset({ name: '', area: '', displayOrder: 0 });
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
          this.editing.set(fresh);
          this.fill(fresh);
        } else {
          this.close();
        }
      },
      error: (error: unknown) => this.notifier.show(error),
    });
  }
}
