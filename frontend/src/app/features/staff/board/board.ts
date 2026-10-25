import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import {
  IconBellCheck,
  IconLayoutKanban,
  IconRefresh,
  IconStack2,
  IconVolume,
  IconVolumeOff,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Select } from 'primeng/select';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { Tooltip } from 'primeng/tooltip';
import { AuthStore } from '../../../core/auth/auth-store';
import { SessionExpiryWarning } from '../../../core/auth/session-expiry-warning';
import { ScreenWakeLock } from '../../../core/screen/screen-wake-lock';
import { BoardAllDayView } from '../../../shared/ui/board-all-day/board-all-day';
import { BoardCard } from '../../../shared/ui/board-card/board-card';
import { SessionExpiryBanner } from '../../../shared/ui/session-expiry-banner/session-expiry-banner';
import { BoardSound } from './board-sound';
import { BoardSort, BoardStore } from './board-store';

@Component({
  selector: 'app-board',
  imports: [
    BoardAllDayView,
    BoardCard,
    SessionExpiryBanner,
    ButtonDirective,
    FormsModule,
    ReactiveFormsModule,
    Select,
    TablerIconComponent,
    ToggleSwitch,
    Tooltip,
  ],
  providers: [BoardStore, BoardSound, ScreenWakeLock],
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

    @let daysLeft = expiry.daysLeft();
    @if (daysLeft !== null) {
      <app-session-expiry-banner [daysLeft]="daysLeft" />
    }

    @if (sound.unsupported()) {
      <div class="sound-warning" role="status" data-testid="sound-warning">
        <tabler-icon [icon]="icons.soundOff" aria-hidden="true" />
        <ng-container i18n="@@staff.board.sound.unsupported"
          >Este navegador no permite sonido. Mira el tablero para ver los avisos nuevos.</ng-container
        >
      </div>
    } @else if (!sound.enabled()) {
      <div class="sound-warning" role="status" data-testid="sound-warning">
        <tabler-icon [icon]="icons.soundOff" aria-hidden="true" />
        <ng-container i18n="@@staff.board.sound.off"
          >Sonido desactivado: no sonarán los avisos de pedidos nuevos, modificados o anulados.</ng-container
        >
        <button
          pButton
          type="button"
          data-testid="enable-sound"
          (click)="enableSound()"
          pTooltip="Activar los sonidos de aviso del tablero"
          i18n-pTooltip="@@staff.board.sound.enable.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.sound" aria-hidden="true" />
          <span i18n="@@staff.board.sound.enable">Activar sonido</span>
        </button>
      </div>
    }

    <h1 i18n="@@staff.board.title">Tablero</h1>
    @if (sound.enabled()) {
      <p class="sound-on" data-testid="sound-on">
        <tabler-icon [icon]="icons.sound" aria-hidden="true" />
        <span i18n="@@staff.board.sound.on">Sonido activado</span>
      </p>
    }

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
        <span i18n="@@staff.board.view.tickets">Pedidos ({{ store.ticketCount() }})</span>
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
        <span i18n="@@staff.board.view.ready">Listos ({{ store.readyCount() }})</span>
      </button>
    </div>

    @if (store.stations().length > 1) {
      <div class="station">
        <label for="board-station" id="board-station-label" i18n="@@staff.board.station">Estación</label>
        <p-select
          inputId="board-station"
          [ariaLabelledBy]="'board-station-label'"
          [formControl]="station"
          optionLabel="name"
          optionValue="id"
          [options]="stationOptions()"
          [filter]="true"
          filterBy="name"
          [resetFilterOnHide]="true"
          filterPlaceholder="Buscar"
          i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
          [appendTo]="'body'"
        />
      </div>
    }

    @if (store.view() !== 'allDay') {
      <div class="controls">
        @if (store.view() === 'tickets') {
          <div class="order">
            <label for="board-order" id="board-order-label" i18n="@@staff.board.order">Orden</label>
            <p-select
              inputId="board-order"
              [ariaLabelledBy]="'board-order-label'"
              [formControl]="order"
              optionLabel="label"
              optionValue="value"
              [options]="orderOptions"
              [filter]="true"
              filterBy="label"
              [resetFilterOnHide]="true"
              filterPlaceholder="Buscar"
              i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
              [appendTo]="'body'"
            />
          </div>
        }
        <div class="attention">
          <p-toggleswitch
            inputId="board-attention"
            data-testid="attention-only"
            [ngModel]="store.attentionOnly()"
            (onChange)="store.setAttentionOnly($event.checked)"
          />
          <label for="board-attention" i18n="@@staff.board.attention">Solo con notas o alergias</label>
        </div>
      </div>
    }

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
              [timeZone]="store.timeZone()"
              [mayHandOver]="mayHandOver()"
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
        @if (store.attentionOnly() && (store.view() === 'ready' ? store.readyCount() : store.ticketCount()) > 0) {
          <p class="empty" i18n="@@staff.board.empty.attention">Ningún pedido tiene notas ni alergias.</p>
        } @else if (store.view() === 'ready') {
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
  private readonly auth = inject(AuthStore);
  protected readonly expiry = inject(SessionExpiryWarning);
  protected readonly sound = inject(BoardSound);
  protected readonly icons = {
    retry: IconRefresh,
    tickets: IconLayoutKanban,
    allDay: IconStack2,
    ready: IconBellCheck,
    sound: IconVolume,
    soundOff: IconVolumeOff,
  };

  // '' stands for all stations: a select option cannot carry null.
  protected readonly station = new FormControl('', { nonNullable: true });
  protected readonly stationOptions = computed(() => [
    { id: '', name: $localize`:@@staff.board.station.all:Todas las estaciones` },
    ...this.store.stations(),
  ]);

  protected readonly orderOptions: { value: BoardSort; label: string }[] = [
    { value: 'elapsed', label: $localize`:@@staff.board.order.elapsed:Más tiempo transcurrido` },
    { value: 'arrivalAsc', label: $localize`:@@staff.board.order.arrivalAsc:Llegada: más antiguos primero` },
    { value: 'arrivalDesc', label: $localize`:@@staff.board.order.arrivalDesc:Llegada: más recientes primero` },
  ];
  protected readonly order = new FormControl<BoardSort>('elapsed', { nonNullable: true });
  // Handing over and recalling are floor commands: the kitchen only prepares.
  protected readonly mayHandOver = computed(() => this.auth.role() !== 'KITCHEN');

  constructor() {
    inject(ScreenWakeLock).start();
    this.order.valueChanges.pipe(takeUntilDestroyed()).subscribe((sort) => this.store.setSortOrder(sort));
    this.station.valueChanges.pipe(takeUntilDestroyed()).subscribe((id) => this.store.setStation(id || null));
    effect(() => this.station.setValue(this.store.station() ?? '', { emitEvent: false }));
  }

  protected enableSound(): void {
    void this.sound.enable();
  }

  protected readonly shown = computed(() =>
    this.store.view() === 'ready' ? this.store.readyCards() : this.store.cards(),
  );
}
