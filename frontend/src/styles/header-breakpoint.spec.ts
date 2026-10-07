import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const read = (path: string) => readFileSync(join(process.cwd(), path), 'utf8');

describe('header navigation breakpoint', () => {
  it('defines a wide breakpoint wide enough for the inline navigation in one row', () => {
    const breakpoints = read('src/styles/_breakpoints.scss');

    const wide = /\$wide:\s*(\d+(?:\.\d+)?)rem/.exec(breakpoints);

    expect(wide).not.toBeNull();
    expect(Number(wide![1])).toBeGreaterThanOrEqual(100);
    expect(breakpoints).toMatch(/@mixin wide-up/);
  });

  it('keeps the hamburger below the wide breakpoint, not only below the desktop one', () => {
    const shell = read('src/app/shared/ui/app-shell/app-shell.scss');

    expect(shell).toMatch(/@include bp\.wide-up/);
    expect(shell).not.toMatch(/@include bp\.desktop-up/);
  });
});
