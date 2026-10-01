import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonDirective } from 'primeng/button';

@Component({
  selector: 'app-forbidden',
  imports: [RouterLink, ButtonDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main>
      <h1 i18n="@@forbidden.title">No tienes permiso para ver esta página</h1>
      <p i18n="@@forbidden.hint">Si crees que es un error, pide acceso a un administrador.</p>
      <a pButton routerLink="/" severity="secondary" i18n="@@forbidden.home">Volver al inicio</a>
    </main>
  `,
})
export class Forbidden {}
