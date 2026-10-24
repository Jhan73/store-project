import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { IconDeviceFloppy, IconX, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Tooltip } from 'primeng/tooltip';

let nextId = 0;

// Presentational: the owner builds the form inside it, runs the command and flips `busy`.
@Component({
  selector: 'app-form-dialog',
  imports: [ButtonDirective, Dialog, Tooltip, TablerIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './form-dialog.scss',
  template: `
    <p-dialog
      [header]="header()"
      [modal]="true"
      [draggable]="false"
      [style]="{ width: 'min(' + width() + ', calc(100vw - 1rem))' }"
      [closable]="!busy()"
      [closeOnEscape]="false"
      [dismissableMask]="false"
      [visible]="visible()"
      (visibleChange)="visibleChange($event)"
    >
      <form [id]="formId" [attr.aria-busy]="busy()" novalidate (submit)="submit($event)" (keydown.escape)="escape($event)">
        <ng-content />
      </form>
      <ng-template #footer>
        <div class="footer">
          @if (saveDisabled() && disabledReason(); as why) {
            <small class="reason" data-testid="fd-reason" [id]="reasonId">{{ why }}</small>
          }
          <button
            pButton
            type="button"
            severity="secondary"
            data-testid="fd-cancel"
            [disabled]="busy()"
            [pTooltip]="cancelTooltip() ?? tips.cancel"
            tooltipPosition="top"
            (click)="dismissed.emit()"
          >
            <tabler-icon [icon]="icons.cancel" aria-hidden="true" />
            @if (cancelLabel(); as label) {
              <span>{{ label }}</span>
            } @else {
              <span i18n="@@shared.formDialog.cancel">Cancelar</span>
            }
          </button>
          <button
            pButton
            type="submit"
            data-testid="fd-save"
            [attr.form]="formId"
            [attr.aria-describedby]="saveDisabled() && disabledReason() ? reasonId : null"
            [severity]="saveSeverity() === 'danger' ? 'danger' : undefined"
            [loading]="busy()"
            [disabled]="saveDisabled() || busy()"
            [pTooltip]="saveTooltip() ?? tips.save"
            tooltipPosition="top"
          >
            @if (!busy()) {
              <tabler-icon [icon]="saveIcon()" aria-hidden="true" />
            }
            @if (saveLabel(); as label) {
              <span>{{ label }}</span>
            } @else {
              <span i18n="@@shared.formDialog.save">Guardar</span>
            }
          </button>
        </div>
      </ng-template>
    </p-dialog>
  `,
})
export class FormDialog {
  readonly visible = input.required<boolean>();
  readonly header = input.required<string>();
  readonly busy = input(false);
  readonly saveDisabled = input(false);
  readonly width = input('32rem');
  // Optional wording for dialogs that confirm something other than a plain save.
  readonly saveLabel = input<string | null>(null);
  readonly saveTooltip = input<string | null>(null);
  readonly saveSeverity = input<'primary' | 'danger'>('primary');
  readonly saveIcon = input<typeof IconDeviceFloppy>(IconDeviceFloppy);
  readonly cancelLabel = input<string | null>(null);
  readonly cancelTooltip = input<string | null>(null);
  // Shown while the save is disabled, so the reason is not hidden in a tooltip.
  readonly disabledReason = input<string | null>(null);

  readonly saved = output<void>();
  readonly dismissed = output<void>();

  protected readonly formId = `form-dialog-${nextId++}`;
  protected readonly reasonId = `${this.formId}-reason`;
  protected readonly tips = {
    save: $localize`:@@shared.formDialog.save.tooltip:Guardar los cambios`,
    cancel: $localize`:@@shared.formDialog.cancel.tooltip:Cerrar sin guardar`,
  };
  protected readonly icons = { save: IconDeviceFloppy, cancel: IconX };

  protected visibleChange(visible: boolean): void {
    if (!visible && !this.busy()) {
      this.dismissed.emit();
    }
  }

  // p-dialog reads closeOnEscape once, when it opens, so it cannot follow `busy`; Escape is handled here.
  protected escape(event: Event): void {
    if (!event.defaultPrevented && !this.busy()) {
      this.dismissed.emit();
    }
  }

  protected submit(event: Event): void {
    event.preventDefault();
    if (!this.busy() && !this.saveDisabled()) {
      this.saved.emit();
    }
  }
}
