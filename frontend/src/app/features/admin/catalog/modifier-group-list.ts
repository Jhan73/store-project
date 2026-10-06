import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonDirective } from 'primeng/button';
import type { ModifierGroup } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { CatalogApi } from './catalog-api';
import { isStale } from './catalog-errors';

@Component({
  selector: 'app-modifier-group-list',
  imports: [RouterLink, ButtonDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './catalog.scss',
  template: `
    <div class="toolbar">
      <h2 i18n="@@admin.catalog.groups.title">Grupos de modificadores</h2>
      <a pButton routerLink="/admin/catalog/modifier-groups/new" i18n="@@admin.catalog.groups.new"
        >Nuevo grupo</a
      >
    </div>

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
                  i18n="@@admin.catalog.groups.confirmDelete"
                >
                  Confirmar eliminación
                </button>
                <button
                  pButton
                  type="button"
                  severity="secondary"
                  [size]="'small'"
                  [attr.data-testid]="'cancel-delete-' + group.id"
                  (click)="confirming.set(null)"
                  i18n="@@admin.catalog.cancel"
                >
                  Cancelar
                </button>
              } @else {
                <a
                  pButton
                  severity="secondary"
                  [size]="'small'"
                  [routerLink]="['/admin/catalog/modifier-groups', group.id]"
                  i18n="@@admin.catalog.edit"
                >
                  Editar
                </a>
                <button
                  pButton
                  type="button"
                  severity="danger"
                  [outlined]="true"
                  [size]="'small'"
                  [attr.data-testid]="'delete-' + group.id"
                  (click)="confirming.set(group.id)"
                  i18n="@@admin.catalog.groups.delete"
                >
                  Eliminar
                </button>
              }
            </td>
          </tr>
        }
      </tbody>
    </table>
  `,
})
export class ModifierGroupList {
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
