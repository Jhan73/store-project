import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-landing',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main>
      <h1 i18n="@@landing.hero.title">Juguería</h1>
      <p i18n="@@landing.hero.subtitle">Muy pronto: pedidos online y en el local.</p>
    </main>
  `,
})
export class Landing {}
