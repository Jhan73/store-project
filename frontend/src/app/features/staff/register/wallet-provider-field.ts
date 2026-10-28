import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { IconRefresh, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Select } from 'primeng/select';
import { Tooltip } from 'primeng/tooltip';
import type { WalletProviderOption } from '../../../core/api/api-types';
import { FormField } from '../../../shared/ui/form-field/form-field';

// The wallet picker of a payment form; the owner decides when it is shown and reads the control.
@Component({
  selector: 'app-wallet-provider-field',
  imports: [ReactiveFormsModule, Select, FormField, ButtonDirective, Tooltip, TablerIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-form-field label="Billetera" i18n-label="@@staff.wallet.label" [controlId]="controlId()">
      <p-select
        [inputId]="controlId()"
        [ariaLabel]="ariaLabel"
        [formControl]="control()"
        optionLabel="name"
        optionValue="id"
        [options]="providers()"
        [placeholder]="placeholder"
        [filter]="true"
        filterBy="name"
        [resetFilterOnHide]="true"
        filterPlaceholder="Buscar"
        i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
        [appendTo]="'body'"
        fluid
      />
    </app-form-field>
    @if (failed()) {
      <button
        pButton
        type="button"
        severity="secondary"
        data-testid="wallet-retry"
        pTooltip="Volver a leer las billeteras"
        i18n-pTooltip="@@staff.wallet.retry.tooltip"
        tooltipPosition="top"
        (click)="retry.emit()"
      >
        <tabler-icon [icon]="icons.retry" aria-hidden="true" />
        <span i18n="@@staff.wallet.retry">Reintentar</span>
      </button>
    }
  `,
})
export class WalletProviderField {
  readonly control = input.required<FormControl<string | null>>();
  readonly providers = input.required<WalletProviderOption[]>();
  readonly controlId = input.required<string>();
  readonly failed = input(false);
  readonly retry = output<void>();

  protected readonly icons = { retry: IconRefresh };

  protected readonly ariaLabel = $localize`:@@staff.wallet.aria:Billetera`;
  protected readonly placeholder = $localize`:@@staff.wallet.placeholder:Elige la billetera`;
}
