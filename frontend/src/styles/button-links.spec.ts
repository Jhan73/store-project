import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const css = readFileSync(join(process.cwd(), 'src/styles.scss'), 'utf8');

describe('link buttons', () => {
  it.each(['a[pButton]', 'a[pButton]:hover', 'a[pButton]:focus'])(
    'drops the underline of %s globally',
    (selector) => {
      const rule = css.slice(css.indexOf('a[pButton]'));
      const selectors = rule.slice(0, rule.indexOf('{')).split(',').map((s) => s.trim());
      expect(selectors).toContain(selector);
      expect(rule.slice(rule.indexOf('{'), rule.indexOf('}'))).toMatch(/text-decoration:\s*none/);
    },
  );
});
