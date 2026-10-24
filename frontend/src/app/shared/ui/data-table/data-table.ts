import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  contentChild,
  input,
  output,
  TemplateRef,
} from '@angular/core';
import { InputText } from 'primeng/inputtext';
import { Table, TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TableScroll } from '../table-scroll/table-scroll';

let nextId = 0;

export interface DataColumn {
  readonly field: string;
  readonly header: string;
  readonly sortable?: boolean;
  readonly filter?: boolean;
  // Global search covers every column that has a field unless it opts out.
  readonly searchable?: boolean;
}

export type DataTableLazyEvent = TableLazyLoadEvent;

export interface DataRowContext<T> {
  readonly $implicit: T;
  readonly index: number;
}

// Wraps p-table: the owner passes the rows and columns and supplies the cells of one row as `<ng-template #row let-item>`.
// With `lazy` the table only reports page, sort and filters through `lazyLoad`; the owner loads the rows.
@Component({
  selector: 'app-data-table',
  imports: [NgTemplateOutlet, InputText, TableModule, TableScroll],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './data-table.scss',
  template: `
    @if (searchable()) {
      <div class="search">
        <label class="sr-only" [for]="searchId" i18n="@@shared.dataTable.search">Buscar</label>
        <input
          pInputText
          type="search"
          data-testid="dt-search"
          autocomplete="off"
          [id]="searchId"
          placeholder="Buscar"
          i18n-placeholder="@@shared.dataTable.search.placeholder"
          (input)="search(dt, $event)"
          fluid
        />
      </div>
    }
    <app-table-scroll [label]="label()">
      <p-table
        #dt
        [value]="$any(rowsCopy())"
        [lazy]="lazy()"
        [totalRecords]="totalRecords()"
        [rows]="rows()"
        [first]="first()"
        [paginator]="true"
        [loading]="loading()"
        [filterDelay]="searchDelay()"
        [globalFilterFields]="searchFields()"
        [lazyLoadOnInit]="lazy()"
        (onLazyLoad)="lazyLoad.emit($event)"
      >
        <ng-template #header>
          <tr>
            @for (column of columns(); track column.header) {
              @if (column.sortable) {
                <th [pSortableColumn]="column.field">
                  {{ column.header }}
                  <p-sortIcon [field]="column.field" />
                </th>
              } @else {
                <th>{{ column.header }}</th>
              }
            }
          </tr>
          @if (hasFilters()) {
            <tr>
              @for (column of columns(); track column.header) {
                <th>
                  @if (column.filter) {
                    <p-columnFilter
                      type="text"
                      [display]="'row'"
                      [field]="column.field"
                      [filterOn]="'input'"
                      [ariaLabel]="column.header"
                      [showMenu]="false"
                      [showClearButton]="false"
                    />
                  }
                </th>
              }
            </tr>
          }
        </ng-template>
        <ng-template #body let-item let-index="rowIndex">
          <tr>
            <ng-container
              [ngTemplateOutlet]="rowTemplate()"
              [ngTemplateOutletContext]="{ $implicit: item, index: index }"
            />
          </tr>
        </ng-template>
        <ng-template #emptymessage>
          <tr>
            <td [attr.colspan]="columns().length">{{ emptyMessage() }}</td>
          </tr>
        </ng-template>
      </p-table>
    </app-table-scroll>
  `,
})
export class DataTable<T = unknown> {
  readonly label = input.required<string>();
  readonly value = input.required<readonly T[]>();
  readonly columns = input.required<readonly DataColumn[]>();
  readonly rows = input(10);
  readonly first = input(0);
  readonly lazy = input(false);
  readonly totalRecords = input(0);
  readonly loading = input(false);
  readonly searchable = input(true);
  readonly searchDelay = input(300);
  // Shown when there are no records at all; a search that finds none still says "no results".
  readonly emptyText = input<string | null>(null);

  readonly lazyLoad = output<DataTableLazyEvent>();

  protected readonly rowTemplate = contentChild.required<TemplateRef<DataRowContext<T>>>('row');
  protected readonly searchId = `data-table-search-${nextId++}`;
  // p-table sorts the array it is given in place.
  protected readonly rowsCopy = computed(() => [...this.value()]);
  protected readonly emptyMessage = computed(
    () =>
      (this.value().length === 0 ? this.emptyText() : null) ??
      $localize`:@@shared.dataTable.empty:No hay resultados.`,
  );
  protected readonly hasFilters = computed(() => this.columns().some((column) => column.filter));
  protected readonly searchFields = computed(() =>
    this.columns()
      .filter((column) => column.field !== '' && column.searchable !== false)
      .map((column) => column.field),
  );

  protected search(table: Table, event: Event): void {
    table.filterGlobal((event.target as HTMLInputElement).value, 'contains');
  }
}
