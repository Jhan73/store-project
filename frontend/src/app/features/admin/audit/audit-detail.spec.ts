import { TestBed } from '@angular/core/testing';
import type { AuditEntry } from '../../../core/api/api-types';
import { AuditDetail } from './audit-detail';

const entry: AuditEntry = {
  id: 'e1',
  occurredAt: '2026-10-05T15:30:00Z',
  actorId: 'u1',
  actorRole: 'ADMIN',
  action: 'PRODUCT_UPDATED',
  entityType: 'PRODUCT',
  entityId: '0199f3a2-7c1e-7b1a-8f00-3c1d2e4a5b6c',
  before: { price: '10.00' },
  after: { price: '12.00', note: '<script>alert(1)</script>' },
  reason: 'Ajuste de precio',
  correlationId: 'req-1',
};

function render(value: AuditEntry) {
  const fixture = TestBed.createComponent(AuditDetail);
  fixture.componentRef.setInput('entry', value);
  fixture.componentRef.setInput('when', '5/10/26, 10:30:00');
  fixture.componentRef.setInput('actor', 'ana@juguera.pe');
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('AuditDetail', () => {
  it('shows what the change was, who made it and why', () => {
    const text = render(entry).textContent ?? '';

    expect(text).toContain('5/10/26, 10:30:00');
    expect(text).toContain('ana@juguera.pe');
    expect(text).toContain('PRODUCT_UPDATED');
    expect(text).toContain('0199f3a2-7c1e-7b1a-8f00-3c1d2e4a5b6c');
    expect(text).toContain('Ajuste de precio');
    expect(text).toContain('req-1');
  });

  it('shows the values before and after side by side, pretty printed', () => {
    const host = render(entry);

    const [before, after] = Array.from(host.querySelectorAll('pre')).map((pre) => pre.textContent);
    expect(before).toBe('{\n  "price": "10.00"\n}');
    expect(after).toContain('"price": "12.00"');
  });

  it('writes untrusted values as text and never as markup', () => {
    const host = render(entry);

    expect(host.querySelector('script')).toBeNull();
    expect(host.querySelectorAll('pre')[1].textContent).toContain('<script>alert(1)</script>');
  });

  it('says so when a side was not recorded', () => {
    const host = render({ ...entry, before: null, reason: null, correlationId: null });

    expect(host.querySelectorAll('pre')).toHaveLength(1);
    expect(host.textContent).toContain('Sin valor registrado');
  });

  it('lets long values scroll inside their own box', () => {
    const host = render(entry);

    expect(host.querySelector('pre')!.tabIndex).toBe(0);
  });
});
