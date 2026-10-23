import { Injectable, signal } from '@angular/core';
import type { Shift } from '../../../core/api/api-types';

// The summary of the shift just closed, kept past the screen that closed it so a lost answer is never lost for good.
@Injectable({ providedIn: 'root' })
export class ClosedShiftHandoff {
  readonly shift = signal<Shift | null>(null);
}
