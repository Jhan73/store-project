import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const css = readFileSync(join(process.cwd(), 'src/styles/_theme.scss'), 'utf8');

function declarations(selector: string): Map<string, string> {
  const start = css.indexOf(`${selector} {`);
  const body = css.slice(start, css.indexOf('\n}', start));
  const found = new Map<string, string>();
  for (const [, name, value] of body.matchAll(/(--[\w-]+):\s*([^;]+);/g)) {
    found.set(name, value.trim());
  }
  return found;
}

const light = declarations(':root');
const dark = new Map([...light, ...declarations(':root.app-dark')]);

function resolve(tokens: Map<string, string>, name: string): string {
  const value = tokens.get(name);
  if (value === undefined) {
    throw new Error(`Missing token ${name}`);
  }
  const reference = /^var\((--[\w-]+)\)$/.exec(value);
  return reference ? resolve(tokens, reference[1]) : value;
}

function luminance(hex: string): number {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const channel = parseInt(hex.slice(i, i + 2), 16) / 255;
    return channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(tokens: Map<string, string>, foreground: string, background: string): number {
  const [a, b] = [foreground, background].map((name) => luminance(resolve(tokens, name)));
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

const textPairs: [string, string][] = [
  ['--foreground', '--background'],
  ['--muted-foreground', '--background'],
  ['--muted-foreground', '--muted'],
  ['--foreground', '--muted'],
  ['--primary', '--background'],
  ['--primary-foreground', '--primary'],
  ['--destructive', '--background'],
  ['--destructive-foreground', '--destructive'],
  ['--success', '--background'],
  ['--success-foreground', '--success'],
  ['--warning', '--background'],
  ['--warning-foreground', '--warning'],
];

describe.each([
  ['light', light],
  ['dark', dark],
])('theme tokens in %s mode', (_mode, tokens) => {
  it.each(textPairs)('%s on %s meets WCAG AA text contrast (4.5:1)', (foreground, background) => {
    expect(contrast(tokens, foreground, background)).toBeGreaterThanOrEqual(4.5);
  });

  it('draws control borders with at least 3:1 against the background', () => {
    expect(contrast(tokens, '--input-border', '--background')).toBeGreaterThanOrEqual(3);
  });
});
