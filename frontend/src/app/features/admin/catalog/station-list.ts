import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  IconDeviceFloppy,
  IconPencil,
  IconPlus,
  IconX,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Tooltip } from 'primeng/tooltip';
import type { Station } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { CatalogApi } from './catalog-api';
import { isStale, reportFailure } from '../admin-errors';

@Component({
  selector: 'app-station-list',
  imports: [ReactiveFormsModule, ButtonDirective, InputText, TablerIconComponent, Tooltip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './catalog.scss',
  template: `
    <h2 i18n="@@admin.catalog.stations.title">Estaciones</h2>

    <table>
      <caption class="muted" i18n="@@admin.catalog.stations.caption">
        Dónde se prepara cada categoría
      </caption>
      <thead>
        <tr>
          <th i18n="@@admin.catalog.stations.name">Nombre</th>
          <th><span class="sr-only" i18n="@@admin.catalog.actions">Acciones</span></th>
        </tr>
      </thead>
      <tbody>
        @for (station of stations(); track station.id) {
          <tr>
            <td>
              {{ station.name }}
              @if (station.defaultStation) {
                <span class="badge" i18n="@@admin.catalog.stations.default">Predeterminada</span>
              }
            </td>
            <td>
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
            </td>
          </tr>
        }
      </tbody>
    </table>

    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <div class="field wide">
        <label for="station-name" i18n="@@admin.catalog.stations.nameLabel">Nombre de la estación</label>
        <input
          pInputText
          id="station-name"
          formControlName="name"
          autocomplete="off"
          [attr.aria-invalid]="form.controls.name.invalid && form.controls.name.touched ? 'true' : null"
          fluid
        />
        @if (form.controls.name.invalid && form.controls.name.touched) {
          <small class="error" i18n="@@admin.catalog.stations.nameInvalid">Escribe un nombre válido.</small>
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
            <tabler-icon [icon]="icons.save" aria-hidden="true" />
            <ng-container i18n="@@admin.catalog.stations.save">Guardar cambios</ng-container>
          } @else {
            <tabler-icon [icon]="icons.create" aria-hidden="true" />
            <ng-container i18n="@@admin.catalog.stations.create">Agregar estación</ng-container>
          }
        </button>
        @if (editing()) {
          <button
            pButton
            type="button"
            severity="secondary"
            (click)="cancel()"
            pTooltip="Descartar los cambios y cerrar el formulario"
            i18n-pTooltip="@@admin.catalog.cancel.tooltip"
            tooltipPosition="top"
          >
            <tabler-icon [icon]="icons.cancel" aria-hidden="true" />
            <span i18n="@@admin.catalog.cancel">Cancelar</span>
          </button>
        }
      </div>
    </form>
  `,
})
export class StationList {
  protected readonly icons = {
    edit: IconPencil,
    save: IconDeviceFloppy,
    create: IconPlus,
    cancel: IconX,
  };
  protected readonly tips = {
    save: $localize`:@@admin.catalog.stations.save.tooltip:Guardar el nuevo nombre de la estación`,
    create: $localize`:@@admin.catalog.stations.create.tooltip:Agregar una estación de preparación nueva`,
  };
  private readonly api = inject(CatalogApi);
  private readonly notifier = inject(ErrorNotifier);

  protected readonly stations = signal<readonly Station[]>([]);
  protected readonly editing = signal<Station | null>(null);
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

  protected edit(station: Station): void {
    this.editing.set(station);
    this.form.setValue({ name: station.name });
  }

  protected cancel(): void {
    this.editing.set(null);
    this.form.reset();
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
        this.cancel();
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
          this.edit(fresh);
        } else if (current) {
          this.cancel();
        }
      },
      error: (error: unknown) => this.notifier.show(error),
    });
  }
}
