import { inject, Injectable } from '@angular/core';
import { MessageService } from 'primeng/api';
import { ApiError } from './api-error';
import { errorMessage, supportCodeMessage } from './error-messages';

@Injectable({ providedIn: 'root' })
export class ErrorNotifier {
  private readonly messages = inject(MessageService);

  show(error: unknown): void {
    const correlationId = error instanceof ApiError ? error.correlationId : null;
    this.messages.add({
      severity: 'error',
      summary: errorMessage(error),
      detail: correlationId ? supportCodeMessage(correlationId) : undefined,
    });
  }
}
