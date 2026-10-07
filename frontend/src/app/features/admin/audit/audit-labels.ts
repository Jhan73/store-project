import { roleLabel } from '../../../core/auth/role-label';
import type { Role } from '../../../core/api/api-types';

// The API takes any text for both; these are what the backend writes today, so the selects can offer them.
export const AUDIT_ENTITY_TYPES: readonly string[] = [
  'DELIVERY_ZONE',
  'MODIFIER_GROUP',
  'MODIFIER_OPTION',
  'OPENING_HOURS',
  'PRODUCT',
  'REASON',
  'STORE_SETTINGS',
  'TABLE',
  'USER',
];

export const AUDIT_ACTIONS: readonly string[] = [
  'DELIVERY_ZONE_CREATED',
  'DELIVERY_ZONE_STATUS_CHANGED',
  'DELIVERY_ZONE_UPDATED',
  'MODIFIER_GROUP_CREATED',
  'MODIFIER_GROUP_DELETED',
  'MODIFIER_GROUP_UPDATED',
  'MODIFIER_OPTION_AVAILABILITY_CHANGED',
  'OPENING_HOURS_CHANGED',
  'PRODUCT_AVAILABILITY_CHANGED',
  'PRODUCT_CREATED',
  'PRODUCT_UPDATED',
  'REASON_CREATED',
  'REASON_STATUS_CHANGED',
  'SET_PASSWORD_LINK_REISSUED',
  'STORE_SETTINGS_CHANGED',
  'TABLE_CREATED',
  'TABLE_STATUS_CHANGED',
  'TABLE_UPDATED',
  'USER_CREATED',
  'USER_DEACTIVATED',
  'USER_PASSWORD_SET',
  'USER_REACTIVATED',
  'USER_ROLE_CHANGED',
];

export function entityTypeLabel(type: string): string {
  switch (type) {
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
    case 'STORE_SETTINGS':
      return $localize`:@@admin.audit.entity.storeSettings:Configuración de la tienda`;
    case 'TABLE':
      return $localize`:@@admin.audit.entity.table:Mesa`;
    case 'USER':
      return $localize`:@@admin.audit.entity.user:Personal`;
    default:
      return type;
  }
}

// SYSTEM and ANONYMOUS are written by the audit module for changes with no signed-in staff member.
export function actorRoleLabel(role: string): string {
  switch (role) {
    case 'SERVER':
    case 'CASHIER':
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
