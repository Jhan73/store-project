import { inject, Injectable } from '@angular/core';
import { ConfirmationService } from 'primeng/api';

export interface ConfirmOptions {
  readonly message: string;
  readonly header?: string;
  readonly acceptLabel?: string;
  readonly rejectLabel?: string;
  readonly destructive?: boolean;
}

// Answers through the single p-confirmdialog mounted in the workspace layout.
@Injectable({ providedIn: 'root' })
export class ConfirmAction {
  private readonly confirmations = inject(ConfirmationService);

  ask(options: ConfirmOptions): Promise<boolean> {
    return new Promise((resolve) => {
      this.confirmations.confirm({
        message: options.message,
        header: options.header ?? $localize`:@@shared.confirm.header:Confirmar`,
        acceptLabel: options.acceptLabel ?? $localize`:@@shared.confirm.accept:Aceptar`,
        rejectLabel: options.rejectLabel ?? $localize`:@@shared.confirm.reject:Cancelar`,
        acceptButtonProps: options.destructive === false ? {} : { severity: 'danger' },
        rejectButtonProps: { severity: 'secondary' },
        accept: () => resolve(true),
        reject: () => resolve(false),
      });
    });
  }
}
