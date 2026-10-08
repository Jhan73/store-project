import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import {
  IconDeviceFloppy,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { InputNumber } from 'primeng/inputnumber';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Tooltip } from 'primeng/tooltip';
import { numberAriaInvalid, selectAriaInvalid } from '../../../shared/forms/aria-invalid';
import type { StoreSettings } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { parseAmount } from '../../../core/money/money';
import { isStale, reportFailure } from '../admin-errors';
import { wholeNumber } from '../admin-validators';
import { SettingsApi, Versioned } from './settings-api';

type NumberName =
  | 'basePrepMinutes'
  | 'queueMinutesPerOrder'
  | 'busyModeMinutes'
  | 'onlineCapacityLimit'
  | 'boardWarningMinutes'
  | 'boardLateMinutes'
  | 'exceptionThreshold';

interface NumberField {
  readonly name: NumberName;
  readonly label: string;
  readonly min: number;
}

const PREPARATION_FIELDS: readonly NumberField[] = [
  {
    name: 'basePrepMinutes',
    label: $localize`:@@admin.settings.general.basePrepMinutes:Minutos base de preparación`,
    min: 1,
  },
  {
    name: 'queueMinutesPerOrder',
    label: $localize`:@@admin.settings.general.queueMinutesPerOrder:Minutos extra por pedido en cola`,
    min: 0,
  },
  {
    name: 'busyModeMinutes',
    label: $localize`:@@admin.settings.general.busyModeMinutes:Minutos extra en modo ocupado`,
    min: 0,
  },
  {
    name: 'onlineCapacityLimit',
    label: $localize`:@@admin.settings.general.onlineCapacityLimit:Límite de pedidos en línea simultáneos`,
    min: 1,
  },
];

const BOARD_FIELDS: readonly NumberField[] = [
  {
    name: 'boardWarningMinutes',
    label: $localize`:@@admin.settings.general.boardWarningMinutes:Minutos para marcar advertencia`,
    min: 1,
  },
  {
    name: 'boardLateMinutes',
    label: $localize`:@@admin.settings.general.boardLateMinutes:Minutos para marcar atraso`,
    min: 1,
  },
];

const EXCEPTION_FIELD: NumberField = {
  name: 'exceptionThreshold',
  label: $localize`:@@admin.settings.general.exceptionThreshold:Umbral de excepciones`,
  min: 1,
};

function lateAfterWarning(group: AbstractControl): ValidationErrors | null {
  const warning = group.get('boardWarningMinutes')?.value;
  const late = group.get('boardLateMinutes')?.value;
  return typeof warning === 'number' && typeof late === 'number' && late <= warning
    ? { boardOrder: true }
    : null;
}

function amountOrZero(control: AbstractControl): ValidationErrors | null {
  return parseAmount(String(control.value ?? '')) === null ? { amount: true } : null;
}

function timeZoneOptions(current: string): string[] {
  const all = typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : [];
  return all.includes(current) ? all : [current, ...all];
}

