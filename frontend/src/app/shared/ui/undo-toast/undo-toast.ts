import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { IconArrowBackUp, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';
import { UndoNotice } from '../../state/undo-notice';

// Mounted once in the workspace layout; lists the commands still inside their undo window.
@Component({
  selector: 'app-undo-toast',
  imports: [ButtonDirective, Tooltip, TablerIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './undo-toast.scss',
  template: `
    <div class="stack" role="status">
      @for (item of notice.active(); track item.key) {
        <div class="notice">
          <span>{{ item.message }}</span>
          <button
            pButton
            type="button"
            [size]="'small'"
            severity="secondary"
            data-testid="undo-action"
            pTooltip="Deshacer y conservar el elemento"
            i18n-pTooltip="@@shared.undo.tooltip"
            tooltipPosition="top"
            (click)="notice.undo(item)"
          >
            <tabler-icon [icon]="icons.undo" aria-hidden="true" />
            <span i18n="@@shared.undo.action">Deshacer</span>
          </button>
        </div>
      }
    </div>
  `,
})
export class UndoToast {
  protected readonly notice = inject(UndoNotice);
  protected readonly icons = { undo: IconArrowBackUp };
}
