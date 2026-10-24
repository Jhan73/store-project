import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  IconPlayerPause,
  IconPlayerPlay,
  IconPlus,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Tooltip } from 'primeng/tooltip';
import { tap } from 'rxjs';
import type { Reason, ReasonType } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { isStale, reportFailure } from '../admin-errors';
import { PendingIds } from '../../../shared/state/pending-ids';
import { UndoNotice } from '../../../shared/state/undo-notice';
import { UndoableChanges } from '../../../shared/state/undoable-changes';
import { DataColumn, DataTable } from '../../../shared/ui/data-table/data-table';
import { FormDialog } from '../../../shared/ui/form-dialog/form-dialog';
import { FormField } from '../../../shared/ui/form-field/form-field';
import { StatusTag } from '../../../shared/ui/status-tag/status-tag';
import { SettingsApi } from './settings-api';

interface TypeOption {
  readonly value: ReasonType;
  readonly label: string;
}

const TYPES: readonly TypeOption[] = [
  { value: 'VOID', label: $localize`:@@admin.settings.reasons.void:Anulaciones` },
  { value: 'COMP', label: $localize`:@@admin.settings.reasons.comp:Cortesías` },
  { value: 'CASH_OUT', label: $localize`:@@admin.settings.reasons.cashOut:Salidas de caja` },
  {
    value: 'STOCK_ADJUSTMENT',
    label: $localize`:@@admin.settings.reasons.stockAdjustment:Ajustes de inventario`,
  },
];

