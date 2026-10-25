import { ChangeDetectionStrategy, Component, computed, effect, input, output, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { IconBan, IconRefresh, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Select } from 'primeng/select';
import { Textarea } from 'primeng/textarea';
import { Tooltip } from 'primeng/tooltip';
import type { ActiveReason } from '../../../core/api/api-types';
import { reasonLabel, requiresNote } from '../../../core/reasons/reason-rules';
import { FormDialog } from '../form-dialog/form-dialog';
import { FormField } from '../form-field/form-field';

export interface VoidChoice {
  readonly reason: string;
  readonly note: string | null;
}

// Presentational: the owner loads the reasons and runs the command; this only collects the choice.
@Component({
  selector: 'app-void-dialog',
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    Select,
    Textarea,
    Tooltip,
    TablerIconComponent,
    FormDialog,
    FormField,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-form-dialog
      [header]="header()"
      [visible]="visible()"
      [busy]="busy()"
      [saveDisabled]="!canConfirm()"
      [saveLabel]="confirmLabel()"
      [saveTooltip]="confirmTooltip()"
      [saveIcon]="confirmIcon()"
      [saveSeverity]="'danger'"
      [disabledReason]="whyDisabled()"
      width="30rem"
      (saved)="confirm()"
      (dismissed)="dismissed.emit()"
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
        <div class="dialog-fields" data-testid="vd-reason-box">
          <app-form-field
            label="Motivo"
            i18n-label="@@shared.voidDialog.reason"
            controlId="void-dialog-reason"
          >
            <p-select
              inputId="void-dialog-reason"
              [ariaLabel]="labels.reason"
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
          </app-form-field>
          <app-form-field
            label="Nota"
            i18n-label="@@shared.voidDialog.note"
            controlId="void-dialog-note"
            [hint]="noteRequired() ? labels.noteRequired : null"
          >
            <textarea
              pTextarea
              id="void-dialog-note"
              data-testid="vd-note"
              [formControl]="note"
              maxlength="280"
              fluid
            ></textarea>
          </app-form-field>
        </div>
      } @else {
        <p class="muted" i18n="@@shared.voidDialog.loading">Cargando los motivos…</p>
      }
    </app-form-dialog>
  `,
  styles: `
    .dialog-fields {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
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

  protected readonly icons = { retry: IconRefresh };
  protected readonly labels = {
    reason: $localize`:@@shared.voidDialog.reason.aria:Motivo`,
    noteRequired: $localize`:@@shared.voidDialog.noteRequired:Para el motivo «Otro» la nota es obligatoria.`,
  };
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

  protected readonly whyDisabled = computed(() => {
    if (this.failed()) {
      return $localize`:@@shared.voidDialog.why.failed:Carga los motivos para continuar.`;
    }
    if (this.reasons() === null) {
      return $localize`:@@shared.voidDialog.why.loading:Esperando los motivos.`;
    }
    if (this.chosen() === null) {
      return $localize`:@@shared.voidDialog.why.reason:Elige un motivo para continuar.`;
    }
    return this.noteRequired() ? $localize`:@@shared.voidDialog.why.note:Escribe la nota para continuar.` : null;
  });

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

  protected confirm(): void {
    const reason = this.reason.value;
    if (reason !== null && this.canConfirm() && !this.busy()) {
      const note = this.note.value.trim();
      this.confirmed.emit({ reason, note: note === '' ? null : note });
    }
  }
}
