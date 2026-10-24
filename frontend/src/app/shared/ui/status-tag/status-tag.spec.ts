import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { StatusTag } from './status-tag';

@Component({
  imports: [StatusTag],
  template: `
    <app-status-tag value="Activo" severity="success" />
    <app-status-tag value="Stock bajo" severity="warn" />
    <app-status-tag value="3" />
  `,
})
class Host {}

describe('StatusTag', () => {
  it('shows the text and the severity class so color is never the only signal', async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const tags = (fixture.nativeElement as HTMLElement).querySelectorAll('.p-tag');

    expect(tags[0].textContent?.trim()).toBe('Activo');
    expect(tags[0].classList).toContain('p-tag-success');
    expect(tags[1].textContent?.trim()).toBe('Stock bajo');
    expect(tags[1].classList).toContain('p-tag-warn');
    expect(tags[2].classList).toContain('p-tag-secondary');
  });
});
