import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-catalog-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './catalog-layout.scss',
  template: `
    <h1 i18n="@@admin.catalog.title">Catálogo</h1>
    <nav aria-label="Catálogo" i18n-aria-label="@@admin.catalog.nav.label">
      <ul>
        <li>
          <a routerLink="categories" routerLinkActive="is-active" ariaCurrentWhenActive="page" i18n="@@admin.catalog.nav.categories"
            >Categorías</a
          >
        </li>
        <li>
          <a
            routerLink="modifier-groups"
            routerLinkActive="is-active"
            ariaCurrentWhenActive="page"
            i18n="@@admin.catalog.nav.modifierGroups"
            >Modificadores</a
          >
        </li>
        <li>
          <a routerLink="stations" routerLinkActive="is-active" ariaCurrentWhenActive="page" i18n="@@admin.catalog.nav.stations"
            >Estaciones</a
          >
        </li>
      </ul>
    </nav>
    <router-outlet />
  `,
})
export class CatalogLayout {}
