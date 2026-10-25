import { NgTemplateOutlet } from '@angular/common';
import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import {
  IconAlertTriangle,
  IconArrowBackUp,
  IconBan,
  IconCheck,
  IconChecks,
  IconListDetails,
  IconNote,
  IconPackageExport,
  IconPlayerPlay,
  IconRotate2,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Tooltip } from 'primeng/tooltip';
import type { BoardLine, BoardMarker, BoardOrder } from '../../../core/api/api-types';
import { formatInStoreZone } from '../../../core/time/store-time';
import { groupLines, indicatorsOf, LineGroup } from './board-lines';

export type BoardCardLevel = 'normal' | 'warning' | 'late';

const CHANNELS = {
  TABLE: $localize`:@@board.card.channel.table:Mesa`,
  COUNTER: $localize`:@@board.card.channel.counter:Mostrador`,
  PICKUP: $localize`:@@board.card.channel.pickup:Recojo`,
  DELIVERY: $localize`:@@board.card.channel.delivery:Delivery`,
} as const;

const MARKER_LABELS = {
  NEW: $localize`:@@board.card.marker.new:NUEVO`,
  MODIFIED: $localize`:@@board.card.marker.modified:MODIFICADO`,
  VOID: $localize`:@@board.card.marker.void:ANULADO`,
} as const;

