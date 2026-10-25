import {
  actorRoleLabel,
  AUDIT_ACTIONS,
  AUDIT_ENTITY_TYPES,
  entityTypeLabel,
  shortId,
} from './audit-labels';

describe('audit labels', () => {
  it('names every entity type the audit module writes', () => {
    expect(AUDIT_ENTITY_TYPES).toEqual(
      expect.arrayContaining([
        'PRODUCT',
        'USER',
        'STORE_SETTINGS',
        'TABLE',
        'MODIFIER_OPTION',
        'CATEGORY',
        'STATION',
        'TICKET',
        'TICKET_LINE',
        'PAYMENT',
        'SHIFT',
        'CASH_MOVEMENT',
        'STOCK_ITEM',
      ]),
    );
    expect(entityTypeLabel('CATEGORY')).toBe('Categoría');
    expect(entityTypeLabel('STATION')).toBe('Estación');
    for (const type of AUDIT_ENTITY_TYPES) {
      expect(entityTypeLabel(type)).not.toBe(type);
    }
  });

  it('falls back to the raw value for a type this screen does not know', () => {
    expect(entityTypeLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW');
  });

  it('lists the actions as the stable codes the API filters on', () => {
    expect(AUDIT_ACTIONS).toContain('PRODUCT_UPDATED');
    expect(AUDIT_ACTIONS).toEqual(
      expect.arrayContaining([
        'CATEGORY_CREATED',
        'CATEGORY_UPDATED',
        'CATEGORY_DEACTIVATED',
        'CATEGORY_REACTIVATED',
        'STATION_CREATED',
        'STATION_UPDATED',
      ]),
    );
    expect(AUDIT_ACTIONS).toContain('MODIFIER_OPTION_AVAILABILITY_CHANGED');
    expect([...AUDIT_ACTIONS]).toEqual([...AUDIT_ACTIONS].sort());
  });

  it('lists the ticket, payment, shift, cash and stock actions the backend writes', () => {
    expect(AUDIT_ACTIONS).toEqual(
      expect.arrayContaining([
        'TICKET_OPENED',
        'TICKET_LINES_SENT',
        'TICKET_LINE_VOIDED',
        'TICKET_LINE_COMPED',
        'TICKET_CANCELLED',
        'TICKET_TRANSFERRED',
        'TICKETS_MERGED',
        'SALE_VOIDED',
        'PAYMENT_RECORDED',
        'PAYMENT_VOIDED',
        'SHIFT_OPENED',
        'SHIFT_CLOSED',
        'SHIFT_COUNT_REVEALED',
        'CASH_MOVEMENT_RECORDED',
        'STOCK_ENTRY',
        'STOCK_ADJUSTMENT',
        'STOCK_TRACKING_CHANGED',
        'STOCK_DEFICIT',
      ]),
    );
    expect(new Set(AUDIT_ACTIONS).size).toBe(AUDIT_ACTIONS.length);
  });

  it('names the roles of an actor, including the ones that are not accounts', () => {
    expect(actorRoleLabel('ADMIN')).toBe('Administrador');
    expect(actorRoleLabel('SYSTEM')).toBe('Sistema');
    expect(actorRoleLabel('ANONYMOUS')).toBe('Anónimo');
    expect(actorRoleLabel('ALIEN')).toBe('ALIEN');
  });

  it('shortens an id to its first block', () => {
    expect(shortId('0199f3a2-7c1e-7b1a-8f00-3c1d2e4a5b6c')).toBe('0199f3a2…');
  });
});
