import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import type { DeliveryZone, Money } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { formatMoney, parseAmount } from '../../../core/money/money';
import { amountValidator, optionalAmountValidator } from '../amount-validator';
import { isStale, reportFailure } from '../admin-errors';
import { wholeNumber } from '../admin-validators';
import { PendingIds } from '../pending-ids';
import { SettingsApi } from './settings-api';

@Component({
  selector: 'app-zone-list',
  imports: [ReactiveFormsModule, ButtonDirective, InputText],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../admin-section.scss',
  template: `
    <h2 i18n="@@admin.settings.zones.title">Zonas de reparto</h2>

    <table>
      <thead>
        <tr>
          <th i18n="@@admin.settings.zones.name">Nombre</th>
          <th i18n="@@admin.settings.zones.fee">Tarifa</th>
          <th i18n="@@admin.settings.zones.minutes">Minutos de reparto</th>
          <th i18n="@@admin.settings.zones.minimum">Pedido mínimo</th>
          <th i18n="@@admin.settings.zones.free">Reparto gratis desde</th>
          <th i18n="@@admin.settings.zones.state">Estado</th>
          <th><span class="sr-only" i18n="@@admin.settings.actions">Acciones</span></th>
        </tr>
      </thead>
      <tbody>
        @for (zone of zones(); track zone.id) {
          <tr>
            <td>{{ zone.name }}</td>
            <td>{{ money(zone.fee) }}</td>
            <td>{{ zone.deliveryMinutes }}</td>
            <td>{{ optionalMoney(zone.minimumOrder) }}</td>
            <td>{{ optionalMoney(zone.freeDeliveryThreshold) }}</td>
            <td>
              @if (zone.active) {
                <span i18n="@@admin.settings.active">Activa</span>
              } @else {
                <span class="muted" i18n="@@admin.settings.inactive">Inactiva</span>
              }
            </td>
            <td class="actions">
              <button
                pButton
                type="button"
                severity="secondary"
                [size]="'small'"
                [disabled]="saving()"
                [attr.data-testid]="'edit-' + zone.id"
                (click)="edit(zone)"
                i18n="@@admin.settings.edit"
              >
                Editar
              </button>
              <button
                pButton
                type="button"
                severity="secondary"
                [size]="'small'"
                [outlined]="true"
                [disabled]="pending.has(zone.id)"
                [attr.data-testid]="'toggle-' + zone.id"
                (click)="toggle(zone)"
              >
                @if (zone.active) {
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

    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <div class="field wide">
        <label for="zone-name" i18n="@@admin.settings.zones.nameLabel">Nombre de la zona</label>
        <input
          pInputText
          id="zone-name"
          formControlName="name"
          autocomplete="off"
          [attr.aria-invalid]="invalid('name') ? 'true' : null"
          fluid
        />
        @if (invalid('name')) {
          <small class="error" i18n="@@admin.settings.zones.nameInvalid">Escribe un nombre válido.</small>
        }
      </div>
      <div class="field">
        <label for="zone-fee" i18n="@@admin.settings.zones.feeLabel">Tarifa ({{ currency() }})</label>
        <input
          pInputText
          id="zone-fee"
          formControlName="fee"
          autocomplete="off"
          [attr.inputmode]="'decimal'"
          [attr.aria-invalid]="invalid('fee') ? 'true' : null"
        />
        @if (invalid('fee')) {
          <small class="error" i18n="@@admin.settings.zones.feeInvalid">Escribe un monto, como 4.00.</small>
        }
      </div>
      <div class="field">
        <label for="zone-minutes" i18n="@@admin.settings.zones.minutesLabel">Minutos de reparto</label>
        <input
          pInputText
          id="zone-minutes"
          type="number"
          min="1"
          step="1"
          formControlName="deliveryMinutes"
          [attr.aria-invalid]="invalid('deliveryMinutes') ? 'true' : null"
        />
        @if (invalid('deliveryMinutes')) {
          <small class="error" i18n="@@admin.settings.zones.minutesInvalid">Escribe un número entero mayor que cero.</small>
        }
      </div>
      <div class="field">
        <label for="zone-minimum" i18n="@@admin.settings.zones.minimumLabel">Pedido mínimo ({{ currency() }}, opcional)</label>
        <input
          pInputText
          id="zone-minimum"
          formControlName="minimumOrder"
          autocomplete="off"
          [attr.inputmode]="'decimal'"
          [attr.aria-invalid]="invalid('minimumOrder') ? 'true' : null"
        />
        @if (invalid('minimumOrder')) {
          <small class="error" i18n="@@admin.settings.zones.minimumInvalid">Escribe un monto, como 15.00, o déjalo vacío.</small>
        }
      </div>
      <div class="field">
        <label for="zone-free" i18n="@@admin.settings.zones.freeLabel">Reparto gratis desde ({{ currency() }}, opcional)</label>
        <input
          pInputText
          id="zone-free"
          formControlName="freeDeliveryThreshold"
          autocomplete="off"
          [attr.inputmode]="'decimal'"
          [attr.aria-invalid]="invalid('freeDeliveryThreshold') ? 'true' : null"
        />
        @if (invalid('freeDeliveryThreshold')) {
          <small class="error" i18n="@@admin.settings.zones.freeInvalid">Escribe un monto, como 50.00, o déjalo vacío.</small>
        }
      </div>
      <div class="actions">
        <button pButton type="submit" [loading]="saving()" [disabled]="!currency()">
          @if (editing()) {
            <ng-container i18n="@@admin.settings.zones.save">Guardar cambios</ng-container>
          } @else {
            <ng-container i18n="@@admin.settings.zones.create">Agregar zona</ng-container>
          }
        </button>
        @if (editing()) {
          <button pButton type="button" severity="secondary" (click)="cancel()" i18n="@@admin.settings.cancel">
            Cancelar
          </button>
        }
      </div>
    </form>
  `,
})
export class ZoneList {
  private readonly api = inject(SettingsApi);
  private readonly notifier = inject(ErrorNotifier);

