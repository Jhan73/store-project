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
import type { WalletProvider } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { ConfirmAction } from '../../../shared/state/confirm-action';
import { PendingIds } from '../../../shared/state/pending-ids';
import { DataColumn, DataTable } from '../../../shared/ui/data-table/data-table';
import { FormDialog } from '../../../shared/ui/form-dialog/form-dialog';
import { FormField } from '../../../shared/ui/form-field/form-field';
import { StatusTag } from '../../../shared/ui/status-tag/status-tag';
import { isStale, reportFailure } from '../admin-errors';
import { SettingsApi } from './settings-api';

@Component({
  selector: 'app-wallet-provider-list',
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
  styleUrl: '../admin-section.scss',
  template: `
    <div class="toolbar">
      <h2 i18n="@@admin.settings.wallets.title">Billeteras</h2>
      <button
        pButton
        type="button"
        data-testid="add-wallet"
        (click)="add()"
        pTooltip="Agregar una billetera que la caja pueda elegir al cobrar"
        i18n-pTooltip="@@admin.settings.wallets.create.tooltip"
        tooltipPosition="top"
      >
        <tabler-icon [icon]="icons.create" aria-hidden="true" />
        <span i18n="@@admin.settings.wallets.create">Agregar billetera</span>
      </button>
    </div>

    <app-data-table
      label="Listado de billeteras"
      i18n-label="@@admin.settings.wallets.tableScroll"
      emptyText="Todavía no hay billeteras."
      i18n-emptyText="@@admin.settings.wallets.empty"
      [value]="providers()"
      [columns]="columns"
    >
      <ng-template #row let-provider>
        <td>{{ provider.name }}</td>
        <td>
          <app-status-tag
            [value]="provider.active ? labels.active : labels.inactive"
            [severity]="provider.active ? 'success' : 'secondary'"
          />
        </td>
        <td class="actions">
          <div class="row-actions">
            <button
              pButton
              type="button"
              severity="secondary"
              [size]="'small'"
              [attr.data-testid]="'edit-' + provider.id"
              (click)="edit(provider)"
              pTooltip="Cambiar el nombre de esta billetera"
              i18n-pTooltip="@@admin.settings.wallets.edit.tooltip"
              tooltipPosition="top"
            >
              <tabler-icon [icon]="icons.edit" aria-hidden="true" />
              <span i18n="@@admin.settings.wallets.edit">Renombrar</span>
            </button>
            <button
              pButton
              type="button"
              severity="secondary"
              [size]="'small'"
              [outlined]="true"
              [disabled]="pending.has(provider.id)"
              [attr.data-testid]="'toggle-' + provider.id"
              (click)="toggle(provider)"
              [pTooltip]="provider.active ? tips.deactivate : tips.reactivate"
              tooltipPosition="top"
            >
              @if (provider.active) {
                <tabler-icon [icon]="icons.deactivate" aria-hidden="true" />
                <ng-container i18n="@@admin.settings.wallets.deactivate">Desactivar</ng-container>
              } @else {
                <tabler-icon [icon]="icons.reactivate" aria-hidden="true" />
                <ng-container i18n="@@admin.settings.wallets.reactivate">Reactivar</ng-container>
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
          label="Nombre de la billetera"
          i18n-label="@@admin.settings.wallets.nameLabel"
          controlId="wallet-name"
          [error]="invalid() ? labels.nameInvalid : null"
        >
          <input
            pInputText
            id="wallet-name"
            formControlName="name"
            autocomplete="off"
            maxlength="60"
            [attr.aria-invalid]="invalid() ? 'true' : null"
            fluid
          />
        </app-form-field>
      </div>
    </app-form-dialog>
  `,
})
export class WalletProviderList {
  protected readonly icons = {
    edit: IconPencil,
    create: IconPlus,
    deactivate: IconPlayerPause,
    reactivate: IconPlayerPlay,
  };
  protected readonly tips = {
    deactivate: $localize`:@@admin.settings.wallets.deactivate.tooltip:Dejar de ofrecer esta billetera al cobrar`,
    reactivate: $localize`:@@admin.settings.wallets.reactivate.tooltip:Volver a ofrecer esta billetera al cobrar`,
  };
  protected readonly labels = {
    active: $localize`:@@admin.settings.wallets.active:Activa`,
    inactive: $localize`:@@admin.settings.wallets.inactive:Inactiva`,
    createHeader: $localize`:@@admin.settings.wallets.createHeader:Agregar billetera`,
    editHeader: $localize`:@@admin.settings.wallets.editHeader:Renombrar billetera`,
    nameInvalid: $localize`:@@admin.settings.wallets.nameInvalid:Escribe un nombre válido.`,
  };
  protected readonly columns: readonly DataColumn[] = [
    { field: 'name', header: $localize`:@@admin.settings.wallets.name:Nombre`, sortable: true, filter: true },
    { field: 'active', header: $localize`:@@admin.settings.wallets.state:Estado`, sortable: true },
    { field: '', header: $localize`:@@admin.settings.wallets.actions:Acciones` },
  ];
  private readonly api = inject(SettingsApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly confirm = inject(ConfirmAction);

  protected readonly pending = new PendingIds();
  protected readonly providers = signal<readonly WalletProvider[]>([]);
  protected readonly editing = signal<WalletProvider | null>(null);
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

  protected edit(provider: WalletProvider): void {
    this.editing.set(provider);
    this.form.reset({ name: provider.name });
    this.open.set(true);
  }

  protected close(): void {
    this.open.set(false);
    this.editing.set(null);
    this.form.reset();
  }

  protected async toggle(provider: WalletProvider): Promise<void> {
    if (this.pending.has(provider.id)) {
      return;
    }
    if (provider.active) {
      const confirmed = await this.confirm.ask({
        header: $localize`:@@admin.settings.wallets.deactivate.header:Desactivar billetera`,
        message: $localize`:@@admin.settings.wallets.deactivate.confirm:La billetera "${provider.name}:name:" dejará de ofrecerse al cobrar. Los cobros ya registrados la conservan.`,
        acceptLabel: $localize`:@@admin.settings.wallets.deactivate:Desactivar`,
      });
      if (!confirmed) {
        return;
      }
    }
    this.pending.add(provider.id);
    const request = provider.active
      ? this.api.deactivateWalletProvider(provider.id, provider.etag)
      : this.api.reactivateWalletProvider(provider.id, provider.etag);
    request.subscribe({
      next: (saved) => {
        this.pending.delete(provider.id);
        this.providers.update((list) => list.map((item) => (item.id === saved.id ? saved : item)));
      },
      error: (error: unknown) => {
        this.pending.delete(provider.id);
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
      ? this.api.renameWalletProvider(target.id, target.etag, body)
      : this.api.createWalletProvider(body);
    this.saving.set(true);
    request.subscribe({
      next: (saved) => {
        this.providers.update((list) =>
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
    this.api.walletProviders().subscribe({
      next: (list) => {
        this.providers.set(list);
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
