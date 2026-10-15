import type { BoardOrder, BoardThresholds } from '../../../core/api/api-types';

export type AgeLevel = 'normal' | 'warning' | 'late';

export interface AgeThresholds {
  readonly warningMinutes: number;
  readonly lateMinutes: number;
}

export const DEFAULT_AGE_THRESHOLDS: AgeThresholds = { warningMinutes: 5, lateMinutes: 10 };

export function ageLevel(minutes: number, thresholds: AgeThresholds): AgeLevel {
  if (minutes >= thresholds.lateMinutes) {
    return 'late';
  }
  return minutes >= thresholds.warningMinutes ? 'warning' : 'normal';
}

export function thresholdsOf(response: BoardThresholds): AgeThresholds {
  return { warningMinutes: response.boardWarningMinutes, lateMinutes: response.boardLateMinutes };
}

// A scheduled card starts aging when it fires, not when it was sent.
export function ageStartOf(order: Pick<BoardOrder, 'sentAt' | 'fireAt'>): string {
  return order.fireAt ?? order.sentAt;
}
