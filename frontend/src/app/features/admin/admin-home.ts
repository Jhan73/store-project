import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-admin-home',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 i18n="@@admin.home.title">Administración</h1>
    <p i18n="@@admin.home.empty">Aquí aparecerán las herramientas de administración.</p>
  `,
})
export class AdminHome {}
