import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Tag } from 'primeng/tag';

export type StatusSeverity = 'success' | 'secondary' | 'info' | 'warn' | 'danger' | 'contrast';

// The text always carries the meaning; the severity only reinforces it.
@Component({
  selector: 'app-status-tag',
  imports: [Tag],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p-tag [value]="value()" [severity]="severity()" />`,
})
export class StatusTag {
  readonly value = input.required<string>();
  readonly severity = input<StatusSeverity>('secondary');
}