@Component({
  selector: 'app-settings-form',
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    InputNumber,
    InputText,
    Select,
    NgTemplateOutlet,
    TablerIconComponent,
    Tooltip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../admin-section.scss',
  template: `
    <h2 i18n="@@admin.settings.general.title">Configuración de la tienda</h2>

    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <ng-template #numberInput let-field>
        <div class="field">
          <label [for]="'settings-' + field.name">{{ field.label }}</label>
          <p-inputnumber
            [inputId]="'settings-' + field.name"
            [formControlName]="field.name"
            [useGrouping]="false"
            [invalid]="invalid(field.name)"
            [pt]="numberAriaInvalid(invalid(field.name))"
          />
          @if (invalid(field.name)) {
            <small class="error">{{ numberInvalid(field) }}</small>
          }
        </div>
      </ng-template>

      <fieldset>
        <legend i18n="@@admin.settings.general.regional">Región</legend>
        <div class="field">
          <label
            for="settings-timeZone"
            id="settings-timeZone-label"
            i18n="@@admin.settings.general.timeZone"
            >Zona horaria</label
          >
          <p-select
            inputId="settings-timeZone"
            [ariaLabelledBy]="'settings-timeZone-label'"
            formControlName="timeZone"
            [options]="timeZones()"
            [filter]="true"
            [resetFilterOnHide]="true"
            filterPlaceholder="Buscar"
            i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
            [invalid]="invalid('timeZone')"
            [pt]="selectAriaInvalid(invalid('timeZone'))"
          />
        </div>
        <div class="field">
          <label for="settings-currency" i18n="@@admin.settings.general.currency">Moneda (ISO 4217)</label>
          <input
            pInputText
            id="settings-currency"
            formControlName="currency"
            maxlength="3"
            autocomplete="off"
            [attr.aria-invalid]="invalid('currency') ? 'true' : null"
          />
          @if (invalid('currency')) {
            <small class="error" i18n="@@admin.settings.general.currencyInvalid"
              >Escribe un código de tres letras, como PEN.</small
            >
          }
          @if (currencyChanged()) {
            <small id="settings-currency-warning" role="note" i18n="@@admin.settings.general.currencyChangeWarning"
              >Las zonas de reparto existentes conservan la moneda anterior; guarda cada una de nuevo para
              actualizarla.</small
            >
          }
        </div>
      </fieldset>

      <fieldset>
        <legend i18n="@@admin.settings.general.preparation">Preparación y pedidos en línea</legend>
        @for (field of preparationFields; track field.name) {
          <ng-container *ngTemplateOutlet="numberInput; context: { $implicit: field }" />
        }
      </fieldset>

      <fieldset>
        <legend i18n="@@admin.settings.general.board">Tablero de preparación</legend>
        @for (field of boardFields; track field.name) {
          <ng-container *ngTemplateOutlet="numberInput; context: { $implicit: field }" />
        }
        @if (form.hasError('boardOrder') && form.controls.boardLateMinutes.touched) {
          <small class="error" i18n="@@admin.settings.general.boardOrderInvalid"
            >El tiempo de atraso debe ser mayor que el de advertencia.</small
          >
        }
      </fieldset>

      <fieldset>
        <legend i18n="@@admin.settings.general.cash">Caja y excepciones</legend>
        <div class="field">
          <label for="settings-registerDifferenceThreshold" i18n="@@admin.settings.general.registerDifference"
            >Diferencia de caja permitida ({{ currency() }})</label
          >
          <input
            pInputText
            id="settings-registerDifferenceThreshold"
            formControlName="registerDifferenceThreshold"
            [attr.inputmode]="'decimal'"
            autocomplete="off"
            [attr.aria-invalid]="invalid('registerDifferenceThreshold') ? 'true' : null"
          />
          @if (invalid('registerDifferenceThreshold')) {
            <small class="error" i18n="@@admin.settings.general.registerDifferenceInvalid"
              >Escribe un monto, como 5.00.</small
            >
          }
        </div>
        <ng-container *ngTemplateOutlet="numberInput; context: { $implicit: exceptionField }" />
      </fieldset>

      <div class="actions">
        <button
          pButton
          type="submit"
          [loading]="saving()"
          pTooltip="Guardar los cambios de la configuración"
          i18n-pTooltip="@@admin.settings.general.save.tooltip"
          tooltipPosition="top"
        >
          @if (!saving()) {
            <tabler-icon [icon]="icons.save" aria-hidden="true" />
          }
          <span i18n="@@admin.settings.general.save">Guardar cambios</span>
        </button>
      </div>
      @if (saved()) {
        <p role="status" i18n="@@admin.settings.general.saved">Cambios guardados.</p>
      }
    </form>
  `,
})
export class SettingsForm {
  protected readonly icons = { save: IconDeviceFloppy };
  private readonly api = inject(SettingsApi);
  private readonly notifier = inject(ErrorNotifier);

