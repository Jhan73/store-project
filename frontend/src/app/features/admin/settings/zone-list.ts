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
import type { DeliveryZone, Money } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { formatMoneyOrDash, parseAmount, safeMinorUnits } from '../../../core/money/money';
import { amountValidator, optionalAmountValidator } from '../../../shared/forms/amount-validator';
import { isStale, reportFailure } from '../admin-errors';
import { wholeNumber } from '../admin-validators';
import { PendingIds } from '../../../shared/state/pending-ids';
import { UndoNotice } from '../../../shared/state/undo-notice';
import { UndoableChanges } from '../../../shared/state/undoable-changes';
import { DataColumn, DataTable } from '../../../shared/ui/data-table/data-table';
import { FormDialog } from '../../../shared/ui/form-dialog/form-dialog';
import { FormField } from '../../../shared/ui/form-field/form-field';
import { MoneyInput } from '../../../shared/ui/money-input/money-input';
import { StatusTag } from '../../../shared/ui/status-tag/status-tag';
import { SettingsApi } from './settings-api';

@Component({
  selector: 'app-zone-list',
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
    MoneyInput,
    StatusTag,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../admin-section.scss',
  template: `
    <div class="toolbar">
      <h2 i18n="@@admin.settings.zones.title">Zonas de reparto</h2>
      <button
        pButton
        type="button"
        data-testid="add-zone"
        [disabled]="saving()"
        (click)="add()"
        pTooltip="Agregar una zona de entrega nueva"
        i18n-pTooltip="@@admin.settings.zones.create.tooltip"
        tooltipPosition="top"
      >
        <tabler-icon [icon]="icons.create" aria-hidden="true" />
        <span i18n="@@admin.settings.zones.create">Agregar zona</span>
      </button>
    </div>

    <app-data-table
      label="Listado de zonas de reparto"
      i18n-label="@@admin.settings.zones.tableScroll"
      [value]="rowsView()"
      [columns]="columns"
    >
      <ng-template #row let-zone>
        <td>{{ zone.name }}</td>
        <td>{{ zone.feeText }}</td>
        <td>{{ zone.deliveryMinutes }}</td>
        <td>{{ zone.minimumText }}</td>
        <td>{{ zone.freeText }}</td>
        <td>
          <app-status-tag
            [value]="zone.stateLabel"
            [severity]="zone.shownActive ? 'success' : 'secondary'"
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
              [attr.data-testid]="'edit-' + zone.id"
              (click)="edit(zone)"
              pTooltip="Editar los datos de esta zona"
              i18n-pTooltip="@@admin.settings.edit.tooltip"
              tooltipPosition="top"
            >
              <tabler-icon [icon]="icons.edit" aria-hidden="true" />
              <span i18n="@@admin.settings.edit">Editar</span>
            </button>
            <button
              pButton
              type="button"
              severity="secondary"
              [size]="'small'"
              [outlined]="true"
              [disabled]="pending.has(zone.id) || changes.has(zone.id)"
              [attr.data-testid]="'toggle-' + zone.id"
              (click)="toggle(zone)"
              [pTooltip]="zone.shownActive ? tips.deactivate : tips.reactivate"
              tooltipPosition="top"
            >
              @if (zone.shownActive) {
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
      [header]="editing() ? labels.editHeader : labels.createHeader"
      [visible]="open()"
      [busy]="saving()"
      [saveDisabled]="!currency()"
      (saved)="submit()"
      (dismissed)="close()"
    >
      <div [formGroup]="form" class="dialog-fields">
        <app-form-field
          label="Nombre de la zona"
          i18n-label="@@admin.settings.zones.nameLabel"
          controlId="zone-name"
          [error]="invalid('name') ? labels.nameInvalid : null"
        >
          <input
            pInputText
            id="zone-name"
            formControlName="name"
            autocomplete="off"
            maxlength="120"
            [attr.aria-invalid]="invalid('name') ? 'true' : null"
            fluid
          />
        </app-form-field>
        <app-form-field
          label="Tarifa"
          i18n-label="@@admin.settings.zones.feeLabel"
          controlId="zone-fee"
          [error]="invalid('fee') ? labels.feeInvalid : null"
        >
          <app-money-input inputId="zone-fee" formControlName="fee" [currency]="currency()" />
        </app-form-field>
        <app-form-field
          label="Minutos de reparto"
          i18n-label="@@admin.settings.zones.minutesLabel"
          controlId="zone-minutes"
          [error]="invalid('deliveryMinutes') ? labels.minutesInvalid : null"
        >
          <p-inputnumber
            inputId="zone-minutes"
            formControlName="deliveryMinutes"
            [useGrouping]="false"
            [invalid]="invalid('deliveryMinutes')"
            [pt]="numberAriaInvalid(invalid('deliveryMinutes'))"
            fluid
          />
        </app-form-field>
        <app-form-field
          label="Pedido mínimo (opcional)"
          i18n-label="@@admin.settings.zones.minimumLabel"
          controlId="zone-minimum"
          [error]="invalid('minimumOrder') ? labels.minimumInvalid : null"
        >
          <app-money-input
            inputId="zone-minimum"
            formControlName="minimumOrder"
            [currency]="currency()"
            [optional]="true"
          />
        </app-form-field>
        <app-form-field
          label="Reparto gratis desde (opcional)"
          i18n-label="@@admin.settings.zones.freeLabel"
          controlId="zone-free"
          [error]="invalid('freeDeliveryThreshold') ? labels.freeInvalid : null"
        >
          <app-money-input
            inputId="zone-free"
            formControlName="freeDeliveryThreshold"
            [currency]="currency()"
            [optional]="true"
          />
        </app-form-field>
      </div>
    </app-form-dialog>
  `,
})
export class ZoneList {
  protected readonly icons = {
    edit: IconPencil,
    deactivate: IconPlayerPause,
    reactivate: IconPlayerPlay,
    create: IconPlus,
  };
  protected readonly tips = {
    deactivate: $localize`:@@admin.settings.deactivate.tooltip:Dejar de usar esta zona de entrega`,
    reactivate: $localize`:@@admin.settings.reactivate.tooltip:Volver a usar esta zona de entrega`,
  };
  protected readonly labels = {
    active: $localize`:@@admin.settings.active:Activa`,
    inactive: $localize`:@@admin.settings.inactive:Inactiva`,
    createHeader: $localize`:@@admin.settings.zones.createHeader:Agregar zona`,
    editHeader: $localize`:@@admin.settings.zones.editHeader:Editar zona`,
    nameInvalid: $localize`:@@admin.settings.zones.nameInvalid:Escribe un nombre válido.`,
    feeInvalid: $localize`:@@admin.settings.zones.feeInvalid:Escribe un monto, como 4.00.`,
    minutesInvalid: $localize`:@@admin.settings.zones.minutesInvalid:Escribe un número entero mayor que cero.`,
    minimumInvalid: $localize`:@@admin.settings.zones.minimumInvalid:Escribe un monto, como 15.00, o déjalo vacío.`,
    freeInvalid: $localize`:@@admin.settings.zones.freeInvalid:Escribe un monto, como 50.00, o déjalo vacío.`,
  };
  protected readonly columns: readonly DataColumn[] = [
    { field: 'name', header: $localize`:@@admin.settings.zones.name:Nombre`, sortable: true, filter: true },
    { field: 'feeMinor', header: $localize`:@@admin.settings.zones.fee:Tarifa`, sortable: true, searchable: false },
    {
      field: 'deliveryMinutes',
      header: $localize`:@@admin.settings.zones.minutes:Minutos de reparto`,
      sortable: true,
    },
    { field: '', header: $localize`:@@admin.settings.zones.minimum:Pedido mínimo` },
    { field: '', header: $localize`:@@admin.settings.zones.free:Reparto gratis desde` },
    { field: 'stateLabel', header: $localize`:@@admin.settings.zones.state:Estado`, sortable: true },
    { field: '', header: $localize`:@@admin.settings.actions:Acciones` },
  ];
  private readonly api = inject(SettingsApi);
  private readonly notifier = inject(ErrorNotifier);

