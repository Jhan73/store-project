import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { IconCheck, IconClockStop, IconRefresh, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import type { Shift } from '../../../core/api/api-types';
import { formatMoneyOrDash, safeMinorUnits } from '../../../core/money/money';
import { DataColumn, DataTable } from '../../../shared/ui/data-table/data-table';
import { FormDialog } from '../../../shared/ui/form-dialog/form-dialog';
import { FormField } from '../../../shared/ui/form-field/form-field';
import { OpenShiftsStore } from './open-shifts-store';
import { ShiftResult } from './shift-result';

// Admin only: closes a shift whose cashier left without counting the drawer.
@Component({
  selector: 'app-open-shifts',
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    Dialog,
    Textarea,
    Tooltip,
    TablerIconComponent,
    DataTable,
    FormDialog,
    FormField,
    ShiftResult,
  ],
  providers: [OpenShiftsStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './register-panel.scss',
  template: `
    <h1 i18n="@@staff.register.shifts.title">Turnos abiertos</h1>

    @if (store.failed()) {
      <div class="offline" role="status" data-testid="load-failed">
        <ng-container i18n="@@staff.register.shifts.failed">No se pudieron cargar los turnos.</ng-container>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="retry"
          (click)="store.load()"
          pTooltip="Volver a cargar los turnos abiertos"
          i18n-pTooltip="@@staff.register.shifts.retry.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.retry" aria-hidden="true" />
          <span i18n="@@staff.register.shifts.retry">Reintentar</span>
        </button>
      </div>
    } @else if (store.shifts(); as shifts) {
      <app-data-table
        label="Turnos abiertos"
        i18n-label="@@staff.register.shifts.scroll"
        [value]="rowsView()"
        [columns]="columns"
        [emptyText]="emptyText"
        [searchable]="false"
      >
        <ng-template #row let-shift>
          <td>{{ shift.cashier }}</td>
          <td>{{ shift.sinceText }}</td>
          <td>{{ shift.floatText }}</td>
          <td class="actions">
            <div class="row-actions">
              <button
                pButton
                type="button"
                severity="danger"
                [size]="'small'"
                [attr.data-testid]="'force-' + shift.id"
                [disabled]="store.pending.has('force:' + shift.id)"
                pTooltip="Cerrar este turno sin contar la caja"
                i18n-pTooltip="@@staff.register.shifts.force.tooltip"
                tooltipPosition="top"
                (click)="start(shift)"
              >
                <tabler-icon [icon]="icons.force" aria-hidden="true" />
                <span i18n="@@staff.register.shifts.force">Cierre forzado</span>
              </button>
            </div>
          </td>
        </ng-template>
      </app-data-table>
    } @else {
      <p class="muted" i18n="@@staff.register.shifts.loading">Cargando los turnos…</p>
    }

    <app-form-dialog
      header="Cierre forzado"
      i18n-header="@@staff.register.force.title"
      width="30rem"
      [visible]="forcing() !== null"
      [busy]="forceBusy()"
      [saveDisabled]="!canConfirm()"
      [saveLabel]="labels.forceSave"
      [saveTooltip]="labels.forceTooltip"
      [saveIcon]="icons.force"
      saveSeverity="danger"
      [disabledReason]="needsReason() ? labels.forceWhy : null"
      (saved)="confirm()"
      (dismissed)="closeForce(false)"
    >
      @if (forcing(); as shift) {
        <div class="dialog-fields">
          <p>
            <ng-container i18n="@@staff.register.force.who">Turno de</ng-container>
            <strong>{{ store.nameOf(shift.cashierId) }}</strong>
          </p>
          <app-form-field label="Motivo" i18n-label="@@staff.register.force.reason" controlId="force-reason">
            <textarea
              pTextarea
              id="force-reason"
              data-testid="force-reason"
              [formControl]="reason"
              maxlength="280"
              rows="3"
              fluid
            ></textarea>
          </app-form-field>
        </div>
      }
    </app-form-dialog>

    <p-dialog
      header="Turno cerrado"
      i18n-header="@@staff.register.forced.title"
      [modal]="true"
      [draggable]="false"
      [style]="{ width: 'min(36rem, calc(100vw - 1rem))' }"
      [visible]="store.forced() !== null"
      (visibleChange)="closeForced($event)"
    >
      @if (store.forced(); as forced) {
        <app-shift-result [shift]="forced" [timeZone]="store.timeZone()" />
      }
      <ng-template #footer>
        <button
          pButton
          type="button"
          data-testid="forced-done"
          pTooltip="Cerrar este resumen"
          i18n-pTooltip="@@staff.register.forced.done.tooltip"
          tooltipPosition="top"
          (click)="closeForced(false)"
        >
          <tabler-icon [icon]="icons.confirm" aria-hidden="true" />
          <span i18n="@@staff.register.forced.done">Listo</span>
        </button>
      </ng-template>
    </p-dialog>
  `,
})
export class OpenShifts {
  protected readonly store = inject(OpenShiftsStore);

  protected readonly icons = { force: IconClockStop, retry: IconRefresh, confirm: IconCheck };
  protected readonly labels = {
    forceSave: $localize`:@@staff.register.force.confirm:Forzar cierre`,
    forceTooltip: $localize`:@@staff.register.force.confirm.tooltip:Cerrar el turno con este motivo`,
    forceWhy: $localize`:@@staff.register.force.why:Escribe el motivo para forzar el cierre.`,
  };
  protected readonly emptyText = $localize`:@@staff.register.shifts.empty:No hay turnos abiertos.`;
  protected readonly columns: readonly DataColumn[] = [
    { field: 'cashier', header: $localize`:@@staff.register.shifts.cashier:Cajero`, sortable: true },
    { field: 'openedAt', header: $localize`:@@staff.register.shifts.since:Desde`, sortable: true },
    { field: 'floatMinor', header: $localize`:@@staff.register.shifts.float:Fondo inicial`, sortable: true },
    { field: '', header: $localize`:@@staff.register.shifts.actions:Acciones` },
  ];
  protected readonly money = formatMoneyOrDash;
  protected readonly rowsView = computed(() =>
    (this.store.shifts() ?? []).map((shift) => ({
      ...shift,
      cashier: this.store.nameOf(shift.cashierId),
      sinceText: this.store.formatTime(shift.openedAt),
      floatText: formatMoneyOrDash(shift.openingFloat),
      floatMinor: safeMinorUnits(shift.openingFloat) ?? 0,
    })),
  );
  protected readonly needsReason = computed(() => this.typedReason().trim() === '');
  protected readonly forceBusy = computed(() => {
    const shift = this.forcing();
    return shift !== null && this.store.pending.has(`force:${shift.id}`);
  });
  protected readonly forcing = signal<Shift | null>(null);
  protected readonly reason = new FormControl('', { nonNullable: true });
  private readonly typedReason = toSignal(this.reason.valueChanges, { initialValue: '' });
  protected readonly canConfirm = computed(() => {
    const shift = this.forcing();
    return shift !== null && this.typedReason().trim() !== '' && !this.store.pending.has(`force:${shift.id}`);
  });

  constructor() {
    this.store.load();
  }

  protected start(shift: Shift): void {
    this.reason.reset();
    this.forcing.set(shift);
  }

  protected closeForce(visible: boolean): void {
    if (!visible) {
      this.forcing.set(null);
    }
  }

  protected closeForced(visible: boolean): void {
    if (!visible) {
      this.store.dismissForced();
    }
  }

  protected confirm(): void {
    const shift = this.forcing();
    if (shift !== null && this.canConfirm()) {
      this.store.forceClose(shift, this.reason.value.trim(), () => this.forcing.set(null));
    }
  }
}