@Component({
  selector: 'app-board-card',
  imports: [ButtonDirective, Dialog, NgTemplateOutlet, TablerIconComponent, Tooltip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './board-card.scss',
  template: `
    <article [attr.data-testid]="'card-' + order().id">
      <header [class]="level()">
        <span class="number" data-testid="short-number">{{ order().shortNumber }}</span>
        <span class="who">
          {{ channel() }}@if (order().label) {
            · {{ order().label }}
          }
        </span>
        <span class="age">
          <span class="elapsed" data-testid="elapsed" i18n="@@board.card.elapsed">{{ minutes() }} min</span>
          @if (level() === 'late') {
            <span class="level" data-testid="level" i18n="@@board.card.level.late">Atrasado</span>
          } @else if (level() === 'warning') {
            <span class="level" data-testid="level" i18n="@@board.card.level.warning">Atención</span>
          }
        </span>
        @if (scheduledTime(); as time) {
          <span class="scheduled" data-testid="scheduled" i18n="@@board.card.scheduled"
            >Programado {{ time }}</span
          >
        }
        <button
          pButton
          type="button"
          severity="secondary"
          class="detail"
          data-testid="detail"
          [attr.aria-haspopup]="'dialog'"
          [attr.aria-expanded]="detailOpen()"
          pTooltip="Ver el detalle completo de la comanda"
          i18n-pTooltip="@@board.card.detail.tooltip"
          tooltipPosition="top"
          (click)="detailOpen.set(true)"
        >
          <tabler-icon [icon]="icons.detail" aria-hidden="true" />
          <span i18n="@@board.card.detail">Detalle</span>
        </button>
      </header>

      @if (indicators().allergy || indicators().note) {
        <div class="indicators">
          @if (indicators().allergy) {
            <span class="indicator alert-allergy" data-testid="indicator-allergy">
              <tabler-icon [icon]="icons.allergy" aria-hidden="true" />
              <span i18n="@@board.card.indicator.allergy">Alergia</span>
            </span>
          }
          @if (indicators().note) {
            <span class="indicator alert-note" data-testid="indicator-note">
              <tabler-icon [icon]="icons.note" aria-hidden="true" />
              <span i18n="@@board.card.indicator.note">Nota</span>
            </span>
          }
        </div>
      }

      @for (item of headerMarkers(); track item.id) {
        <div class="marker" [class]="item.kind.toLowerCase()" [attr.data-testid]="'marker-' + item.id">
          <span class="label">{{ markerLabel(item) }}</span>
          <ng-container [ngTemplateOutlet]="ack" [ngTemplateOutletContext]="{ marker: item }" />
        </div>
      }

      <ul class="lines">
        @for (group of groups(); track group.id) {
          @let item = group.lines[0];
          <li
            class="line"
            [class.allergy]="item.allergy"
            [class.voided]="item.state === 'VOIDED'"
            [attr.data-testid]="'line-' + group.id"
          >
            <div class="what">
              <span class="quantity">{{ group.quantity }}×</span>
              <span class="name">{{ item.displayName }}</span>
              @if (item.allergy) {
                <span class="badge allergy-badge" data-testid="allergy">
                  <tabler-icon [icon]="icons.allergy" aria-hidden="true" />
                  <span i18n="@@board.card.allergy">Alergia</span>
                </span>
              }
            </div>
            @if (item.modifiersText) {
              <div class="modifiers">{{ item.modifiersText }}</div>
            }
            @if (item.note) {
              <div class="note">{{ item.note }}</div>
            }
            <div class="line-state">
              @if (stateOf(group); as state) {
                <span class="state" [class]="state" [attr.data-testid]="'line-state-' + group.id">
                  @switch (state) {
                    @case ('ready') {
                      <tabler-icon [icon]="icons.ready" aria-hidden="true" />
                      <span i18n="@@board.card.line.ready">Lista</span>
                    }
                    @case ('flagged') {
                      <tabler-icon [icon]="icons.flag" aria-hidden="true" />
                      <span i18n="@@board.card.line.flagged">Agotado</span>
                    }
                    @case ('voided') {
                      <tabler-icon [icon]="icons.flag" aria-hidden="true" />
                      <span i18n="@@board.card.line.voided">Anulada</span>
                    }
                  }
                </span>
              }
              @if (item.comped) {
                <span class="state comped" [attr.data-testid]="'comp-' + group.id" i18n="@@board.card.line.comped">Cortesía</span>
              }
              @for (tag of markersOf(item); track tag.id) {
                <span class="marker inline" [class]="tag.kind.toLowerCase()" [attr.data-testid]="'marker-' + tag.id">
                  <span class="label">{{ markerLabel(tag) }}</span>
                  <ng-container [ngTemplateOutlet]="ack" [ngTemplateOutletContext]="{ marker: tag }" />
                </span>
              }
              @if (groupQueued(group)) {
                <button
                  pButton
                  type="button"
                  severity="secondary"
                  [attr.data-testid]="'undo-line-' + group.id"
                  [attr.data-action]="'undo-line-' + group.id"
                  pTooltip="Cancelar el aviso de línea lista antes de que se envíe"
                  i18n-pTooltip="@@board.card.line.undo.tooltip"
                  tooltipPosition="top"
                  (click)="undoGroup(group)"
                >
                  <tabler-icon [icon]="icons.undo" aria-hidden="true" />
                  <span i18n="@@board.card.line.undo">Deshacer</span>
                </button>
              } @else if (mayReadyLine(group)) {
                <button
                  pButton
                  type="button"
                  [attr.data-testid]="'ready-line-' + group.id"
                  [attr.data-action]="'ready-line-' + group.id"
                  [disabled]="!enabled() || groupPending(group)"
                  pTooltip="Marcar esta línea como lista"
                  i18n-pTooltip="@@board.card.line.ready.tooltip"
                  tooltipPosition="top"
                  (click)="readyGroup(group)"
                >
                  <tabler-icon [icon]="icons.ready" aria-hidden="true" />
                  <span i18n="@@board.card.line.readyAction">Lista</span>
                </button>
              }
              @if (mayFlag(group)) {
                <button
                  pButton
                  type="button"
                  severity="secondary"
                  [attr.data-testid]="'flag-' + group.id"
                  [disabled]="!enabled() || groupPending(group)"
                  pTooltip="Avisar que este producto se agotó"
                  i18n-pTooltip="@@board.card.line.flag.tooltip"
                  tooltipPosition="top"
                  (click)="flagGroup(group)"
                >
                  <tabler-icon [icon]="icons.flag" aria-hidden="true" />
                  <span i18n="@@board.card.line.flag">Agotado</span>
                </button>
              }
            </div>
          </li>
        }
      </ul>

      <footer>
        @switch (order().status) {
          @case ('WAITING') {
            <button
              pButton
              type="button"
              data-testid="start"
              [disabled]="!enabled() || pending().has(order().id)"
              pTooltip="Empezar a preparar este pedido"
              i18n-pTooltip="@@board.card.start.tooltip"
              tooltipPosition="top"
              (click)="start.emit(order().id)"
            >
              <tabler-icon [icon]="icons.start" aria-hidden="true" />
              <span i18n="@@board.card.start">Iniciar</span>
            </button>
          }
          @case ('PREPARING') {
            @if (queued().has(order().id)) {
              <button
                pButton
                type="button"
                severity="secondary"
                data-testid="undo-order"
                [attr.data-action]="'undo-order'"
                pTooltip="Cancelar el aviso de pedido listo antes de que se envíe"
                i18n-pTooltip="@@board.card.undo.tooltip"
                tooltipPosition="top"
                (click)="undoReady(order().id, 'ready-order')"
              >
                <tabler-icon [icon]="icons.undo" aria-hidden="true" />
                <span i18n="@@board.card.undo">Deshacer</span>
              </button>
            } @else {
              <button
                pButton
                type="button"
                data-testid="ready-order"
                [attr.data-action]="'ready-order'"
                [disabled]="!enabled() || pending().has(order().id)"
                pTooltip="Marcar todo el pedido como listo"
                i18n-pTooltip="@@board.card.ready.tooltip"
                tooltipPosition="top"
                (click)="markReady(readyOrder, order().id, 'undo-order')"
              >
                <tabler-icon [icon]="icons.readyAll" aria-hidden="true" />
                <span i18n="@@board.card.ready">Todo listo</span>
              </button>
            }
          }
          @case ('READY') {
            @if (mayHandOver()) {
              <button
                pButton
                type="button"
                data-testid="handover"
                [disabled]="!enabled() || pending().has(order().id)"
                pTooltip="Entregar el pedido al cliente"
                i18n-pTooltip="@@board.card.handover.tooltip"
                tooltipPosition="top"
                (click)="handover.emit(order().id)"
              >
                <tabler-icon [icon]="icons.handover" aria-hidden="true" />
                <span i18n="@@board.card.handover">Entregar</span>
              </button>
              <button
                pButton
                type="button"
                severity="secondary"
                data-testid="recall"
                [disabled]="!enabled() || pending().has(order().id)"
                pTooltip="Volver a preparación un pedido terminado hace poco"
                i18n-pTooltip="@@board.card.recall.tooltip"
                tooltipPosition="top"
                (click)="recall.emit(order().id)"
              >
                <tabler-icon [icon]="icons.recall" aria-hidden="true" />
                <span i18n="@@board.card.recall">Reabrir</span>
              </button>
            }
          }
        }
      </footer>
    </article>

    @if (detailOpen()) {
      <p-dialog
        [header]="detailTitle()"
        [modal]="true"
        [draggable]="false"
        [closeOnEscape]="false"
        [dismissableMask]="true"
        [style]="{ width: 'min(40rem, calc(100vw - 1rem))' }"
        [visible]="true"
        (visibleChange)="closeDetail($event)"
        (keydown.escape)="closeDetail(false)"
      >
        <div class="detail-body" data-testid="detail-dialog">
          <ul class="detail-lines">
            @for (group of groups(); track group.id) {
              @let item = group.lines[0];
              <li [class.voided]="item.state === 'VOIDED'">
                <div class="detail-what">
                  <span class="quantity">{{ group.quantity }}×</span>
                  <span>{{ item.displayName }}</span>
                  @if (item.allergy) {
                    <span class="badge allergy-badge">
                      <tabler-icon [icon]="icons.allergy" aria-hidden="true" />
                      <span i18n="@@board.card.detail.allergy">Alergia</span>
                    </span>
                  }
                </div>
                @if (item.modifiersText) {
                  <div class="modifiers">{{ item.modifiersText }}</div>
                }
                @if (item.note) {
                  <div class="note">
                    <span class="note-label" i18n="@@board.card.detail.note">Nota:</span> {{ item.note }}
                  </div>
                }
              </li>
            }
          </ul>
        </div>
      </p-dialog>
    }

    <ng-template #ack let-marker="marker">
      <button
        pButton
        type="button"
        severity="secondary"
        [attr.data-testid]="'ack-' + marker.id"
        [disabled]="!enabled() || pending().has(marker.id)"
        pTooltip="Confirmar que viste este aviso"
        i18n-pTooltip="@@board.card.marker.ack.tooltip"
        tooltipPosition="top"
        (click)="acknowledge.emit(marker.id)"
      >
        <tabler-icon [icon]="icons.ack" aria-hidden="true" />
        <span i18n="@@board.card.marker.ack">Confirmar</span>
      </button>
    </ng-template>
  `,
})
export class BoardCard {
  readonly order = input.required<BoardOrder>();
  readonly minutes = input.required<number>();
  readonly level = input.required<BoardCardLevel>();
  readonly enabled = input.required<boolean>();
  // Ids with a request in flight, and ids whose Ready is still inside the undo window.
  readonly pending = input.required<ReadonlySet<string>>();
  readonly queued = input.required<ReadonlySet<string>>();
  readonly timeZone = input.required<string>();
  // Handing over and recalling belong to the floor; a kitchen device only prepares.
  readonly mayHandOver = input(true);

  readonly start = output<string>();
  readonly readyOrder = output<string>();
  readonly readyLine = output<string>();
  readonly undo = output<string>();
  readonly handover = output<string>();
  readonly recall = output<string>();
  readonly flag = output<string>();
  readonly acknowledge = output<string>();

  protected readonly icons = {
    allergy: IconAlertTriangle,
    ack: IconChecks,
    detail: IconListDetails,
    note: IconNote,
    flag: IconBan,
    handover: IconPackageExport,
    ready: IconCheck,
    readyAll: IconChecks,
    recall: IconRotate2,
    start: IconPlayerPlay,
    undo: IconArrowBackUp,
  };

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  // Ready and Undo replace each other, so the control that takes over gets the focus the clicked one had.
  private focusAfterSwap: string | null = null;

  constructor() {
    afterRenderEffect(() => {
      this.queued();
      const action = this.focusAfterSwap;
      this.focusAfterSwap = null;
      if (action) {
        this.host.nativeElement.querySelector<HTMLElement>(`[data-action="${action}"]`)?.focus();
      }
    });
  }

  protected readonly detailOpen = signal(false);

  protected markReady(emitter: { emit(id: string): void }, id: string, next: string): void {
    this.focusAfterSwap = next;
    emitter.emit(id);
  }

  protected undoReady(id: string, next: string): void {
    this.focusAfterSwap = next;
    this.undo.emit(id);
  }

  protected readyGroup(group: LineGroup): void {
    this.focusAfterSwap = 'undo-line-' + group.id;
    group.lines.forEach((member) => this.readyLine.emit(member.id));
  }

  protected undoGroup(group: LineGroup): void {
    this.focusAfterSwap = 'ready-line-' + group.id;
    group.lines.forEach((member) => this.undo.emit(member.id));
  }

  protected flagGroup(group: LineGroup): void {
    group.lines.forEach((member) => this.flag.emit(member.id));
  }

  protected closeDetail(visible: boolean): void {
    if (!visible && this.detailOpen()) {
      this.detailOpen.set(false);
      this.host.nativeElement.querySelector<HTMLElement>('[data-testid="detail"]')?.focus();
    }
  }

  protected readonly indicators = computed(() => indicatorsOf(this.order()));
  protected readonly groups = computed(() =>
    groupLines(this.order().lines, (member) =>
      this.markersOf(member)
        .map((tag) => tag.id)
        .join(','),
    ),
  );
  protected readonly detailTitle = computed(() =>
    this.order().label ? `${this.order().shortNumber} · ${this.order().label}` : this.order().shortNumber,
  );
  protected readonly channel = computed(() => CHANNELS[this.order().channel]);
  protected readonly scheduledTime = computed(() => {
    const fireAt = this.order().fireAt;
    return fireAt ? formatInStoreZone(fireAt, this.timeZone(), { timeStyle: 'short' }) : null;
  });
  private readonly unacknowledged = computed(() =>
    this.order().markers.filter((marker) => marker.acknowledgedAt === null),
  );
  protected readonly headerMarkers = computed(() => {
    const lineIds = new Set(this.order().lines.map((line) => line.id));
    return this.unacknowledged().filter((marker) => marker.lineId === null || !lineIds.has(marker.lineId));
  });

  protected markersOf(line: BoardLine): BoardMarker[] {
    return this.unacknowledged().filter((marker) => marker.lineId === line.id);
  }

  protected markerLabel(marker: BoardMarker): string {
    return MARKER_LABELS[marker.kind];
  }

  // A group is queued or pending as a whole: its lines are always sent together.
  protected groupQueued(group: LineGroup): boolean {
    return group.lines.every((member) => this.queued().has(member.id));
  }

  protected groupPending(group: LineGroup): boolean {
    return group.lines.some((member) => this.pending().has(member.id));
  }

  protected stateOf(group: LineGroup): 'ready' | 'flagged' | 'voided' | null {
    const line = group.lines[0];
    switch (line.state) {
      case 'VOIDED':
        return 'voided';
      case 'READY':
        return 'ready';
      default:
        if (line.flaggedUnavailable) {
          return 'flagged';
        }
        // A line already counts as ready while its Ready, or the whole order's, waits out the undo window.
        return this.groupQueued(group) || this.queued().has(this.order().id) ? 'ready' : null;
    }
  }

  protected mayReadyLine(group: LineGroup): boolean {
    const line = group.lines[0];
    return (
      this.order().status === 'PREPARING' &&
      line.state === 'PENDING' &&
      !line.flaggedUnavailable &&
      !this.queued().has(this.order().id)
    );
  }

  protected mayFlag(group: LineGroup): boolean {
    const line = group.lines[0];
    const open = this.order().status === 'WAITING' || this.order().status === 'PREPARING';
    const queued = this.groupQueued(group) || this.queued().has(this.order().id);
    return open && line.state === 'PENDING' && !line.flaggedUnavailable && !queued;
  }
}
