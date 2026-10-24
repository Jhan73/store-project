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
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="fd-cancel"
          [disabled]="busy()"
          pTooltip="Cerrar sin guardar"
          i18n-pTooltip="@@shared.formDialog.cancel.tooltip"
          tooltipPosition="top"
          (click)="dismissed.emit()"
        >
          <tabler-icon [icon]="icons.cancel" aria-hidden="true" />
          <span i18n="@@shared.formDialog.cancel">Cancelar</span>
        </button>
        <button
          pButton
          type="submit"
          data-testid="fd-save"
          [attr.form]="formId"
          [loading]="busy()"
          [disabled]="saveDisabled()"
          pTooltip="Guardar los cambios"
          i18n-pTooltip="@@shared.formDialog.save.tooltip"
          tooltipPosition="top"
        >
          @if (!busy()) {
            <tabler-icon [icon]="icons.save" aria-hidden="true" />
          }
          <span i18n="@@shared.formDialog.save">Guardar</span>
        </button>
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

  readonly saved = output<void>();
  readonly dismissed = output<void>();

  protected readonly formId = `form-dialog-${nextId++}`;
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
