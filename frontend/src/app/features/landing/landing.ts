import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonDirective } from 'primeng/button';

@Component({
  selector: 'app-landing',
  imports: [ButtonDirective, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main>
      <h1 i18n="@@landing.hero.title">Juguería</h1>
      <p i18n="@@landing.hero.subtitle">Muy pronto: pedidos online y en el local.</p>
      <a
        pButton
        routerLink="/staff"
        severity="secondary"
        i18n="@@landing.staffLink.label"
        >Acceso del personal</a
      >
    </main>
  `,
})
export class Landing {}
