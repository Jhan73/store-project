import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import type { Shift } from '../../../core/api/api-types';
import { formatMoney } from '../../../core/money/money';
import { formatInStoreZone } from '../../../core/time/store-time';
import { TableScroll } from '../../../shared/ui/table-scroll/table-scroll';
import { methodLabel } from './payment-methods';

// Presentational: the closed shift as the server returned it. Every amount comes from the API.
@Component({
  selector: 'app-shift-result',
  imports: [TableScroll],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './register-panel.scss',
  template: `
    @if (shift(); as shift) {
      <dl>
        <div>
          <dt i18n="@@staff.register.result.opened">Abierto</dt>
          <dd>{{ time(shift.openedAt) }}</dd>
        </div>
        @if (shift.closedAt) {
          <div>
            <dt i18n="@@staff.register.result.closed">Cerrado</dt>
            <dd>{{ time(shift.closedAt) }}</dd>
          </div>
        }
        <div>
          <dt i18n="@@staff.register.result.float">Fondo inicial</dt>
          <dd>{{ money(shift.openingFloat) }}</dd>
        </div>
        @if (shift.expectedCash; as expected) {
          <div>
            <dt i18n="@@staff.register.result.expected">Efectivo esperado</dt>
            <dd data-testid="expected-cash">{{ money(expected) }}</dd>
          </div>
        }
        @if (shift.countedCash; as counted) {
          <div>
            <dt i18n="@@staff.register.result.counted">Efectivo contado</dt>
            <dd data-testid="counted-cash">{{ money(counted) }}</dd>
          </div>
        }
        @if (shift.difference; as difference) {
          <div>
            <dt i18n="@@staff.register.result.difference">Diferencia</dt>
            <dd data-testid="difference">{{ money(difference) }}</dd>
          </div>
        }
      </dl>
      @if (shift.differenceNote) {
        <p data-testid="difference-note">
          <ng-container i18n="@@staff.register.result.note">Nota:</ng-container>
          {{ shift.differenceNote }}
        </p>
      }
      @if (shift.closeKind === 'FORCED') {
        <p class="muted" data-testid="forced">
          <ng-container i18n="@@staff.register.result.forced">Cierre forzado por un administrador:</ng-container>
          {{ shift.forceReason }}
        </p>
      }
      @if (shift.totals; as totals) {
        <h3 i18n="@@staff.register.result.cash">Efectivo del turno</h3>
        <dl>
          <div>
            <dt i18n="@@staff.register.result.cashReceived">Efectivo recibido</dt>
            <dd data-testid="cash-received">{{ money(totals.cashReceived) }}</dd>
          </div>
          <div>
            <dt i18n="@@staff.register.result.changeGiven">Vuelto entregado</dt>
            <dd>{{ money(totals.changeGiven) }}</dd>
          </div>
          <div>
            <dt i18n="@@staff.register.result.voidedCash">Efectivo anulado</dt>
            <dd>{{ money(totals.voidedCash) }}</dd>
          </div>
          <div>
            <dt i18n="@@staff.register.result.cashIn">Entradas de caja</dt>
            <dd data-testid="cash-in">{{ money(totals.cashIn) }}</dd>
          </div>
          <div>
            <dt i18n="@@staff.register.result.cashOut">Salidas de caja</dt>
            <dd>{{ money(totals.cashOut) }}</dd>
          </div>
        </dl>
        <h3 i18n="@@staff.register.result.methods">Cobros por método</h3>
        <app-table-scroll label="Cobros por método de pago" i18n-label="@@staff.register.result.methodsScroll">
          <table>
            <thead>
              <tr>
                <th i18n="@@staff.register.result.method">Método</th>
                <th i18n="@@staff.register.result.recorded">Cobrado</th>
                <th i18n="@@staff.register.result.voided">Anulado</th>
                <th i18n="@@staff.register.result.net">Neto</th>
              </tr>
            </thead>
            <tbody>
              @for (row of totals.byMethod; track row.method) {
                <tr>
                  <td>{{ method(row.method) }}</td>
                  <td>{{ money(row.recorded) }}</td>
                  <td>{{ money(row.voided) }}</td>
                  <td>{{ money(row.net) }}</td>
                </tr>
              }
            </tbody>
          </table>
        </app-table-scroll>
      }
    }
  `,
})
export class ShiftResult {
  readonly shift = input.required<Shift>();
  readonly timeZone = input<string | null>(null);

  protected readonly money = formatMoney;
  protected readonly method = methodLabel;

  protected time(instant: string): string {
    const zone = this.timeZone();
    return zone === null ? '' : formatInStoreZone(instant, zone);
  }
}
