import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { IconCheck, IconLock, IconX, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import { formatMoney, parseAmount } from '../../../core/money/money';
import { ShiftResult } from './shift-result';
import { ShiftStore } from './shift-store';

// The close is blind: the count is typed before any expected cash exists on the screen.
@Component({
  selector: 'app-close-shift',
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    Dialog,
    InputText,
    Textarea,
    Tooltip,
    TablerIconComponent,
    ShiftResult,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './register-panel.scss',
  template: `
    <p-dialog
      header="Cerrar turno"
      i18n-header="@@staff.register.close.title"
      [modal]="true"
      [draggable]="false"
      [style]="{ width: 'min(36rem, calc(100vw - 1rem))' }"
      [visible]="visible()"
      (visibleChange)="visibleChange($event)"
    >
      @if (store.closedShift(); as closed) {
        <app-shift-result [shift]="closed" [timeZone]="store.timeZone()" />
      } @else {
        <form id="close-form" (submit)="submit($event)">
          <p class="muted" i18n="@@staff.register.close.hint">
            Cuenta el efectivo de la caja y escribe el monto. Después verás cuánto se esperaba.
          </p>
          <div class="field">
            <label for="close-count" i18n="@@staff.register.close.count">Efectivo contado</label>
            <input
              pInputText
              id="close-count"
              data-testid="close-count"
              [formControl]="count"
              [readOnly]="store.noteNeeded() !== null"
              [attr.inputmode]="'decimal'"
              autocomplete="off"
              fluid
            />
          </div>
          @if (store.noteNeeded(); as needed) {
            <p data-testid="close-difference">
              <ng-container i18n="@@staff.register.close.difference">Diferencia:</ng-container>
              <strong>{{ money(needed.difference) }}</strong>
            </p>
            <p class="muted" i18n="@@staff.register.close.noteHint">
              La diferencia supera el umbral permitido. El monto contado ya no puede cambiar: escribe una nota para cerrar.
            </p>
            <div class="field">
              <label for="close-note" i18n="@@staff.register.close.note">Nota</label>
              <textarea
                pTextarea
                id="close-note"
                data-testid="close-note"
                [formControl]="note"
                maxlength="280"
                rows="3"
                fluid
              ></textarea>
            </div>
          }
          @if (store.countLocked()) {
            <p class="error" role="alert" data-testid="count-locked" i18n="@@staff.register.close.locked">
              El efectivo contado ya se mostró y no puede cambiar. Escribe el mismo monto de antes.
            </p>
          }
        </form>
      }
      <ng-template #footer>
        @if (store.closedShift()) {
          <button
            pButton
            type="button"
            data-testid="close-done"
            pTooltip="Cerrar este resumen"
            i18n-pTooltip="@@staff.register.close.done.tooltip"
            tooltipPosition="top"
            (click)="dismissed.emit()"
          >
            <tabler-icon [icon]="icons.confirm" aria-hidden="true" />
            <span i18n="@@staff.register.close.done">Listo</span>
          </button>
        } @else {
          <button
            pButton
            type="button"
            severity="secondary"
            data-testid="close-cancel"
            pTooltip="Cerrar sin cerrar el turno"
            i18n-pTooltip="@@staff.register.close.cancel.tooltip"
            tooltipPosition="top"
            (click)="dismissed.emit()"
          >
            <tabler-icon [icon]="icons.cancel" aria-hidden="true" />
            <span i18n="@@staff.register.close.cancel">Cancelar</span>
          </button>
          <button
            pButton
            type="submit"
            [attr.form]="'close-form'"
            severity="danger"
            data-testid="close-confirm"
            [loading]="closing()"
            [disabled]="!canConfirm() || closing()"
            pTooltip="Cerrar el turno con este efectivo contado"
            i18n-pTooltip="@@staff.register.close.confirm.tooltip"
            tooltipPosition="top"
          >
            @if (!closing()) {
              <tabler-icon [icon]="icons.lock" aria-hidden="true" />
            }
            <span i18n="@@staff.register.close.confirm">Cerrar turno</span>
          </button>
        }
      </ng-template>
    </p-dialog>
  `,
})
export class CloseShift {
  protected readonly store = inject(ShiftStore);

  readonly visible = input.required<boolean>();
  readonly dismissed = output<void>();

  protected readonly icons = { lock: IconLock, cancel: IconX, confirm: IconCheck };
  protected readonly money = formatMoney;
  protected readonly count = new FormControl('', { nonNullable: true });
  protected readonly note = new FormControl('', { nonNullable: true });
  private readonly typedCount = toSignal(this.count.valueChanges, { initialValue: '' });
  private readonly typedNote = toSignal(this.note.valueChanges, { initialValue: '' });
  protected readonly closing = computed(() => this.store.pending.has('close'));
  protected readonly canConfirm = computed(
    () =>
      parseAmount(this.typedCount()) !== null &&
      (this.store.noteNeeded() === null || this.typedNote().trim() !== ''),
  );

  constructor() {
    effect(() => {
      if (this.visible()) {
        untracked(() => {
          this.count.reset();
          this.note.reset();
        });
      }
    });
  }

  protected visibleChange(visible: boolean): void {
    if (!visible) {
      this.dismissed.emit();
    }
  }

  protected submit(event: Event): void {
    event.preventDefault();
    const count = parseAmount(this.count.value);
    if (count === null || !this.canConfirm() || this.closing()) {
      return;
    }
    const note = this.note.value.trim();
    this.store.close(count, note === '' ? null : note);
  }
}
