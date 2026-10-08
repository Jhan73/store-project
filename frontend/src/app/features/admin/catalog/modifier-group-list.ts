import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  IconCheck,
  IconPencil,
  IconPlus,
  IconTrash,
  IconX,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';
import type { ModifierGroup } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { CatalogApi } from './catalog-api';
import { isStale } from '../admin-errors';
import { TableScroll } from '../../../shared/ui/table-scroll/table-scroll';

@Component({
  selector: 'app-modifier-group-list',
  imports: [TableScroll, RouterLink, ButtonDirective, TablerIconComponent, Tooltip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './catalog.scss',
  template: `
    <div class="toolbar">
      <h2 i18n="@@admin.catalog.groups.title">Grupos de modificadores</h2>
      <a
        pButton
        routerLink="/admin/catalog/modifier-groups/new"
        pTooltip="Crear un grupo de modificadores nuevo"
        i18n-pTooltip="@@admin.catalog.groups.new.tooltip"
        tooltipPosition="top"
      >
        <tabler-icon [icon]="icons.create" aria-hidden="true" />
        <span i18n="@@admin.catalog.groups.new">Nuevo grupo</span>
      </a>
    </div>

    <app-table-scroll label="Listado de grupos de modificadores" i18n-label="@@admin.catalog.groups.tableScroll">
      <table>
      <thead>
        <tr>
          <th i18n="@@admin.catalog.groups.name">Nombre</th>
          <th i18n="@@admin.catalog.groups.rule">Elección</th>
          <th i18n="@@admin.catalog.groups.options">Opciones</th>
          <th><span class="sr-only" i18n="@@admin.catalog.actions">Acciones</span></th>
        </tr>
      </thead>
      <tbody>
        @for (group of groups(); track group.id) {
          <tr>
            <td>{{ group.name }}</td>
            <td>
              @if (group.required) {
                <ng-container i18n="@@admin.catalog.groups.required">Obligatorio</ng-container>
              } @else {
                <ng-container i18n="@@admin.catalog.groups.optional">Opcional</ng-container>
              }
              <span class="muted" i18n="@@admin.catalog.groups.range"
                >{{ group.minChoices }} a {{ group.maxChoices }}</span
              >
            </td>
            <td i18n="@@admin.catalog.groups.optionCount">
              {group.options.length, plural, =1 {1 opción} other {{{ group.options.length }} opciones}}
            </td>
            <td class="actions">
              @if (confirming() === group.id) {
                <button
                  pButton
                  type="button"
                  severity="danger"
                  [size]="'small'"
                  [attr.data-testid]="'confirm-delete-' + group.id"
                  (click)="remove(group)"
                  pTooltip="Eliminar el grupo de forma definitiva"
                  i18n-pTooltip="@@admin.catalog.groups.confirmDelete.tooltip"
                  tooltipPosition="top"
                >
                  <tabler-icon [icon]="icons.confirm" aria-hidden="true" />
                  <span i18n="@@admin.catalog.groups.confirmDelete">Confirmar eliminación</span>
                </button>
                <button
                  pButton
                  type="button"
                  severity="secondary"
                  [size]="'small'"
                  [attr.data-testid]="'cancel-delete-' + group.id"
                  (click)="confirming.set(null)"
                  pTooltip="Conservar el grupo y no eliminarlo"
                  i18n-pTooltip="@@admin.catalog.groups.cancelDelete.tooltip"
                  tooltipPosition="top"
                >
                  <tabler-icon [icon]="icons.cancel" aria-hidden="true" />
                  <span i18n="@@admin.catalog.cancel">Cancelar</span>
                </button>
              } @else {
                <a
                  pButton
                  severity="secondary"
                  [size]="'small'"
                  [routerLink]="['/admin/catalog/modifier-groups', group.id]"
                  pTooltip="Editar este grupo de modificadores"
                  i18n-pTooltip="@@admin.catalog.groups.edit.tooltip"
                  tooltipPosition="top"
                >
                  <tabler-icon [icon]="icons.edit" aria-hidden="true" />
                  <span i18n="@@admin.catalog.edit">Editar</span>
                </a>
                <button
                  pButton
                  type="button"
                  severity="danger"
                  [outlined]="true"
                  [size]="'small'"
                  [attr.data-testid]="'delete-' + group.id"
                  (click)="confirming.set(group.id)"
                  pTooltip="Eliminar este grupo de modificadores"
                  i18n-pTooltip="@@admin.catalog.groups.delete.tooltip"
                  tooltipPosition="top"
                >
                  <tabler-icon [icon]="icons.delete" aria-hidden="true" />
                  <span i18n="@@admin.catalog.groups.delete">Eliminar</span>
                </button>
              }
            </td>
          </tr>
        }
      </tbody>
      </table>
    </app-table-scroll>
  `,
})
export class ModifierGroupList {
  protected readonly icons = {
    create: IconPlus,
    edit: IconPencil,
    delete: IconTrash,
    confirm: IconCheck,
    cancel: IconX,
  };
  private readonly api = inject(CatalogApi);
  private readonly notifier = inject(ErrorNotifier);

  protected readonly groups = signal<readonly ModifierGroup[]>([]);
  protected readonly confirming = signal<string | null>(null);

  constructor() {
    this.load();
  }

  protected remove(group: ModifierGroup): void {
    this.confirming.set(null);
    this.api.deleteModifierGroup(group.id, group.etag).subscribe({
      next: () => this.groups.update((list) => list.filter((item) => item.id !== group.id)),
      error: (error: unknown) => {
        this.notifier.show(error);
        if (isStale(error)) {
          this.load();
        }
      },
    });
  }

  private load(): void {
    this.api.modifierGroups().subscribe({
      next: (list) => this.groups.set(list),
      error: (error: unknown) => this.notifier.show(error),
    });
  }
}
