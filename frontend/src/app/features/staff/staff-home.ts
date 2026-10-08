import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-staff-home',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 i18n="@@staff.home.title">Operación</h1>
    <p i18n="@@staff.home.empty">Aquí aparecerán las herramientas del local.</p>
  `,
})
export class StaffHome {}
