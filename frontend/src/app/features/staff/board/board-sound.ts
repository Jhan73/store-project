import { isPlatformBrowser } from '@angular/common';
import { DestroyRef, inject, Injectable, InjectionToken, PLATFORM_ID, signal } from '@angular/core';
import type { MarkerKind } from './board-markers';

export interface Note {
  readonly frequency: number;
  readonly type: OscillatorType;
  // Seconds from the start of the pattern, and how long the note lasts.
  readonly at: number;
  readonly length: number;
}

// Three sounds a cook can tell apart without looking: rising pair, triple beep, low falling buzz.
export const SOUND_PATTERNS: Record<MarkerKind, readonly Note[]> = {
  NEW: [
    { frequency: 660, type: 'sine', at: 0, length: 0.18 },
    { frequency: 880, type: 'sine', at: 0.22, length: 0.3 },
  ],
  MODIFIED: [
    { frequency: 740, type: 'triangle', at: 0, length: 0.12 },
    { frequency: 740, type: 'triangle', at: 0.2, length: 0.12 },
    { frequency: 740, type: 'triangle', at: 0.4, length: 0.12 },
  ],
  VOID: [
    { frequency: 330, type: 'square', at: 0, length: 0.25 },
    { frequency: 220, type: 'square', at: 0.3, length: 0.45 },
  ],
};

const CONFIRMATION: Note = { frequency: 880, type: 'sine', at: 0, length: 0.15 };
const PAUSE_BETWEEN_KINDS = 0.3;
const VOLUME = 0.4;
const SILENCE = 0.0001;

export const AUDIO_CONTEXT_FACTORY = new InjectionToken<() => AudioContext | null>('AUDIO_CONTEXT_FACTORY', {
  providedIn: 'root',
  factory: () => {
    const browser = isPlatformBrowser(inject(PLATFORM_ID));
    return () => (browser && typeof AudioContext !== 'undefined' ? new AudioContext() : null);
  },
});

const patternLength = (notes: readonly Note[]) => Math.max(...notes.map((note) => note.at + note.length));

// Provided by the screen itself. Browsers keep audio locked until a tap, so the sound starts off.
@Injectable()
export class BoardSound {
  private readonly createContext = inject(AUDIO_CONTEXT_FACTORY);
  private readonly on = signal(false);
  private readonly noAudio = signal(false);
  private context: AudioContext | null = null;

  readonly enabled = this.on.asReadonly();
  readonly unsupported = this.noAudio.asReadonly();

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.on.set(false);
      void this.context?.close().catch(() => undefined);
      this.context = null;
    });
  }

  async enable(): Promise<void> {
    if (this.on()) {
      return;
    }
    const context = this.context ?? this.createContext();
    if (!context) {
      this.noAudio.set(true);
      return;
    }
    this.context = context;
    try {
      await context.resume();
    } catch {
      return;
    }
    this.on.set(true);
    this.note(context, CONFIRMATION, context.currentTime);
  }

  play(kinds: readonly MarkerKind[]): void {
    const context = this.context;
    if (!context || !this.on()) {
      return;
    }
    let offset = 0;
    for (const kind of kinds) {
      for (const note of SOUND_PATTERNS[kind]) {
        this.note(context, note, context.currentTime + offset + note.at);
      }
      offset += patternLength(SOUND_PATTERNS[kind]) + PAUSE_BETWEEN_KINDS;
    }
  }

  private note(context: AudioContext, note: Note, startAt: number): void {
    const oscillator = context.createOscillator();
    const gain = context.createGain();
    oscillator.type = note.type;
    oscillator.frequency.setValueAtTime(note.frequency, startAt);
    gain.gain.setValueAtTime(SILENCE, startAt);
    gain.gain.linearRampToValueAtTime(VOLUME, startAt + 0.02);
    gain.gain.linearRampToValueAtTime(SILENCE, startAt + note.length);
    oscillator.connect(gain);
    gain.connect(context.destination);
    oscillator.start(startAt);
    oscillator.stop(startAt + note.length);
  }
}
