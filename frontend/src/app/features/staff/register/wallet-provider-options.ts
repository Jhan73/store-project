import { computed, inject, Injectable, signal } from '@angular/core';
import type { WalletProviderOption } from '../../../core/api/api-types';
import { ApiError } from '../../../core/errors/api-error';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { RegisterApi } from './register-api';

// Provided by the screen that takes wallet payments; the list is read the first time a wallet is chosen.
@Injectable()
export class WalletProviderOptions {
  private readonly api = inject(RegisterApi);
  private readonly notifier = inject(ErrorNotifier);
  private requested = false;

  private readonly list = signal<WalletProviderOption[]>([]);
  private readonly state = signal<'idle' | 'loading' | 'loaded' | 'failed'>('idle');

  readonly providers = this.list.asReadonly();
  readonly loaded = computed(() => this.state() === 'loaded');
  readonly failed = computed(() => this.state() === 'failed');

  // A refusal that names the wallet means the list the cashier picked from is stale.
  static refuses(error: unknown): boolean {
    return (
      error instanceof ApiError &&
      (error.code === 'instore.wallet-provider-inactive' || error.code === 'instore.wallet-provider-not-found')
    );
  }

  load(force = false): void {
    if (this.requested && !force) {
      return;
    }
    this.requested = true;
    this.state.set('loading');
    this.api.walletProviders().subscribe({
      next: (providers) => {
        this.list.set(providers);
        this.state.set('loaded');
      },
      error: (error: unknown) => {
        this.requested = false;
        this.state.set('failed');
        this.notifier.show(error);
      },
    });
  }
}
