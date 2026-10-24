import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { DataColumn, DataTable, DataTableLazyEvent } from './data-table';

interface Row {
  readonly name: string;
  readonly stock: number;
}

const ROWS: Row[] = [
  { name: 'Mango', stock: 5 },
  { name: 'Fresa', stock: 20 },
  { name: 'Piña', stock: 1 },
  { name: 'Papaya', stock: 9 },
];

const COLUMNS: DataColumn[] = [
  { field: 'name', header: 'Nombre', sortable: true, filter: true },
  { field: 'stock', header: 'Stock', sortable: true },
  { field: '', header: 'Acciones' },
];

@Component({
  imports: [DataTable],
  template: `
    <app-data-table
      label="Productos"
      [value]="rows()"
      [columns]="columns"
      [rows]="pageSize()"
      [lazy]="lazy()"
      [totalRecords]="total()"
      [searchDelay]="0"
      [emptyText]="emptyText()"
      (lazyLoad)="events.push($event)"
    >
      <ng-template #row let-item>
        <td data-testid="cell-name">{{ item.name }}</td>
        <td>{{ item.stock }}</td>
        <td class="actions"><div class="row-actions"><button type="button">Editar</button></div></td>
      </ng-template>
    </app-data-table>
  `,
})
class Host {
  readonly rows = signal<Row[]>(ROWS);
  readonly pageSize = signal(10);
  readonly lazy = signal(false);
  readonly emptyText = signal<string | null>(null);
  readonly total = signal(0);
  readonly columns = COLUMNS;
  readonly events: DataTableLazyEvent[] = [];
}

describe('DataTable', () => {
  async function render(setup?: (host: Host) => void) {
    const fixture = TestBed.createComponent(Host);
    setup?.(fixture.componentInstance);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;
    const names = () =>
      [...root.querySelectorAll('[data-testid="cell-name"]')].map((cell) => cell.textContent?.trim());
    return { fixture, root, names };
  }

  it('renders the consumer row cells inside a labelled scroll region', async () => {
    const { root, names } = await render();

    expect(names()).toEqual(['Mango', 'Fresa', 'Piña', 'Papaya']);
    expect(root.querySelector('app-table-scroll')?.getAttribute('aria-label')).toBe('Productos');
    expect(root.querySelector('app-table-scroll table')).not.toBeNull();
  });

  it('sorts by a sortable column and reverses on the second click', async () => {
    const { fixture, root, names } = await render();
    const stockHeader = [...root.querySelectorAll<HTMLElement>('th')].find((th) =>
      th.textContent?.includes('Stock'),
    )!;

    stockHeader.click();
    await fixture.whenStable();
    expect(names()).toEqual(['Piña', 'Mango', 'Papaya', 'Fresa']);
    expect(stockHeader.getAttribute('aria-sort')).toBe('ascending');

    stockHeader.click();
    await fixture.whenStable();
    expect(names()).toEqual(['Fresa', 'Papaya', 'Mango', 'Piña']);
  });

  it('does not make non-sortable columns sortable', async () => {
    const { root } = await render();
    const headers = [...root.querySelectorAll('thead tr:first-child th')];

    expect(headers[2].hasAttribute('aria-sort')).toBe(false);
  });

  it('filters by the global search', async () => {
    const { fixture, root, names } = await render();
    const search = root.querySelector<HTMLInputElement>('[data-testid="dt-search"]')!;

    search.value = 'pa';
    search.dispatchEvent(new Event('input'));
    await new Promise((resolve) => setTimeout(resolve, 20));
    await fixture.whenStable();

    expect(names()).toEqual(['Papaya']);
  });

  it('filters by the column filter of a filterable column', async () => {
    const { fixture, root, names } = await render();
    const filter = root.querySelector<HTMLInputElement>('thead input[type="text"]')!;

    filter.value = 'fre';
    filter.dispatchEvent(new Event('input'));
    await new Promise((resolve) => setTimeout(resolve, 20));
    await fixture.whenStable();

    expect(names()).toEqual(['Fresa']);
  });

  it('paginates and shows the empty state', async () => {
    const { fixture, root, names } = await render((host) => host.pageSize.set(2));

    expect(names()).toEqual(['Mango', 'Fresa']);
    root.querySelector<HTMLButtonElement>('.p-paginator-next')!.click();
    await fixture.whenStable();
    expect(names()).toEqual(['Piña', 'Papaya']);

    fixture.componentInstance.rows.set([]);
    await fixture.whenStable();
    expect(root.querySelector('tbody')?.textContent).toContain('No hay resultados');
  });

  it('says its own text when there are no records at all, and "no results" when a search finds none', async () => {
    const { fixture, root } = await render((host) => host.emptyText.set('Todavía no hay mesas.'));

    fixture.componentInstance.rows.set([]);
    await fixture.whenStable();
    expect(root.querySelector('tbody')?.textContent).toContain('Todavía no hay mesas.');

    fixture.componentInstance.rows.set(ROWS);
    await fixture.whenStable();
    const search = root.querySelector<HTMLInputElement>('[data-testid="dt-search"]')!;
    search.value = 'zzz';
    search.dispatchEvent(new Event('input'));
    await new Promise((resolve) => setTimeout(resolve, 20));
    await fixture.whenStable();
    expect(root.querySelector('tbody')?.textContent).toContain('No hay resultados');
  });

  it('gives every row a fixed-height cell class hook and keeps the actions cell a real table cell', async () => {
    const { root } = await render();
    const cell = root.querySelector<HTMLElement>('td.actions')!;

    expect(cell.querySelector(':scope > .row-actions')).not.toBeNull();
    expect(getComputedStyle(cell).display).not.toBe('flex');
  });

  it('in lazy mode it reports page, sort and filters instead of slicing the rows', async () => {
    const { fixture, root, names } = await render((host) => {
      host.lazy.set(true);
      host.pageSize.set(2);
      host.total.set(40);
      host.rows.set(ROWS.slice(0, 2));
    });
    const host = fixture.componentInstance;

    expect(host.events.at(-1)).toMatchObject({ first: 0, rows: 2 });
    expect(names()).toEqual(['Mango', 'Fresa']);

    root.querySelector<HTMLButtonElement>('.p-paginator-next')!.click();
    await fixture.whenStable();
    expect(host.events.at(-1)).toMatchObject({ first: 2, rows: 2 });

    const nameHeader = root.querySelector<HTMLElement>('th[aria-sort]')!;
    nameHeader.click();
    await fixture.whenStable();
    expect(host.events.at(-1)).toMatchObject({ sortField: 'name', sortOrder: 1 });
  });
});
