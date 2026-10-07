import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import type { AuditEntry } from '../../../core/api/api-types';

@Component({
  selector: 'app-audit-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './audit-detail.scss',
  template: `
    <dl>
      <dt i18n="@@admin.audit.detail.when">Cuándo</dt>
      <dd>{{ when() }}</dd>
      <dt i18n="@@admin.audit.detail.actor">Quién</dt>
      <dd>{{ actor() }}</dd>
      <dt i18n="@@admin.audit.detail.action">Acción</dt>
      <dd>
        <code>{{ entry().action }}</code>
      </dd>
      <dt i18n="@@admin.audit.detail.entity">Entidad</dt>
      <dd>
        {{ entry().entityType }} <code>{{ entry().entityId }}</code>
      </dd>
      <dt i18n="@@admin.audit.detail.reason">Motivo</dt>
      <dd>{{ entry().reason ?? '—' }}</dd>
      <dt i18n="@@admin.audit.detail.correlation">Id de la solicitud</dt>
      <dd>
        @if (entry().correlationId) {
          <code>{{ entry().correlationId }}</code>
        } @else {
          —
        }
      </dd>
    </dl>

    <div class="sides">
      @for (side of sides(); track side.key) {
        <section>
          <h3 [id]="'audit-side-' + side.key">{{ side.title }}</h3>
          @if (side.text !== null) {
            <pre
              role="region"
              tabindex="0"
              [attr.aria-labelledby]="'audit-side-' + side.key"
              >{{ side.text }}</pre
            >
          } @else {
            <p class="muted" i18n="@@admin.audit.detail.none">Sin valor registrado</p>
          }
        </section>
      }
    </div>
  `,
})
export class AuditDetail {
  readonly entry = input.required<AuditEntry>();
  readonly when = input.required<string>();
  readonly actor = input.required<string>();

  protected readonly sides = computed(() => [
    {
      key: 'before',
      title: $localize`:@@admin.audit.detail.before:Antes`,
      text: pretty(this.entry().before),
    },
    {
      key: 'after',
      title: $localize`:@@admin.audit.detail.after:Después`,
      text: pretty(this.entry().after),
    },
  ]);
}

function pretty(value: unknown): string | null {
  return value === null || value === undefined ? null : JSON.stringify(value, null, 2);
}
