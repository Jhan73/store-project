import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconBoxSeam, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';

@Component({
  selector: 'app-admin-home',
  imports: [RouterLink, ButtonDirective, TablerIconComponent, Tooltip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1 i18n="@@admin.home.title">Administración</h1>
    <p i18n="@@admin.home.empty">Aquí aparecerán las herramientas de administración.</p>
    <a
      pButton
      routerLink="/admin/stock"
      data-testid="stock-link"
      pTooltip="Ver el inventario y registrar entradas y ajustes"
      i18n-pTooltip="@@admin.home.stock.tooltip"
      tooltipPosition="top"
    >
      <tabler-icon [icon]="icons.stock" aria-hidden="true" />
      <span i18n="@@admin.home.stock">Inventario</span>
    </a>
  `,
})
export class AdminHome {
  protected readonly icons = { stock: IconBoxSeam };
}
