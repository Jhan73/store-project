import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { IconCheck, IconClockStop, IconRefresh, IconX, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import type { Shift } from '../../../core/api/api-types';
import { formatMoney } from '../../../core/money/money';
import { TableScroll } from '../../../shared/ui/table-scroll/table-scroll';
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
    TableScroll,
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
      @if (shifts.length === 0) {
        <p class="muted" data-testid="no-open-shifts" i18n="@@staff.register.shifts.empty">
          No hay turnos abiertos.
        </p>
      } @else {
        <app-table-scroll label="Turnos abiertos" i18n-label="@@staff.register.shifts.scroll">
          <table>
            <thead>
              <tr>
                <th i18n="@@staff.register.shifts.cashier">Cajero</th>
                <th i18n="@@staff.register.shifts.since">Desde</th>
                <th i18n="@@staff.register.shifts.float">Fondo inicial</th>
                <th><span i18n="@@staff.register.shifts.actions">Acciones</span></th>
              </tr>
            </thead>
            <tbody>
              @for (shift of shifts; track shift.id) {
                <tr>
                  <td>{{ store.nameOf(shift.cashierId) }}</td>
                  <td>{{ store.formatTime(shift.openedAt) }}</td>
                  <td>{{ money(shift.openingFloat) }}</td>
                  <td>
                    <button
                      pButton
                      type="button"
                      severity="danger"
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
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </app-table-scroll>
      }
    } @else {
      <p class="muted" i18n="@@staff.register.shifts.loading">Cargando los turnos…</p>
    }

    <p-dialog
      header="Cierre forzado"
      i18n-header="@@staff.register.force.title"
      [modal]="true"
      [draggable]="false"
      [style]="{ width: 'min(30rem, calc(100vw - 1rem))' }"
      [visible]="forcing() !== null"
      (visibleChange)="closeForce($event)"
    >
      @if (forcing(); as shift) {
        <p>
          <ng-container i18n="@@staff.register.force.who">Turno de</ng-container>
          <strong>{{ store.nameOf(shift.cashierId) }}</strong>
        </p>
        <div class="field">
          <label for="force-reason" i18n="@@staff.register.force.reason">Motivo</label>
          <textarea
            pTextarea
            id="force-reason"
            data-testid="force-reason"
            [formControl]="reason"
            maxlength="280"
            rows="3"
            fluid
          ></textarea>
        </div>
      }
      <ng-template #footer>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="force-cancel"
          pTooltip="Cerrar sin forzar el cierre"
          i18n-pTooltip="@@staff.register.force.cancel.tooltip"
          tooltipPosition="top"
          (click)="closeForce(false)"
        >
          <tabler-icon [icon]="icons.cancel" aria-hidden="true" />
          <span i18n="@@staff.register.force.cancel">Cancelar</span>
        </button>
        <button
          pButton
          type="button"
          severity="danger"
          data-testid="force-confirm"
          [disabled]="!canConfirm()"
          pTooltip="Cerrar el turno con este motivo"
          i18n-pTooltip="@@staff.register.force.confirm.tooltip"
          tooltipPosition="top"
          (click)="confirm()"
        >
          <tabler-icon [icon]="icons.force" aria-hidden="true" />
          <span i18n="@@staff.register.force.confirm">Forzar cierre</span>
        </button>
      </ng-template>
    </p-dialog>

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

  protected readonly icons = { force: IconClockStop, retry: IconRefresh, cancel: IconX, confirm: IconCheck };
  protected readonly money = formatMoney;
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
