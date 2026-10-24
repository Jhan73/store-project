import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const root = join(process.cwd(), 'src/app');

function sources(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      return sources(path);
    }
    return path.endsWith('.ts') && !path.endsWith('.spec.ts') ? [path] : [];
  });
}

interface DataTable {
  readonly file: string;
  readonly line: number;
  readonly tag: string;
  readonly wrapped: boolean;
}

// `<table\b` never matches `<tabler-icon`; a table is wrapped when the nearest wrapper tag before it is an opening one.
function tables(): DataTable[] {
  return sources(root).flatMap((path) => {
    const text = readFileSync(path, 'utf8').replace(/\r\n/g, '\n');
    const file = path.slice(root.length + 1).replace(/\\/g, '/');
    return [...text.matchAll(/<(table|p-table)\b/g)].map((match) => {
      const before = text.slice(0, match.index);
      const wrapped = before.lastIndexOf('<app-table-scroll') > before.lastIndexOf('</app-table-scroll>');
      return { file, line: before.split('\n').length, tag: match[1], wrapped };
    });
  });
}

const found = tables();

// Lists use app-data-table; the one raw p-table lives inside it.
const RAW_P_TABLE_ALLOWED = ['shared/ui/data-table/data-table.ts'];

describe('table conventions', () => {
  it('finds the data tables of the app', () => {
    // data-table, the shift result, the stock movements and the opening hours.
    expect(found.length).toBeGreaterThanOrEqual(4);
  });

  it('uses p-table only where it is allowed, so lists go through app-data-table', () => {
    const raw = [...new Set(found.filter((table) => table.tag === 'p-table').map((table) => table.file))];

    expect(raw.filter((file) => !RAW_P_TABLE_ALLOWED.includes(file))).toEqual([]);
    expect(raw).toEqual(RAW_P_TABLE_ALLOWED);
  });

  it.each(found.map((table) => [`${table.file}:${table.line}`, table] as const))(
    'sits inside app-table-scroll so it scrolls horizontally: %s',
    (_name, { wrapped }) => {
      expect(wrapped).toBe(true);
    },
  );
});
