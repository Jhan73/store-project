import type { PaymentMethod } from '../../../core/api/api-types';

export const PAYMENT_METHODS: readonly PaymentMethod[] = ['CASH', 'CARD', 'WALLET', 'TRANSFER'];

export function methodLabel(method: PaymentMethod): string {
  switch (method) {
    case 'CASH':
      return $localize`:@@register.method.cash:Efectivo`;
    case 'CARD':
      return $localize`:@@register.method.card:Tarjeta`;
    case 'WALLET':
      return $localize`:@@register.method.wallet:Billetera`;
    case 'TRANSFER':
      return $localize`:@@register.method.transfer:Transferencia`;
  }
}