  protected readonly zones = signal<readonly DeliveryZone[]>([]);
  protected readonly currency = signal('');
  protected readonly editing = signal<DeliveryZone | null>(null);
  protected readonly saving = signal(false);
  protected readonly pending = new PendingIds();
  private lastLoad = 0;
  private reconcile = false;
  protected readonly form = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/)],
    }),
    fee: new FormControl('', { nonNullable: true, validators: [Validators.required, amountValidator] }),
    deliveryMinutes: new FormControl<number | null>(null, wholeNumber(1)),
    minimumOrder: new FormControl('', { nonNullable: true, validators: [optionalAmountValidator] }),
    freeDeliveryThreshold: new FormControl('', {
      nonNullable: true,
      validators: [optionalAmountValidator],
    }),
  });

  constructor() {
    this.load();
    this.api.settings().subscribe({
      next: ({ value }) => this.currency.set(value.currency),
      error: (error: unknown) => this.notifier.show(error),
    });
  }

  protected money(amount: Money): string {
    return formatMoney(amount);
  }

  protected optionalMoney(amount: Money | null): string {
    return amount ? formatMoney(amount) : '—';
  }

  protected invalid(name: string): boolean {
    const control = this.form.get(name);
    return !!control && control.invalid && control.touched;
  }

  protected edit(zone: DeliveryZone): void {
    this.editing.set(zone);
    this.form.setValue({
      name: zone.name,
      fee: zone.fee.amount,
      deliveryMinutes: zone.deliveryMinutes,
      minimumOrder: zone.minimumOrder?.amount ?? '',
      freeDeliveryThreshold: zone.freeDeliveryThreshold?.amount ?? '',
    });
  }

  protected cancel(): void {
    this.editing.set(null);
    this.form.reset({
      name: '',
      fee: '',
      deliveryMinutes: null,
      minimumOrder: '',
      freeDeliveryThreshold: '',
    });
  }

  protected toggle(zone: DeliveryZone): void {
    if (this.pending.has(zone.id)) {
      return;
    }
    this.pending.add(zone.id);
    const request = zone.active
      ? this.api.deactivateZone(zone.id, zone.etag)
      : this.api.reactivateZone(zone.id, zone.etag);
    request.subscribe({
      next: (saved) => {
        this.pending.delete(zone.id);
        this.replace(saved);
      },
      error: (error: unknown) => {
        this.pending.delete(zone.id);
        this.fail(error, undefined, zone.id);
      },
    });
  }

  protected submit(): void {
    if (this.saving() || !this.currency()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const body = {
      name: raw.name.trim(),
      fee: this.amount(raw.fee),
      deliveryMinutes: raw.deliveryMinutes ?? 0,
      minimumOrder: this.optionalAmount(raw.minimumOrder),
      freeDeliveryThreshold: this.optionalAmount(raw.freeDeliveryThreshold),
    };
    const target = this.editing();
    const request = target
      ? this.api.changeZone(target.id, target.etag, body)
      : this.api.createZone(body);
    this.saving.set(true);
    request.subscribe({
      next: (saved) => {
        if (target) {
          this.replace(saved);
        } else {
          this.zones.update((list) => [...list, saved]);
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

  private amount(text: string): Money {
    return { amount: parseAmount(text) ?? '0.00', currency: this.currency() };
  }

  private optionalAmount(text: string): Money | null {
    return text.trim() === '' ? null : this.amount(text);
  }

  private replace(saved: DeliveryZone): void {
    this.zones.update((list) => list.map((item) => (item.id === saved.id ? saved : item)));
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
    this.api.zones().subscribe({
      next: (list) => {
        if (load !== this.lastLoad) {
          return;
        }
        this.zones.set(list);
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
