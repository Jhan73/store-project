import { ChangeDetectionStrategy, Component, computed, effect, input, output, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { IconBan, IconRefresh, IconX, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Select } from 'primeng/select';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import type { ActiveReason } from '../../../core/api/api-types';
import { reasonLabel, requiresNote } from '../../../core/reasons/reason-rules';

export interface VoidChoice {
  readonly reason: string;
  readonly note: string | null;
}

// Presentational: the owner loads the reasons and runs the command; this only collects the choice.
@Component({
  selector: 'app-void-dialog',
  imports: [ReactiveFormsModule, ButtonDirective, Dialog, Select, Textarea, Tooltip, TablerIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-dialog
      [header]="header()"
      [modal]="true"
      [draggable]="false"
      [style]="{ width: 'min(30rem, calc(100vw - 1rem))' }"
      [visible]="visible()"
      (visibleChange)="visibleChange($event)"
    >
      <ng-content />
      @if (failed()) {
        <div data-testid="vd-failed" role="status">
          <p i18n="@@shared.voidDialog.failed">No se pudieron cargar los motivos.</p>
          <button
            pButton
            type="button"
            severity="secondary"
            data-testid="vd-retry"
            pTooltip="Volver a cargar los motivos"
            i18n-pTooltip="@@shared.voidDialog.retry.tooltip"
            tooltipPosition="top"
            (click)="retried.emit()"
          >
            <tabler-icon [icon]="icons.retry" aria-hidden="true" />
            <span i18n="@@shared.voidDialog.retry">Reintentar</span>
          </button>
        </div>
      } @else if (reasons(); as reasons) {
        @if (reasons.length === 0) {
          <p data-testid="vd-no-reasons" i18n="@@shared.voidDialog.noReasons">
            No hay motivos configurados. Pide a un administrador que los agregue.
          </p>
        }
        <div class="field" data-testid="vd-reason-box">
          <label for="void-dialog-reason" id="void-dialog-reason-label" i18n="@@shared.voidDialog.reason"
            >Motivo</label
          >
          <p-select
            inputId="void-dialog-reason"
            [ariaLabelledBy]="'void-dialog-reason-label'"
            [formControl]="reason"
            optionLabel="label"
            optionValue="code"
            [options]="options()"
            [filter]="true"
            filterBy="label"
            [resetFilterOnHide]="true"
            filterPlaceholder="Buscar"
            i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
            placeholder="Elige un motivo"
            i18n-placeholder="@@shared.voidDialog.reason.placeholder"
            [appendTo]="'body'"
            fluid
          />
        </div>
        <div class="field">
          <label for="void-dialog-note" i18n="@@shared.voidDialog.note">Nota</label>
          <textarea
            pTextarea
            id="void-dialog-note"
            data-testid="vd-note"
            [formControl]="note"
            maxlength="280"
            rows="3"
            fluid
          ></textarea>
          @if (noteRequired()) {
            <small i18n="@@shared.voidDialog.noteRequired">Para el motivo «Otro» la nota es obligatoria.</small>
          }
        </div>
      } @else {
        <p class="muted" i18n="@@shared.voidDialog.loading">Cargando los motivos…</p>
      }
      <ng-template #footer>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="vd-cancel"
          pTooltip="Cerrar sin confirmar"
          i18n-pTooltip="@@shared.voidDialog.cancel.tooltip"
          tooltipPosition="top"
          (click)="dismissed.emit()"
        >
          <tabler-icon [icon]="icons.cancel" aria-hidden="true" />
          <span i18n="@@shared.voidDialog.cancel">Cancelar</span>
        </button>
        <button
          pButton
          type="button"
          severity="danger"
          data-testid="vd-confirm"
          [disabled]="!canConfirm() || busy()"
          [pTooltip]="confirmTooltip()"
          tooltipPosition="top"
          (click)="confirm()"
        >
          <tabler-icon [icon]="confirmIcon()" aria-hidden="true" />
          <span>{{ confirmLabel() }}</span>
        </button>
      </ng-template>
    </p-dialog>
  `,
  styles: `
    .field {
      display: flex;
      flex-direction: column;
      gap: var(--space-1);
      margin-top: var(--space-3);
    }

    p {
      margin: 0;
    }

    .muted {
      color: var(--muted-foreground);
    }
  `,
})
export class VoidDialog {
  readonly visible = input.required<boolean>();
  readonly header = input.required<string>();
  readonly confirmLabel = input.required<string>();
  readonly confirmTooltip = input.required<string>();
  readonly reasons = input<readonly ActiveReason[] | null>(null);
  readonly failed = input(false);
  readonly busy = input(false);
  readonly confirmIcon = input<typeof IconBan>(IconBan);

  readonly confirmed = output<VoidChoice>();
  readonly dismissed = output<void>();
  readonly retried = output<void>();

  protected readonly icons = { cancel: IconX, retry: IconRefresh };
  protected readonly reason = new FormControl<string | null>(null);
  protected readonly note = new FormControl('', { nonNullable: true });
  private readonly chosen = toSignal(this.reason.valueChanges, { initialValue: null });
  private readonly typed = toSignal(this.note.valueChanges, { initialValue: '' });
  protected readonly options = computed(() =>
    (this.reasons() ?? []).map((reason) => ({ code: reason.code, label: reasonLabel(reason.code) })),
  );
  protected readonly noteRequired = computed(() => requiresNote(this.chosen()));
  protected readonly canConfirm = computed(
    () => this.chosen() !== null && (!this.noteRequired() || this.typed().trim() !== ''),
  );

  constructor() {
    effect(() => {
      if (this.visible()) {
        untracked(() => {
          this.reason.reset();
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

  protected confirm(): void {
    const reason = this.reason.value;
    if (reason !== null && this.canConfirm() && !this.busy()) {
      const note = this.note.value.trim();
      this.confirmed.emit({ reason, note: note === '' ? null : note });
    }
  }
}
