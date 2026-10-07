import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const root = join(process.cwd(), 'src/app');

// A search box over a fixed list of three options is noise, so these selects (by stable inputId) skip the filter.
const WITHOUT_FILTER = ['shell-theme'];

function sources(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      return sources(path);
    }
    return path.endsWith('.ts') && !path.endsWith('.spec.ts') ? [path] : [];
  });
}

interface SelectElement {
  readonly file: string;
  readonly open: string;
  readonly inputId: string | undefined;
}

const openTag = /<p-select\b(?:"[^"]*"|'[^']*'|[^>"'])*>/g;

const found: SelectElement[] = sources(root).flatMap((path) => {
  const text = readFileSync(path, 'utf8').replace(/\r\n/g, '\n');
  const file = path.slice(root.length + 1).replace(/\\/g, '/');
  return [...text.matchAll(openTag)].map(([open]) => ({
    file,
    open,
    inputId: /\sinputId="([^"]+)"/.exec(open)?.[1],
  }));
});

const label = ({ file, open }: SelectElement) =>
  `${file}: ${open.replace(/\s+/g, ' ').slice(0, 90)}`;
const allowed = ({ inputId }: SelectElement) => inputId !== undefined && WITHOUT_FILTER.includes(inputId);

describe('select conventions', () => {
  it('finds the selects of the app', () => {
    expect(found.length).toBeGreaterThan(5);
  });

  it.each(found.filter((select) => !allowed(select)).map((s) => [label(s), s] as const))(
    'has the search filter on: %s',
    (_name, { open }) => {
      expect(open).toMatch(/\s\[filter\]="true"/);
      expect(open).toMatch(/\s\[resetFilterOnHide\]="true"/);
      expect(open).toMatch(/\sfilterPlaceholder=/);
    },
  );

  it('keeps every filter exception in use and without a filter', () => {
    for (const inputId of WITHOUT_FILTER) {
      const select = found.filter((candidate) => candidate.inputId === inputId);
      expect(select, inputId).toHaveLength(1);
      expect(select[0].open).not.toMatch(/\s\[filter\]=/);
    }
  });
});
