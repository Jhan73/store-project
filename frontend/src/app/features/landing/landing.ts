import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconLogin, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';

@Component({
  selector: 'app-landing',
  imports: [ButtonDirective, RouterLink, TablerIconComponent, Tooltip],
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
      <h1 i18n="@@landing.hero.title">Juguería</h1>
      <p i18n="@@landing.hero.subtitle">Muy pronto: pedidos online y en el local.</p>
      <a
        pButton
        routerLink="/staff"
        severity="secondary"
        pTooltip="Entrar a la zona del personal"
        i18n-pTooltip="@@landing.staffLink.tooltip"
        tooltipPosition="top"
      >
        <tabler-icon [icon]="icons.login" aria-hidden="true" />
        <span i18n="@@landing.staffLink.label">Acceso del personal</span>
      </a>
    </main>
  `,
})
export class Landing {
  protected readonly icons = { login: IconLogin };
}