  protected readonly preparationFields = PREPARATION_FIELDS;
  protected readonly numberAriaInvalid = numberAriaInvalid;
  protected readonly selectAriaInvalid = selectAriaInvalid;
  protected readonly boardFields = BOARD_FIELDS;
  protected readonly exceptionField = EXCEPTION_FIELD;
  protected readonly timeZones = signal<string[]>([]);
  protected readonly currency = signal('');
  private readonly typedCurrency = signal('');
  protected readonly currencyChanged = computed(() => {
    const typed = this.typedCurrency().trim().toUpperCase();
    return typed.length === 3 && typed !== this.currency();
  });
  protected readonly saving = signal(false);
  protected readonly saved = signal(false);
  private etag = '';
  private lastRead = 0;
  protected readonly form = new FormGroup(
    {
      timeZone: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
      currency: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, Validators.pattern(/^\s*[A-Za-z]{3}\s*$/)],
      }),
      basePrepMinutes: new FormControl<number | null>(null, wholeNumber(1)),
      queueMinutesPerOrder: new FormControl<number | null>(null, wholeNumber(0)),
      busyModeMinutes: new FormControl<number | null>(null, wholeNumber(0)),
      onlineCapacityLimit: new FormControl<number | null>(null, wholeNumber(1)),
      boardWarningMinutes: new FormControl<number | null>(null, wholeNumber(1)),
      boardLateMinutes: new FormControl<number | null>(null, wholeNumber(1)),
      registerDifferenceThreshold: new FormControl('', {
        nonNullable: true,
        validators: [Validators.required, amountOrZero],
      }),
      exceptionThreshold: new FormControl<number | null>(null, wholeNumber(1)),
    },
    { validators: [lateAfterWarning] },
  );

  constructor() {
    this.form.valueChanges
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => {
        this.saved.set(false);
        this.typedCurrency.set(this.form.controls.currency.value);
      });
    this.load();
  }

  protected invalid(name: string): boolean {
    const control = this.form.get(name);
    return !!control && control.invalid && control.touched;
  }

  protected numberInvalid(field: NumberField): string {
    return $localize`:@@admin.settings.general.numberInvalid:Escribe un número entero de ${field.min}:min: o más.`;
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
    const currency = raw.currency.trim().toUpperCase();
    this.saving.set(true);
    this.api
      .updateSettings(this.etag, {
        timeZone: raw.timeZone,
        currency,
        basePrepMinutes: raw.basePrepMinutes ?? 0,
        queueMinutesPerOrder: raw.queueMinutesPerOrder ?? 0,
        busyModeMinutes: raw.busyModeMinutes ?? 0,
        onlineCapacityLimit: raw.onlineCapacityLimit ?? 0,
        boardWarningMinutes: raw.boardWarningMinutes ?? 0,
        boardLateMinutes: raw.boardLateMinutes ?? 0,
        registerDifferenceThreshold: {
          amount: parseAmount(raw.registerDifferenceThreshold) ?? '0.00',
          currency,
        },
        exceptionThreshold: raw.exceptionThreshold ?? 0,
      })
      .subscribe({
        next: (result) => {
          this.saving.set(false);
          this.apply(result);
          this.saved.set(true);
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

  private apply({ value, etag }: Versioned<StoreSettings>): void {
    this.etag = etag;
    this.currency.set(value.currency);
    this.timeZones.set(timeZoneOptions(value.timeZone));
    this.form.reset({
      timeZone: value.timeZone,
      currency: value.currency,
      basePrepMinutes: value.basePrepMinutes,
      queueMinutesPerOrder: value.queueMinutesPerOrder,
      busyModeMinutes: value.busyModeMinutes,
      onlineCapacityLimit: value.onlineCapacityLimit,
      boardWarningMinutes: value.boardWarningMinutes,
      boardLateMinutes: value.boardLateMinutes,
      registerDifferenceThreshold: value.registerDifferenceThreshold.amount,
      exceptionThreshold: value.exceptionThreshold,
    });
  }

  private load(): void {
    const read = ++this.lastRead;
    this.api.settings().subscribe({
      next: (result) => {
        if (read === this.lastRead) {
          this.apply(result);
        }
      },
      error: (error: unknown) => this.notifier.show(error),
    });
  }
}
