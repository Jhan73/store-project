import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import type { Reason, ReasonType } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { isStale, reportFailure } from '../admin-errors';
import { PendingIds } from '../../../shared/state/pending-ids';
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
  imports: [ReactiveFormsModule, ButtonDirective, InputText, Select],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../admin-section.scss',
  template: `
    <h2 i18n="@@admin.settings.reasons.title">Motivos</h2>

    <div class="field">
      <label
        for="reason-type"
        id="reason-type-label"
        i18n="@@admin.settings.reasons.typeLabel"
        >Lista de motivos</label
      >
      <p-select
        inputId="reason-type"
        [ariaLabelledBy]="'reason-type-label'"
        optionLabel="label"
        optionValue="value"
        [options]="types"
        [formControl]="typeControl"
        (onChange)="pick($event.value)"
      />
    </div>

    @if (reasons().length === 0) {
      <p class="muted" i18n="@@admin.settings.reasons.empty">Todavía no hay motivos en esta lista.</p>
    } @else {
      <table>
        <thead>
          <tr>
            <th i18n="@@admin.settings.reasons.code">Motivo</th>
            <th i18n="@@admin.settings.reasons.state">Estado</th>
            <th><span class="sr-only" i18n="@@admin.settings.actions">Acciones</span></th>
          </tr>
        </thead>
        <tbody>
          @for (reason of reasons(); track reason.id) {
            <tr>
              <td>{{ reason.code }}</td>
              <td>
                @if (reason.active) {
                  <span i18n="@@admin.settings.reasons.active">Activo</span>
                } @else {
                  <span class="muted" i18n="@@admin.settings.reasons.inactive">Inactivo</span>
                }
              </td>
              <td class="actions">
                <button
                  pButton
                  type="button"
                  severity="secondary"
                  [size]="'small'"
                  [outlined]="true"
                  [disabled]="pending.has(reason.id)"
                  [attr.data-testid]="'toggle-' + reason.id"
                  (click)="toggle(reason)"
                >
                  @if (reason.active) {
                    <ng-container i18n="@@admin.settings.deactivate">Desactivar</ng-container>
                  } @else {
                    <ng-container i18n="@@admin.settings.reactivate">Reactivar</ng-container>
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
        <label for="reason-code" i18n="@@admin.settings.reasons.codeLabel">Nuevo motivo</label>
        <input
          pInputText
          id="reason-code"
          formControlName="code"
          autocomplete="off"
          [attr.aria-invalid]="code.invalid && code.touched ? 'true' : null"
          fluid
        />
        @if (code.invalid && code.touched) {
          <small class="error" i18n="@@admin.settings.reasons.codeInvalid">Escribe un motivo.</small>
        }
      </div>
      <div class="actions">
        <button pButton type="submit" [loading]="saving()" i18n="@@admin.settings.reasons.create">
          Agregar motivo
        </button>
      </div>
    </form>
  `,
})
export class ReasonList {
  private readonly api = inject(SettingsApi);
  private readonly notifier = inject(ErrorNotifier);

  protected readonly types = [...TYPES];
  protected readonly type = signal<ReasonType>(TYPES[0].value);
  protected readonly typeControl = new FormControl<ReasonType>(TYPES[0].value, {
    nonNullable: true,
  });
  protected readonly reasons = signal<readonly Reason[]>([]);
  protected readonly saving = signal(false);
  protected readonly pending = new PendingIds();
  protected readonly code = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.pattern(/\S/)],
  });
  protected readonly form = new FormGroup({ code: this.code });
  private lastLoad = 0;

  constructor() {
    this.load();
  }

  protected pick(type: ReasonType): void {
    this.type.set(type);
    this.load();
  }

  protected toggle(reason: Reason): void {
    if (this.pending.has(reason.id)) {
      return;
    }
    this.pending.add(reason.id);
    const request = reason.active
      ? this.api.deactivateReason(reason.id, reason.etag)
      : this.api.reactivateReason(reason.id, reason.etag);
    request.subscribe({
      next: (saved) => {
        this.pending.delete(reason.id);
        this.reasons.update((list) => list.map((item) => (item.id === saved.id ? saved : item)));
      },
      error: (error: unknown) => {
        this.pending.delete(reason.id);
        reportFailure(this.notifier, error);
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
        this.code.reset('');
      },
      error: (error: unknown) => {
        this.saving.set(false);
        reportFailure(this.notifier, error);
      },
    });
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
