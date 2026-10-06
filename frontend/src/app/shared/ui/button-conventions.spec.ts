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

interface ButtonElement {
  readonly file: string;
  readonly tag: string;
  readonly open: string;
  readonly body: string;
}

const openTag = /<(button|a|p-button|p-fileupload)\b(?:"[^"]*"|'[^']*'|[^>"'])*>/g;

function buttons(): ButtonElement[] {
  return sources(root).flatMap((path) => {
    const text = readFileSync(path, 'utf8').replace(/\r\n/g, '\n');
    const file = path.slice(root.length + 1).replace(/\\/g, '/');
    return [...text.matchAll(openTag)]
      .filter(([open, tag]) => tag !== 'a' || /\spButton\b/.test(open))
      .map((match) => {
        const [open, tag] = match;
        const from = match.index + open.length;
        const end = open.endsWith('/>') ? from : text.indexOf(`</${tag}>`, from);
        return { file, tag, open, body: text.slice(from, end) };
      });
  });
}

const found = buttons();
const label = ({ file, open }: ButtonElement) => `${file}: ${open.replace(/\s+/g, ' ').slice(0, 90)}`;

describe('button conventions', () => {
  it('finds the buttons of the app', () => {
    expect(found.length).toBeGreaterThan(50);
  });

  it.each(found.map((button) => [label(button), button] as const))(
    'has a decorative Tabler icon: %s',
    (_name, { body }) => {
      expect(body).toMatch(/<tabler-icon\b[^>]*aria-hidden="true"/);
    },
  );

  it.each(found.map((button) => [label(button), button] as const))(
    'has an i18n tooltip on top: %s',
    (_name, { open }) => {
      expect(open).toMatch(/\spTooltip=|\s\[pTooltip\]=/);
      if (/\spTooltip=/.test(open)) {
        expect(open).toMatch(/\si18n-pTooltip="@@[\w.]+"/);
      }
      expect(open).toMatch(/\stooltipPosition="top"/);
    },
  );

  it.each(found.map((button) => [label(button), button] as const))(
    'keeps an i18n aria-label when it is icon-only: %s',
    (_name, { open, body }) => {
      const visibleText = body
        .replace(/<tabler-icon\b[^>]*>/g, '')
        .replace(/<\/tabler-icon>/g, '')
        .replace(/@(if|else)\b[^{]*\{|\}/g, '')
        .trim();
      if (visibleText === '') {
        expect(open).toMatch(/\saria-label=|\s\[attr\.aria-label\]=/);
        expect(open).toMatch(/\si18n-aria-label="@@[\w.]+"|\$localize/);
      }
    },
  );
});
