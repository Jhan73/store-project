import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconHome, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';

@Component({
  selector: 'app-forbidden',
  imports: [RouterLink, ButtonDirective, TablerIconComponent, Tooltip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    main {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: var(--space-3);
      padding: var(--page-padding);
    }

    h1,
    p {
      margin: 0;
    }
  `,
  template: `
    <main>
      <h1 i18n="@@forbidden.title">No tienes permiso para ver esta página</h1>
      <p i18n="@@forbidden.hint">Si crees que es un error, pide acceso a un administrador.</p>
      <a
        pButton
        routerLink="/"
        severity="secondary"
        pTooltip="Ir a la página de inicio"
        i18n-pTooltip="@@forbidden.home.tooltip"
        tooltipPosition="top"
      >
        <tabler-icon [icon]="icons.home" aria-hidden="true" />
        <span i18n="@@forbidden.home">Volver al inicio</span>
      </a>
    </main>
  `,
})
export class Forbidden {
  protected readonly icons = { home: IconHome };
}
