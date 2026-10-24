import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
} from '@angular/core';

let nextId = 0;

// Label, projected control and a message slot that is always rendered, so an error never moves the layout.
@Component({
  selector: 'app-form-field',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './form-field.scss',
  template: `
    <label [for]="controlId()">{{ label() }}</label>
    <ng-content />
    <small class="message" [class.error]="!!error()" [id]="messageId">{{ message() }}</small>
  `,
})
export class FormField {
  readonly label = input.required<string>();
  readonly controlId = input.required<string>();
  readonly error = input<string | null>(null);
  readonly hint = input<string | null>(null);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly messageId = `form-field-message-${nextId++}`;
  protected readonly message = computed(() => this.error() || this.hint() || '');

  constructor() {
    afterRenderEffect(() => {
      const control = this.host.nativeElement.querySelector(`[id="${this.controlId()}"]`);
      const own = this.message() ? this.messageId : null;
      if (!control) {
        return;
      }
      const others = (control.getAttribute('aria-describedby') ?? '')
        .split(' ')
        .filter((id) => id !== '' && id !== this.messageId);
      const ids = own ? [...others, own] : others;
      if (ids.length > 0) {
        control.setAttribute('aria-describedby', ids.join(' '));
      } else {
        control.removeAttribute('aria-describedby');
      }
    });
  }
}
