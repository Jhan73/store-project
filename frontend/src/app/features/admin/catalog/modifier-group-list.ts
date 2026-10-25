import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { IconPencil, IconPlus, IconTrash, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';
import type { ModifierGroup } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { ConfirmAction } from '../../../shared/state/confirm-action';
import { DataColumn, DataTable } from '../../../shared/ui/data-table/data-table';
import { StatusTag } from '../../../shared/ui/status-tag/status-tag';
import { CatalogApi } from './catalog-api';
import { isStale } from '../admin-errors';

@Component({
  selector: 'app-modifier-group-list',
  imports: [RouterLink, ButtonDirective, TablerIconComponent, Tooltip, DataTable, StatusTag],
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

    <app-data-table
      label="Listado de grupos de modificadores"
      i18n-label="@@admin.catalog.groups.tableScroll"
      [value]="rowsView()"
      [columns]="columns"
    >
      <ng-template #row let-group>
        <td>{{ group.name }}</td>
        <td>
          <app-status-tag
            [value]="group.requiredLabel"
            [severity]="group.required ? 'warn' : 'secondary'"
          />
          <app-status-tag [value]="group.rangeLabel" severity="info" />
        </td>
        <td><app-status-tag [value]="group.optionsLabel" severity="secondary" /></td>
        <td class="actions">
          <div class="row-actions">
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
            @if (group.deletable) {
              <button
                pButton
                type="button"
                severity="danger"
                [outlined]="true"
                [size]="'small'"
                [attr.data-testid]="'delete-' + group.id"
                (click)="remove(group)"
                pTooltip="Eliminar este grupo de modificadores"
                i18n-pTooltip="@@admin.catalog.groups.delete.tooltip"
                tooltipPosition="top"
              >
                <tabler-icon [icon]="icons.delete" aria-hidden="true" />
                <span i18n="@@admin.catalog.groups.delete">Eliminar</span>
              </button>
            } @else {
              <app-status-tag [value]="labels.inUse" severity="info" />
            }
          </div>
        </td>
      </ng-template>
    </app-data-table>
  `,
})
export class ModifierGroupList {
  protected readonly icons = { create: IconPlus, edit: IconPencil, delete: IconTrash };
  protected readonly labels = {
    inUse: $localize`:@@admin.catalog.groups.inUse:En uso`,
  };
  protected readonly columns: readonly DataColumn[] = [
    { field: 'name', header: $localize`:@@admin.catalog.groups.name:Nombre`, sortable: true, filter: true },
    { field: 'requiredLabel', header: $localize`:@@admin.catalog.groups.rule:Elección`, sortable: true },
    { field: 'optionCount', header: $localize`:@@admin.catalog.groups.options:Opciones`, sortable: true },
    { field: '', header: $localize`:@@admin.catalog.actions:Acciones` },
  ];
  private readonly api = inject(CatalogApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly confirm = inject(ConfirmAction);

  protected readonly groups = signal<readonly ModifierGroup[]>([]);
  protected readonly rowsView = computed(() =>
    this.groups().map((group) => {
      const count = group.options.length;
      return {
        ...group,
        optionCount: count,
        requiredLabel: group.required
          ? $localize`:@@admin.catalog.groups.required:Obligatorio`
          : $localize`:@@admin.catalog.groups.optional:Opcional`,
        rangeLabel: $localize`:@@admin.catalog.groups.range:${group.minChoices}:min: a ${group.maxChoices}:max:`,
        optionsLabel:
          count === 1
            ? $localize`:@@admin.catalog.groups.optionCount.one:1 opción`
            : $localize`:@@admin.catalog.groups.optionCount.other:${count}:count: opciones`,
      };
    }),
  );

  constructor() {
    this.load();
  }

  protected async remove(group: ModifierGroup): Promise<void> {
    const confirmed = await this.confirm.ask({
      header: $localize`:@@admin.catalog.groups.delete.header:Eliminar grupo`,
      message: $localize`:@@admin.catalog.groups.delete.confirm:Se eliminará el grupo "${group.name}:name:" de forma definitiva y no se puede deshacer.`,
      acceptLabel: $localize`:@@admin.catalog.groups.delete:Eliminar`,
    });
    if (!confirmed) {
      return;
    }
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
