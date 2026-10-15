import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import {
  IconBellCheck,
  IconLayoutKanban,
  IconRefresh,
  IconStack2,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';
import { BoardAllDayView } from '../../../shared/ui/board-all-day/board-all-day';
import { BoardCard } from '../../../shared/ui/board-card/board-card';
import { BoardStore } from './board-store';

@Component({
  selector: 'app-board',
  imports: [BoardAllDayView, BoardCard, ButtonDirective, TablerIconComponent, Tooltip],
  providers: [BoardStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './board.scss',
  template: `
    @if (store.failed()) {
      <div class="offline" role="status" data-testid="offline-banner">
        <ng-container i18n="@@staff.board.failed"
          >No se pudo actualizar el tablero. Las acciones están deshabilitadas.</ng-container
        >
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="retry"
          (click)="store.load()"
          pTooltip="Volver a cargar el tablero"
          i18n-pTooltip="@@staff.board.retry.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.retry" aria-hidden="true" />
          <span i18n="@@staff.board.retry">Reintentar</span>
        </button>
      </div>
    } @else if (!store.connected()) {
      <div class="offline" role="status" data-testid="offline-banner">
        <ng-container i18n="@@staff.board.offline"
          >Sin conexión en tiempo real. Las acciones se habilitan al reconectar.</ng-container
        >
      </div>
    } @else if (!store.enabled()) {
      <div class="offline" role="status" data-testid="offline-banner">
        <ng-container i18n="@@staff.board.updating"
          >Actualizando el tablero. Las acciones se habilitan en unos segundos.</ng-container
        >
      </div>
    }

    <h1 i18n="@@staff.board.title">Tablero</h1>

    <div class="views" role="group" aria-label="Vistas del tablero" i18n-aria-label="@@staff.board.views">
      <button
        pButton
        type="button"
        data-testid="view-tickets"
        [severity]="store.view() === 'tickets' ? undefined : 'secondary'"
        [attr.aria-pressed]="store.view() === 'tickets'"
        pTooltip="Ver los pedidos por preparar"
        i18n-pTooltip="@@staff.board.view.tickets.tooltip"
        tooltipPosition="top"
        (click)="store.setView('tickets')"
      >
        <tabler-icon [icon]="icons.tickets" aria-hidden="true" />
        <span i18n="@@staff.board.view.tickets">Pedidos ({{ store.cards().length }})</span>
      </button>
      <button
        pButton
        type="button"
        data-testid="view-allDay"
        [severity]="store.view() === 'allDay' ? undefined : 'secondary'"
        [attr.aria-pressed]="store.view() === 'allDay'"
        pTooltip="Ver el total de cada producto pendiente"
        i18n-pTooltip="@@staff.board.view.allDay.tooltip"
        tooltipPosition="top"
        (click)="store.setView('allDay')"
      >
        <tabler-icon [icon]="icons.allDay" aria-hidden="true" />
        <span i18n="@@staff.board.view.allDay">Todo el día</span>
      </button>
      <button
        pButton
        type="button"
        data-testid="view-ready"
        [severity]="store.view() === 'ready' ? undefined : 'secondary'"
        [attr.aria-pressed]="store.view() === 'ready'"
        pTooltip="Ver los pedidos listos para entregar"
        i18n-pTooltip="@@staff.board.view.ready.tooltip"
        tooltipPosition="top"
        (click)="store.setView('ready')"
      >
        <tabler-icon [icon]="icons.ready" aria-hidden="true" />
        <span i18n="@@staff.board.view.ready">Listos ({{ store.readyCards().length }})</span>
      </button>
    </div>

    @if (store.view() === 'allDay') {
      <app-board-all-day [allDay]="store.allDay()" />
    } @else {
      <ul class="cards">
        @for (card of shown(); track card.order.id) {
          <li>
            <app-board-card
              [order]="card.order"
              [minutes]="card.minutes"
              [level]="card.level"
              [enabled]="store.enabled()"
              [pending]="store.pending.values()"
              [queued]="store.queuedIds()"
              (start)="store.start($event)"
              (readyOrder)="store.readyOrder($event)"
              (readyLine)="store.readyLine($event)"
              (undo)="store.undo($event)"
              (handover)="store.handover($event)"
              (recall)="store.recall($event)"
              (flag)="store.flag($event)"
              (acknowledge)="store.acknowledge($event)"
            />
          </li>
        }
      </ul>
      @if (shown().length === 0 && store.loaded()) {
        @if (store.view() === 'ready') {
          <p class="empty" i18n="@@staff.board.empty.ready">No hay pedidos listos para entregar.</p>
        } @else {
          <p class="empty" i18n="@@staff.board.empty.tickets">No hay pedidos por preparar.</p>
        }
      }
    }
  `,
})
export class Board {
  protected readonly store = inject(BoardStore);
  protected readonly icons = {
    retry: IconRefresh,
    tickets: IconLayoutKanban,
    allDay: IconStack2,
    ready: IconBellCheck,
  };

  protected readonly shown = computed(() =>
    this.store.view() === 'ready' ? this.store.readyCards() : this.store.cards(),
  );
}
