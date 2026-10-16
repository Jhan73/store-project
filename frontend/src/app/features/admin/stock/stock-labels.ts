import type { StockMovementType } from '../../../core/api/api-types';

export function movementTypeLabel(type: StockMovementType): string {
  switch (type) {
    case 'ENTRY':
      return $localize`:@@admin.stock.movement.entry:Entrada`;
    case 'ADJUSTMENT':
      return $localize`:@@admin.stock.movement.adjustment:Ajuste`;
    case 'SALE':
      return $localize`:@@admin.stock.movement.sale:Venta`;
    case 'RETURN':
      return $localize`:@@admin.stock.movement.return:Devolución`;
    case 'RESERVE':
      return $localize`:@@admin.stock.movement.reserve:Reserva`;
    case 'COMMIT':
      return $localize`:@@admin.stock.movement.commit:Confirmación`;
    case 'RELEASE':
      return $localize`:@@admin.stock.movement.release:Liberación`;
    case 'EXPIRE':
      return $localize`:@@admin.stock.movement.expire:Vencimiento`;
    default:
      return type;
  }
}
