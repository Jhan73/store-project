import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const read = (path: string) => readFileSync(join(process.cwd(), 'src', path), 'utf8');

describe('shared styles', () => {
  it('keeps the actions cell a real table cell and puts the flex layout in an inner element', () => {
    const section = read('styles/_section.scss');

    expect(section).toMatch(/td\.actions\s*{[^}]*display:\s*table-cell/);
    expect(section).toMatch(/\.row-actions\s*{[^}]*display:\s*flex/);
  });

  it.each([
    'app/features/admin/admin-section.scss',
    'app/features/admin/catalog/catalog.scss',
    'app/features/staff/register/register-panel.scss',
  ])('%s uses the shared rules instead of repeating them', (path) => {
    const sheet = read(path);

    expect(sheet).toContain('section.section');
    expect(sheet).not.toMatch(/td\.actions\s*{[^}]*flex/);
    expect(sheet).not.toMatch(/\.badge\s*{/);
  });

  it('lets textareas be resized vertically only', () => {
    expect(read('styles.scss')).toMatch(/textarea\s*{\s*resize:\s*vertical;/);
  });
});
