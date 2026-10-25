import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  IconPencil,
  IconPlayerPause,
  IconPlayerPlay,
  IconPlus,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Tooltip } from 'primeng/tooltip';
import type { Station } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { ConfirmAction } from '../../../shared/state/confirm-action';
import { PendingIds } from '../../../shared/state/pending-ids';
import { DataColumn, DataTable } from '../../../shared/ui/data-table/data-table';
import { FormDialog } from '../../../shared/ui/form-dialog/form-dialog';
import { FormField } from '../../../shared/ui/form-field/form-field';
import { StatusTag } from '../../../shared/ui/status-tag/status-tag';
import { isStale, reportFailure } from '../admin-errors';
import { CatalogApi } from './catalog-api';

@Component({
  selector: 'app-station-list',
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    InputText,
    TablerIconComponent,
    Tooltip,
    DataTable,
    FormDialog,
    FormField,
    StatusTag,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './catalog.scss',
  template: `
    <div class="toolbar">
      <h2 i18n="@@admin.catalog.stations.title">Estaciones</h2>
      <button
        pButton
        type="button"
        data-testid="add-station"
        (click)="add()"
        pTooltip="Agregar una estación de preparación nueva"
        i18n-pTooltip="@@admin.catalog.stations.create.tooltip"
        tooltipPosition="top"
      >
        <tabler-icon [icon]="icons.create" aria-hidden="true" />
        <span i18n="@@admin.catalog.stations.create">Agregar estación</span>
      </button>
    </div>
    <p class="muted" i18n="@@admin.catalog.stations.caption">Dónde se prepara cada categoría</p>

    <app-data-table
      label="Listado de estaciones"
      i18n-label="@@admin.catalog.stations.tableScroll"
      [value]="stations()"
      [columns]="columns"
    >
      <ng-template #row let-station>
        <td>
          {{ station.name }}
          @if (station.defaultStation) {
            <app-status-tag [value]="labels.default" severity="info" />
          }
        </td>
        <td>
          <app-status-tag
            [value]="station.active ? labels.active : labels.inactive"
            [severity]="station.active ? 'success' : 'secondary'"
          />
        </td>
        <td class="actions">
          <div class="row-actions">
            <button
              pButton
              type="button"
              severity="secondary"
              [size]="'small'"
              [attr.data-testid]="'edit-' + station.id"
              (click)="edit(station)"
              pTooltip="Cambiar el nombre de esta estación"
              i18n-pTooltip="@@admin.catalog.stations.edit.tooltip"
              tooltipPosition="top"
            >
              <tabler-icon [icon]="icons.edit" aria-hidden="true" />
              <span i18n="@@admin.catalog.stations.edit">Renombrar</span>
            </button>
            @if (!station.defaultStation) {
              <button
                pButton
                type="button"
                severity="secondary"
                [size]="'small'"
                [outlined]="true"
                [disabled]="pending.has(station.id)"
                [attr.data-testid]="'toggle-' + station.id"
                (click)="toggle(station)"
                [pTooltip]="station.active ? tips.deactivate : tips.reactivate"
                tooltipPosition="top"
              >
                @if (station.active) {
                  <tabler-icon [icon]="icons.deactivate" aria-hidden="true" />
                  <ng-container i18n="@@admin.catalog.stations.deactivate">Desactivar</ng-container>
                } @else {
                  <tabler-icon [icon]="icons.reactivate" aria-hidden="true" />
                  <ng-container i18n="@@admin.catalog.stations.reactivate">Reactivar</ng-container>
                }
              </button>
            }
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
          label="Nombre de la estación"
          i18n-label="@@admin.catalog.stations.nameLabel"
          controlId="station-name"
          [error]="invalid() ? labels.nameInvalid : null"
        >
          <input
            pInputText
            id="station-name"
            formControlName="name"
            autocomplete="off"
            maxlength="80"
            [attr.aria-invalid]="invalid() ? 'true' : null"
            fluid
          />
        </app-form-field>
      </div>
    </app-form-dialog>
  `,
})
export class StationList {
  protected readonly icons = {
    edit: IconPencil,
    create: IconPlus,
    deactivate: IconPlayerPause,
    reactivate: IconPlayerPlay,
  };
  protected readonly tips = {
    deactivate: $localize`:@@admin.catalog.stations.deactivate.tooltip:Dejar de usar esta estación`,
    reactivate: $localize`:@@admin.catalog.stations.reactivate.tooltip:Volver a usar esta estación`,
  };
  protected readonly labels = {
    active: $localize`:@@admin.catalog.stations.active:Activa`,
    inactive: $localize`:@@admin.catalog.stations.inactive:Inactiva`,
    default: $localize`:@@admin.catalog.stations.default:Predeterminada`,
    createHeader: $localize`:@@admin.catalog.stations.createHeader:Agregar estación`,
    editHeader: $localize`:@@admin.catalog.stations.editHeader:Renombrar estación`,
    nameInvalid: $localize`:@@admin.catalog.stations.nameInvalid:Escribe un nombre válido.`,
  };
  protected readonly columns: readonly DataColumn[] = [
    { field: 'name', header: $localize`:@@admin.catalog.stations.name:Nombre`, sortable: true, filter: true },
    { field: 'active', header: $localize`:@@admin.catalog.stations.state:Estado`, sortable: true },
    { field: '', header: $localize`:@@admin.catalog.actions:Acciones` },
  ];
  private readonly api = inject(CatalogApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly confirm = inject(ConfirmAction);

  protected readonly pending = new PendingIds();
  protected readonly stations = signal<readonly Station[]>([]);
  protected readonly editing = signal<Station | null>(null);
  protected readonly open = signal(false);
  protected readonly saving = signal(false);
  protected readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/)],
    }),
  });

  constructor() {
    this.load();
  }

  protected invalid(): boolean {
    const control = this.form.controls.name;
    return control.invalid && control.touched;
  }

  protected add(): void {
    this.editing.set(null);
    this.form.reset();
    this.open.set(true);
  }

  protected edit(station: Station): void {
    this.editing.set(station);
    this.form.reset({ name: station.name });
    this.open.set(true);
  }

  protected close(): void {
    this.open.set(false);
    this.editing.set(null);
    this.form.reset();
  }

  protected async toggle(station: Station): Promise<void> {
    if (this.pending.has(station.id)) {
      return;
    }
    if (station.active) {
      const confirmed = await this.confirm.ask({
        header: $localize`:@@admin.catalog.stations.deactivate.header:Desactivar estación`,
        message: $localize`:@@admin.catalog.stations.deactivate.confirm:La estación "${station.name}:name:" dejará de ofrecerse en el tablero de cocina y para nuevas categorías.`,
        acceptLabel: $localize`:@@admin.catalog.stations.deactivate:Desactivar`,
      });
      if (!confirmed) {
        return;
      }
    }
    this.pending.add(station.id);
    const request = station.active
      ? this.api.deactivateStation(station.id, station.etag)
      : this.api.reactivateStation(station.id, station.etag);
    request.subscribe({
      next: (saved) => {
        this.pending.delete(station.id);
        this.stations.update((list) => list.map((item) => (item.id === saved.id ? saved : item)));
      },
      error: (error: unknown) => {
        this.pending.delete(station.id);
        this.notifier.show(error);
        if (isStale(error)) {
          this.load();
        }
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
    const body = { name: this.form.controls.name.value.trim() };
    const target = this.editing();
    const request = target
      ? this.api.renameStation(target.id, target.etag, body)
      : this.api.createStation(body);
    this.saving.set(true);
    request.subscribe({
      next: (saved) => {
        this.stations.update((list) =>
          target ? list.map((item) => (item.id === saved.id ? saved : item)) : [...list, saved],
        );
        this.close();
        this.saving.set(false);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        reportFailure(this.notifier, error, this.form);
        if (isStale(error)) {
          this.load();
        }
      },
    });
  }

  private load(): void {
    this.api.stations().subscribe({
      next: (list) => {
        this.stations.set(list);
        const current = this.editing();
        const fresh = current && list.find((item) => item.id === current.id);
        if (fresh) {
          this.editing.set(fresh);
          this.form.reset({ name: fresh.name });
        } else if (current) {
          this.close();
        }
      },
      error: (error: unknown) => this.notifier.show(error),
    });
  }
}
