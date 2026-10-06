import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import type { Station } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { CatalogApi } from './catalog-api';
import { isStale, reportFailure } from '../admin-errors';

@Component({
  selector: 'app-station-list',
  imports: [ReactiveFormsModule, ButtonDirective, InputText],
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
                i18n="@@admin.catalog.stations.edit"
              >
                Renombrar
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
        <button pButton type="submit" [loading]="saving()">
          @if (editing()) {
            <ng-container i18n="@@admin.catalog.stations.save">Guardar cambios</ng-container>
          } @else {
            <ng-container i18n="@@admin.catalog.stations.create">Agregar estación</ng-container>
          }
        </button>
        @if (editing()) {
          <button
            pButton
            type="button"
            severity="secondary"
            (click)="cancel()"
            i18n="@@admin.catalog.cancel"
          >
            Cancelar
          </button>
        }
      </div>
    </form>
  `,
})
export class StationList {
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