@Component({
  selector: 'app-reason-list',
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    InputText,
    Select,
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
      <h2 i18n="@@admin.settings.reasons.title">Motivos</h2>
      <button
        pButton
        type="button"
        data-testid="add-reason"
        (click)="add()"
        pTooltip="Agregar un motivo nuevo a la lista"
        i18n-pTooltip="@@admin.settings.reasons.create.tooltip"
        tooltipPosition="top"
      >
        <tabler-icon [icon]="icons.create" aria-hidden="true" />
        <span i18n="@@admin.settings.reasons.create">Agregar motivo</span>
      </button>
    </div>

    <app-form-field
      label="Lista de motivos"
      i18n-label="@@admin.settings.reasons.typeLabel"
      controlId="reason-type"
    >
      <p-select
        inputId="reason-type"
        [ariaLabel]="labels.type"
        optionLabel="label"
        optionValue="value"
        [options]="types"
        [filter]="true"
        filterBy="label"
        [resetFilterOnHide]="true"
        filterPlaceholder="Buscar"
        i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
        [formControl]="typeControl"
        (onChange)="pick($event.value)"
        fluid
      />
    </app-form-field>

    <app-data-table
      label="Listado de motivos"
      i18n-label="@@admin.settings.reasons.tableScroll"
      emptyText="Todavía no hay motivos en esta lista."
      i18n-emptyText="@@admin.settings.reasons.empty"
      [value]="rowsView()"
      [columns]="columns"
    >
      <ng-template #row let-reason>
        <td>{{ reason.code }}</td>
        <td>
          <app-status-tag
            [value]="reason.stateLabel"
            [severity]="reason.shownActive ? 'success' : 'secondary'"
          />
        </td>
        <td class="actions">
          <div class="row-actions">
            <button
              pButton
              type="button"
              severity="secondary"
              [size]="'small'"
              [outlined]="true"
              [disabled]="pending.has(reason.id) || changes.has(reason.id)"
              [attr.data-testid]="'toggle-' + reason.id"
              (click)="toggle(reason)"
              [pTooltip]="reason.shownActive ? tips.deactivate : tips.reactivate"
              tooltipPosition="top"
            >
              @if (reason.shownActive) {
                <tabler-icon [icon]="icons.deactivate" aria-hidden="true" />
                <ng-container i18n="@@admin.settings.deactivate">Desactivar</ng-container>
              } @else {
                <tabler-icon [icon]="icons.reactivate" aria-hidden="true" />
                <ng-container i18n="@@admin.settings.reactivate">Reactivar</ng-container>
              }
            </button>
          </div>
        </td>
      </ng-template>
    </app-data-table>

    <app-form-dialog
      header="Agregar motivo"
      i18n-header="@@admin.settings.reasons.createHeader"
      [visible]="open()"
      [busy]="saving()"
      (saved)="submit()"
      (dismissed)="close()"
    >
      <div [formGroup]="form" class="dialog-fields">
        <app-form-field
          label="Nuevo motivo"
          i18n-label="@@admin.settings.reasons.codeLabel"
          controlId="reason-code"
          [error]="code.invalid && code.touched ? labels.codeInvalid : null"
        >
          <input
            pInputText
            id="reason-code"
            formControlName="code"
            autocomplete="off"
            maxlength="60"
            [attr.aria-invalid]="code.invalid && code.touched ? 'true' : null"
            fluid
          />
        </app-form-field>
      </div>
    </app-form-dialog>
  `,
})
export class ReasonList {
  protected readonly icons = {
    deactivate: IconPlayerPause,
    reactivate: IconPlayerPlay,
    create: IconPlus,
  };
  protected readonly tips = {
    deactivate: $localize`:@@admin.settings.reasons.deactivate.tooltip:Dejar de ofrecer este motivo`,
    reactivate: $localize`:@@admin.settings.reasons.reactivate.tooltip:Volver a ofrecer este motivo`,
  };
  protected readonly labels = {
    type: $localize`:@@admin.settings.reasons.typeLabel:Lista de motivos`,
    active: $localize`:@@admin.settings.reasons.active:Activo`,
    inactive: $localize`:@@admin.settings.reasons.inactive:Inactivo`,
    codeInvalid: $localize`:@@admin.settings.reasons.codeInvalid:Escribe un motivo.`,
  };
  protected readonly columns: readonly DataColumn[] = [
    { field: 'code', header: $localize`:@@admin.settings.reasons.code:Motivo`, sortable: true, filter: true },
    { field: 'stateLabel', header: $localize`:@@admin.settings.reasons.state:Estado`, sortable: true },
    { field: '', header: $localize`:@@admin.settings.actions:Acciones` },
  ];
  private readonly api = inject(SettingsApi);
  private readonly notifier = inject(ErrorNotifier);

  protected readonly changes = new UndoableChanges<boolean>(inject(UndoNotice));
  protected readonly types = [...TYPES];
  protected readonly type = signal<ReasonType>(TYPES[0].value);
  protected readonly typeControl = new FormControl<ReasonType>(TYPES[0].value, {
    nonNullable: true,
  });
  protected readonly reasons = signal<readonly Reason[]>([]);
  protected readonly open = signal(false);
  protected readonly saving = signal(false);
  protected readonly pending = new PendingIds();
  protected readonly rowsView = computed(() =>
    this.reasons().map((reason) => {
      const shownActive = this.changes.pending(reason.id) ?? reason.active;
      return {
        ...reason,
        shownActive,
        stateLabel: shownActive ? this.labels.active : this.labels.inactive,
      };
    }),
  );
  protected readonly code = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.pattern(/\S/)],
  });
  protected readonly form = new FormGroup({ code: this.code });
  private lastLoad = 0;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.changes.flush());
    this.load();
  }

  protected pick(type: ReasonType): void {
    this.type.set(type);
    this.load();
  }

  protected add(): void {
    this.code.reset('');
    this.open.set(true);
  }

  protected close(): void {
    this.open.set(false);
    this.code.reset('');
  }

  protected toggle(reason: Reason): void {
    if (this.pending.has(reason.id) || this.changes.has(reason.id)) {
      return;
    }
    if (reason.active) {
      this.changes.schedule(
        reason.id,
        false,
        $localize`:@@admin.settings.reasons.deactivated:Motivo "${reason.code}:code:" desactivado`,
        () =>
          this.api.deactivateReason(reason.id, reason.etag).pipe(tap((saved) => this.replace(saved))),
        (error) => this.fail(error),
      );
      return;
    }
    this.pending.add(reason.id);
    this.api.reactivateReason(reason.id, reason.etag).subscribe({
      next: (saved) => {
        this.pending.delete(reason.id);
        this.replace(saved);
      },
      error: (error: unknown) => {
        this.pending.delete(reason.id);
        this.fail(error);
      },
    });
  }

  protected submit(): void {
    if (this.saving()) {
      return;
    }
    if (this.code.invalid) {
      this.code.markAsTouched();
      return;
    }
    this.saving.set(true);
    this.api.createReason({ type: this.type(), code: this.code.value.trim() }).subscribe({
      next: (saved) => {
        this.saving.set(false);
        if (saved.type === this.type()) {
          this.reasons.update((list) => [...list, saved]);
        }
        this.close();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        reportFailure(this.notifier, error);
      },
    });
  }

  private replace(saved: Reason): void {
    this.reasons.update((list) => list.map((item) => (item.id === saved.id ? saved : item)));
  }

  private fail(error: unknown): void {
    reportFailure(this.notifier, error);
    if (isStale(error)) {
      this.load();
    }
  }

  private load(): void {
    const load = ++this.lastLoad;
    this.api.reasons(this.type()).subscribe({
      next: (list) => {
        if (load === this.lastLoad) {
          this.reasons.set(list);
        }
      },
      error: (error: unknown) => {
        if (load === this.lastLoad) {
          this.notifier.show(error);
        }
      },
    });
  }
}
