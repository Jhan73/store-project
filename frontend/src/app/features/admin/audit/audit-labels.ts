import { roleLabel } from '../../../core/auth/role-label';
import type { Role } from '../../../core/api/api-types';

// The API takes any text for both; these are every value the audit listeners write, so the selects can offer them.
export const AUDIT_ENTITY_TYPES: readonly string[] = [
  'CASH_MOVEMENT',
  'CATEGORY',
  'DELIVERY_ZONE',
  'MODIFIER_GROUP',
  'MODIFIER_OPTION',
  'OPENING_HOURS',
  'PAYMENT',
  'PRODUCT',
  'REASON',
  'SHIFT',
  'STATION',
  'STOCK_ITEM',
  'STORE_SETTINGS',
  'TABLE',
  'TICKET',
  'TICKET_LINE',
  'USER',
  'WALLET_PROVIDER',
];

export const AUDIT_ACTIONS: readonly string[] = [
  'CASH_MOVEMENT_RECORDED',
  'CATEGORY_CREATED',
  'CATEGORY_DEACTIVATED',
  'CATEGORY_DELETED',
  'CATEGORY_REACTIVATED',
  'CATEGORY_UPDATED',
  'DELIVERY_ZONE_CREATED',
  'DELIVERY_ZONE_DELETED',
  'DELIVERY_ZONE_STATUS_CHANGED',
  'DELIVERY_ZONE_UPDATED',
  'MODIFIER_GROUP_CREATED',
  'MODIFIER_GROUP_DELETED',
  'MODIFIER_GROUP_UPDATED',
  'MODIFIER_OPTION_AVAILABILITY_CHANGED',
  'OPENING_HOURS_CHANGED',
  'PAYMENT_RECORDED',
  'PAYMENT_VOIDED',
  'PRODUCT_AVAILABILITY_CHANGED',
  'PRODUCT_CREATED',
  'PRODUCT_DELETED',
  'PRODUCT_UPDATED',
  'REASON_CREATED',
  'REASON_STATUS_CHANGED',
  'SALE_VOIDED',
  'SET_PASSWORD_LINK_REISSUED',
  'SHIFT_CLOSED',
  'SHIFT_COUNT_REVEALED',
  'SHIFT_OPENED',
  'STATION_CREATED',
  'STATION_DEACTIVATED',
  'STATION_REACTIVATED',
  'STATION_UPDATED',
  'STOCK_ADJUSTMENT',
  'STOCK_DEFICIT',
  'STOCK_ENTRY',
  'STOCK_TRACKING_CHANGED',
  'STORE_SETTINGS_CHANGED',
  'TABLE_CREATED',
  'TABLE_DELETED',
  'TABLE_STATUS_CHANGED',
  'TABLE_UPDATED',
  'TICKETS_MERGED',
  'TICKET_CANCELLED',
  'TICKET_LINES_SENT',
  'TICKET_LINE_COMPED',
  'TICKET_LINE_VOIDED',
  'TICKET_OPENED',
  'TICKET_TRANSFERRED',
  'USER_CREATED',
  'USER_DEACTIVATED',
  'USER_DELETED',
  'USER_PASSWORD_SET',
  'USER_REACTIVATED',
  'USER_RENAMED',
  'USER_ROLE_CHANGED',
  'WALLET_PROVIDER_CREATED',
  'WALLET_PROVIDER_DEACTIVATED',
  'WALLET_PROVIDER_REACTIVATED',
  'WALLET_PROVIDER_UPDATED',
];

export function entityTypeLabel(type: string): string {
  switch (type) {
    case 'CASH_MOVEMENT':
      return $localize`:@@admin.audit.entity.cashMovement:Movimiento de caja`;
    case 'PAYMENT':
      return $localize`:@@admin.audit.entity.payment:Pago`;
    case 'SHIFT':
      return $localize`:@@admin.audit.entity.shift:Turno de caja`;
    case 'STOCK_ITEM':
      return $localize`:@@admin.audit.entity.stockItem:Stock`;
    case 'TICKET':
      return $localize`:@@admin.audit.entity.ticket:Ticket`;
    case 'TICKET_LINE':
      return $localize`:@@admin.audit.entity.ticketLine:Línea de ticket`;
    case 'CATEGORY':
      return $localize`:@@admin.audit.entity.category:Categoría`;
    case 'DELIVERY_ZONE':
      return $localize`:@@admin.audit.entity.deliveryZone:Zona de reparto`;
    case 'MODIFIER_GROUP':
      return $localize`:@@admin.audit.entity.modifierGroup:Grupo de modificadores`;
    case 'MODIFIER_OPTION':
      return $localize`:@@admin.audit.entity.modifierOption:Opción de modificador`;
    case 'OPENING_HOURS':
      return $localize`:@@admin.audit.entity.openingHours:Horario de atención`;
    case 'PRODUCT':
      return $localize`:@@admin.audit.entity.product:Producto`;
    case 'REASON':
      return $localize`:@@admin.audit.entity.reason:Motivo`;
    case 'STATION':
      return $localize`:@@admin.audit.entity.station:Estación`;
    case 'STORE_SETTINGS':
      return $localize`:@@admin.audit.entity.storeSettings:Configuración de la tienda`;
    case 'TABLE':
      return $localize`:@@admin.audit.entity.table:Mesa`;
    case 'USER':
      return $localize`:@@admin.audit.entity.user:Personal`;
    case 'WALLET_PROVIDER':
      return $localize`:@@admin.audit.entity.walletProvider:Billetera`;
    default:
      return type;
  }
}

// SYSTEM and ANONYMOUS are written by the audit module for changes with no signed-in staff member.
export function actorRoleLabel(role: string): string {
  switch (role) {
    case 'SERVER':
    case 'CASHIER':
    case 'KITCHEN':
    case 'ADMIN':
    case 'CUSTOMER':
      return roleLabel(role as Role);
    case 'SYSTEM':
      return $localize`:@@admin.audit.role.system:Sistema`;
    case 'ANONYMOUS':
      return $localize`:@@admin.audit.role.anonymous:Anónimo`;
    default:
      return role;
  }
}

export function shortId(id: string): string {
  return `${id.slice(0, 8)}…`;
}
