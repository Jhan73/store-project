import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const read = (path: string) => readFileSync(join(process.cwd(), path), 'utf8');

describe('desktop breakpoint and sidebar', () => {
  it('defines the desktop breakpoint once, at 1025px', () => {
    const breakpoints = read('src/styles/_breakpoints.scss');

    const desktop = /\$desktop:\s*(\d+(?:\.\d+)?)rem/.exec(breakpoints);

    expect(desktop).not.toBeNull();
    expect(Number(desktop![1]) * 16).toBe(1025);
    expect(breakpoints.match(/\$desktop:/g)).toHaveLength(1);
    expect(breakpoints).toMatch(/@mixin desktop-up\s*{\s*@media \(min-width: \$desktop\)/);
  });

  it('drops the wide breakpoint', () => {
    const breakpoints = read('src/styles/_breakpoints.scss');

    expect(breakpoints).not.toMatch(/\$wide|wide-up/);
  });

  it('swaps the top bar for the sidebar only above the desktop breakpoint', () => {
    const shell = read('src/app/shared/ui/app-shell/app-shell.scss');

    expect(shell).toMatch(/@include bp\.desktop-up/);
    expect(shell).not.toMatch(/@include bp\.wide-up|max-width/);
    const desktop = shell.slice(shell.indexOf('@include bp.desktop-up'));
    expect(desktop).toMatch(/\.bar\s*{\s*display:\s*none/);
    expect(desktop).toMatch(/\.sidebar\s*{[^}]*display:\s*flex/);
  });

  it('declares no width media query of its own in the sidebar styles', () => {
    expect(read('src/app/shared/ui/app-shell/app-shell.scss')).not.toMatch(/@media[^{]*width/);
  });

  it('animates the sidebar width only when motion is allowed', () => {
    const shell = read('src/app/shared/ui/app-shell/app-shell.scss');

    const motion = shell.slice(shell.indexOf('@media (prefers-reduced-motion: no-preference)'));
    expect(motion).toMatch(/transition/);
    expect(shell.replace(motion, '')).not.toMatch(/transition/);
  });
});