  protected readonly changes = new UndoableChanges<boolean>(inject(UndoNotice));
  protected readonly zones = signal<readonly DeliveryZone[]>([]);
  protected readonly currency = signal('');
  protected readonly editing = signal<DeliveryZone | null>(null);
  protected readonly open = signal(false);
  protected readonly saving = signal(false);
  protected readonly numberAriaInvalid = numberAriaInvalid;
  protected readonly pending = new PendingIds();
  protected readonly rowsView = computed(() =>
    this.zones().map((zone) => {
      const shownActive = this.changes.pending(zone.id) ?? zone.active;
      return {
        ...zone,
        feeText: formatMoneyOrDash(zone.fee),
        feeMinor: safeMinorUnits(zone.fee) ?? 0,
        minimumText: formatMoneyOrDash(zone.minimumOrder),
        freeText: formatMoneyOrDash(zone.freeDeliveryThreshold),
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
    inject(DestroyRef).onDestroy(() => this.changes.flush());
    this.load();
    this.api.settings().subscribe({
      next: ({ value }) => this.currency.set(value.currency),
      error: (error: unknown) => this.notifier.show(error),
    });
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

  protected edit(zone: DeliveryZone): void {
    this.editing.set(zone);
    this.fill(zone);
    this.open.set(true);
  }

  protected close(): void {
    this.open.set(false);
    this.editing.set(null);
    this.resetForm();
  }

  protected toggle(zone: DeliveryZone): void {
    if (this.pending.has(zone.id) || this.changes.has(zone.id)) {
      return;
    }
    if (zone.active) {
      this.changes.schedule(
        zone.id,
        false,
        $localize`:@@admin.settings.zones.deactivated:Zona "${zone.name}:name:" desactivada`,
        () => this.api.deactivateZone(zone.id, zone.etag).pipe(tap((saved) => this.replace(saved))),
        (error) => this.fail(error, undefined, zone.id),
      );
      return;
    }
    this.pending.add(zone.id);
    this.api.reactivateZone(zone.id, zone.etag).subscribe({
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
        this.close();
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

  private fill(zone: DeliveryZone): void {
    this.form.reset({
      name: zone.name,
      fee: zone.fee.amount,
      deliveryMinutes: zone.deliveryMinutes,
      minimumOrder: zone.minimumOrder?.amount ?? '',
      freeDeliveryThreshold: zone.freeDeliveryThreshold?.amount ?? '',
    });
  }

  private resetForm(): void {
    this.form.reset({
      name: '',
      fee: '',
      deliveryMinutes: null,
      minimumOrder: '',
      freeDeliveryThreshold: '',
    });
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
