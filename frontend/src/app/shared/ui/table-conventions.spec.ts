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
      return { file, line: before.split('\n').length, wrapped };
    });
  });
}

const found = tables();

describe('table conventions', () => {
  it('finds the data tables of the app', () => {
    expect(found.length).toBeGreaterThanOrEqual(9);
  });

  it.each(found.map((table) => [`${table.file}:${table.line}`, table] as const))(
    'sits inside app-table-scroll so it scrolls horizontally: %s',
    (_name, { wrapped }) => {
      expect(wrapped).toBe(true);
    },
  );
});
