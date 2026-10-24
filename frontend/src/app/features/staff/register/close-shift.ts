import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { map } from 'rxjs';
import { IconCheck, IconLock, IconX, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import { formatMoneyOrDash, parseAmount } from '../../../core/money/money';
import { FormField } from '../../../shared/ui/form-field/form-field';
import { MoneyInput } from '../../../shared/ui/money-input/money-input';
import { ShiftResult } from './shift-result';
import { ShiftStore } from './shift-store';

// The close is blind: the count is typed before any expected cash exists on the screen.
@Component({
  selector: 'app-close-shift',
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    Dialog,
    Textarea,
    Tooltip,
    TablerIconComponent,
    ShiftResult,
    FormField,
    MoneyInput,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './register-panel.scss',
  template: `
    <p-dialog
      header="Cerrar turno"
      i18n-header="@@staff.register.close.title"
      [modal]="true"
      [draggable]="false"
      [closable]="!closing()"
      [closeOnEscape]="false"
      [dismissableMask]="false"
      [style]="{ width: 'min(36rem, calc(100vw - 1rem))' }"
      [visible]="visible()"
      (visibleChange)="visibleChange($event)"
    >
      <div class="close-body" tabindex="-1" (keydown.escape)="escape($event)">
        @if (store.closedShift(); as closed) {
          <app-shift-result [shift]="closed" [timeZone]="store.timeZone()" />
        } @else {
          <form id="close-form" [attr.aria-busy]="closing()" (submit)="submit($event)" novalidate>
            <p class="muted" i18n="@@staff.register.close.hint">
              Cuenta el efectivo de la caja y escribe el monto. Después verás cuánto se esperaba.
            </p>
            <app-form-field
              label="Efectivo contado"
              i18n-label="@@staff.register.close.count"
              controlId="close-count"
              [error]="countError() ? amountInvalid : null"
            >
              <app-money-input
                inputId="close-count"
                [formControl]="count"
                [currency]="store.currency()"
                [readonly]="store.noteNeeded() !== null"
              />
            </app-form-field>
            @if (store.noteNeeded(); as needed) {
              <p data-testid="close-difference">
                <ng-container i18n="@@staff.register.close.difference">Diferencia:</ng-container>
                <strong>{{ money(needed.difference) }}</strong>
              </p>
              <p class="muted" i18n="@@staff.register.close.noteHint">
                La diferencia supera el umbral permitido. El monto contado ya no puede cambiar: escribe una nota para cerrar.
              </p>
              <app-form-field label="Nota" i18n-label="@@staff.register.close.note" controlId="close-note">
                <textarea
                  pTextarea
                  id="close-note"
                  data-testid="close-note"
                  [formControl]="note"
                  maxlength="280"
                  rows="3"
                  fluid
                ></textarea>
              </app-form-field>
            }
            @if (store.countLocked()) {
              <p class="error" role="alert" data-testid="count-locked" i18n="@@staff.register.close.locked">
                El efectivo contado ya se mostró y no puede cambiar. Escribe el mismo monto de antes.
              </p>
            }
          </form>
        }
      </div>
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
            [disabled]="closing()"
            pTooltip="Cerrar sin cerrar el turno"
            i18n-pTooltip="@@staff.register.close.cancel.tooltip"
            tooltipPosition="top"
            (click)="dismissed.emit()"
          >
            <tabler-icon [icon]="icons.cancel" aria-hidden="true" />
            <span i18n="@@staff.register.close.cancel">Cancelar</span>
          </button>
          @if (!canConfirm() && why(); as reason) {
            <small class="why" data-testid="close-why" id="close-why">{{ reason }}</small>
          }
          <button
            pButton
            type="submit"
            [attr.form]="'close-form'"
            [attr.aria-describedby]="!canConfirm() && why() ? 'close-why' : null"
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
  protected readonly money = formatMoneyOrDash;
  protected readonly amountInvalid = $localize`:@@staff.register.close.countInvalid:Escribe un monto, como 90 o 90.00.`;
  protected readonly count = new FormControl('', { nonNullable: true });
  protected readonly note = new FormControl('', { nonNullable: true });
  private readonly typedCount = toSignal(this.count.valueChanges, { initialValue: '' });
  private readonly typedNote = toSignal(this.note.valueChanges, { initialValue: '' });
  protected readonly countError = toSignal(
    this.count.events.pipe(map(() => this.count.invalid && this.count.touched)),
    { initialValue: false },
  );
  protected readonly why = computed(() => {
    if (parseAmount(this.typedCount()) === null) {
      return $localize`:@@staff.register.close.why.count:Escribe el efectivo contado para cerrar el turno.`;
    }
    return this.store.noteNeeded() !== null && this.typedNote().trim() === ''
      ? $localize`:@@staff.register.close.why.note:Escribe la nota de la diferencia para cerrar el turno.`
      : null;
  });
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
          this.count.reset(this.store.noteNeeded()?.count ?? '');
          this.note.reset();
        });
      }
    });
  }

  protected visibleChange(visible: boolean): void {
    if (!visible && !this.closing()) {
      this.dismissed.emit();
    }
  }

  // p-dialog reads closeOnEscape once, so Escape is handled here and ignored while the request runs.
  protected escape(event: Event): void {
    if (!event.defaultPrevented && !this.closing()) {
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
