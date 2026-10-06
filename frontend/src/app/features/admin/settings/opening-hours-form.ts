import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormArray,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ButtonDirective } from 'primeng/button';
import { Checkbox } from 'primeng/checkbox';
import { InputText } from 'primeng/inputtext';
import type { DayOfWeek, OpeningHour } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { isStale, reportFailure } from '../admin-errors';
import { SettingsApi, Versioned } from './settings-api';

const WEEK: readonly DayOfWeek[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];

const DAY_LABELS: Record<DayOfWeek, string> = {
  MONDAY: $localize`:@@admin.settings.hours.monday:Lunes`,
  TUESDAY: $localize`:@@admin.settings.hours.tuesday:Martes`,
  WEDNESDAY: $localize`:@@admin.settings.hours.wednesday:Miércoles`,
  THURSDAY: $localize`:@@admin.settings.hours.thursday:Jueves`,
  FRIDAY: $localize`:@@admin.settings.hours.friday:Viernes`,
  SATURDAY: $localize`:@@admin.settings.hours.saturday:Sábado`,
  SUNDAY: $localize`:@@admin.settings.hours.sunday:Domingo`,
};

type DayRow = FormGroup<{
  day: FormControl<DayOfWeek>;
  closed: FormControl<boolean>;
  opensAt: FormControl<string>;
  closesAt: FormControl<string>;
}>;

// The API sends HH:mm:ss; the time input only deals in HH:mm.
function toInput(time: string | null): string {
  return time ? time.slice(0, 5) : '';
}

function toApi(time: string): string {
  return time.length === 5 ? `${time}:00` : time;
}

function buildRow(hour: OpeningHour): DayRow {
  const row: DayRow = new FormGroup({
    day: new FormControl(hour.dayOfWeek, { nonNullable: true }),
    closed: new FormControl(hour.closed, { nonNullable: true }),
    opensAt: new FormControl(toInput(hour.opensAt), {
      nonNullable: true,
      validators: [Validators.required],
    }),
    closesAt: new FormControl(toInput(hour.closesAt), {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });
  const syncTimes = (closed: boolean) => {
    for (const control of [row.controls.opensAt, row.controls.closesAt]) {
      if (closed) {
        control.disable({ emitEvent: false });
      } else {
        control.enable({ emitEvent: false });
      }
    }
  };
  syncTimes(hour.closed);
  row.controls.closed.valueChanges.subscribe(syncTimes);
  return row;
}

@Component({
  selector: 'app-opening-hours-form',
  imports: [ReactiveFormsModule, ButtonDirective, Checkbox, InputText],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../admin-section.scss',
  template: `
    <h2 i18n="@@admin.settings.hours.title">Horario de atención</h2>
    <p class="muted" i18n="@@admin.settings.hours.hint">
      Horas locales de la tienda. Si una hora de cierre es anterior a la de apertura, el local cierra
      después de medianoche.
    </p>

    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <table formArrayName="days">
        <thead>
          <tr>
            <th i18n="@@admin.settings.hours.day">Día</th>
            <th i18n="@@admin.settings.hours.closed">Cerrado</th>
            <th i18n="@@admin.settings.hours.opens">Abre</th>
            <th i18n="@@admin.settings.hours.closes">Cierra</th>
          </tr>
        </thead>
        <tbody>
          @for (row of rows(); track row; let i = $index) {
            <tr [formGroupName]="i">
              <th [attr.scope]="'row'">{{ dayLabel(row.controls.day.value) }}</th>
              <td>
                <p-checkbox
                  formControlName="closed"
                  [binary]="true"
                  [inputId]="'hours-' + row.controls.day.value + '-closed'"
                  [ariaLabel]="closedLabel(row.controls.day.value)"
                />
              </td>
              <td>
                <input
                  pInputText
                  type="time"
                  formControlName="opensAt"
                  [id]="'hours-' + row.controls.day.value + '-opens'"
                  [attr.aria-label]="opensLabel(row.controls.day.value)"
                  [attr.aria-invalid]="invalid(row.controls.opensAt) ? 'true' : null"
                />
              </td>
              <td>
                <input
                  pInputText
                  type="time"
                  formControlName="closesAt"
                  [id]="'hours-' + row.controls.day.value + '-closes'"
                  [attr.aria-label]="closesLabel(row.controls.day.value)"
                  [attr.aria-invalid]="invalid(row.controls.closesAt) ? 'true' : null"
                />
                @if (overnight(row)) {
                  <small class="muted" [attr.data-testid]="'overnight-' + row.controls.day.value" i18n="@@admin.settings.hours.overnight"
                    >Cierra al día siguiente</small
                  >
                }
              </td>
            </tr>
          }
        </tbody>
      </table>

      <div class="actions">
        <button pButton type="submit" [loading]="saving()" i18n="@@admin.settings.hours.save">
          Guardar horario
        </button>
      </div>
      @if (saved()) {
        <p role="status" i18n="@@admin.settings.hours.saved">Horario guardado.</p>
      }
    </form>
  `,
})
export class OpeningHoursForm {
  private readonly api = inject(SettingsApi);
  private readonly notifier = inject(ErrorNotifier);

  protected readonly saving = signal(false);
  protected readonly saved = signal(false);
  protected readonly rows = signal<readonly DayRow[]>([]);
  private readonly days = new FormArray<DayRow>([]);
  protected readonly form = new FormGroup({ days: this.days });
  private etag = '';
  private lastRead = 0;

  constructor() {
    this.form.valueChanges
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe(() => this.saved.set(false));
    this.load();
  }

  protected dayLabel(day: DayOfWeek): string {
    return DAY_LABELS[day];
  }

  protected closedLabel(day: DayOfWeek): string {
    return $localize`:@@admin.settings.hours.closedLabel:${DAY_LABELS[day]}:day:: cerrado`;
  }

  protected opensLabel(day: DayOfWeek): string {
    return $localize`:@@admin.settings.hours.opensLabel:${DAY_LABELS[day]}:day:: hora de apertura`;
  }

  protected closesLabel(day: DayOfWeek): string {
    return $localize`:@@admin.settings.hours.closesLabel:${DAY_LABELS[day]}:day:: hora de cierre`;
  }

  protected invalid(control: FormControl): boolean {
    return control.invalid && control.touched;
  }

  protected overnight(row: DayRow): boolean {
    const { closed, opensAt, closesAt } = row.getRawValue();
    return !closed && opensAt !== '' && closesAt !== '' && closesAt < opensAt;
  }

  protected submit(): void {
    if (this.saving()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const week = this.days.getRawValue().map((row) =>
      row.closed
        ? { dayOfWeek: row.day, closed: true, opensAt: null, closesAt: null }
        : {
            dayOfWeek: row.day,
            closed: false,
            opensAt: toApi(row.opensAt),
            closesAt: toApi(row.closesAt),
          },
    );
    this.saving.set(true);
    this.api.replaceOpeningHours(this.etag, week).subscribe({
      next: (result) => {
        this.saving.set(false);
        this.apply(result);
        this.saved.set(true);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        reportFailure(this.notifier, error);
        if (isStale(error)) {
          this.load();
        }
      },
    });
  }

  private apply({ value, etag }: Versioned<OpeningHour[]>): void {
    this.etag = etag;
    const ordered = [...value].sort((a, b) => WEEK.indexOf(a.dayOfWeek) - WEEK.indexOf(b.dayOfWeek));
    this.days.clear({ emitEvent: false });
    const rows = ordered.map(buildRow);
    for (const row of rows) {
      this.days.push(row, { emitEvent: false });
    }
    this.rows.set(rows);
    this.form.markAsPristine();
    this.form.markAsUntouched();
    this.form.updateValueAndValidity();
  }

  private load(): void {
    const read = ++this.lastRead;
    this.api.openingHours().subscribe({
      next: (result) => {
        if (read === this.lastRead) {
          this.apply(result);
        }
      },
      error: (error: unknown) => this.notifier.show(error),
    });
  }
}
