import { TestBed } from '@angular/core/testing';
import { AUDIO_CONTEXT_FACTORY, BoardSound, SOUND_PATTERNS } from './board-sound';

class FakeOscillator {
  readonly frequency = { setValueAtTime: vi.fn() };
  type = 'sine';
  readonly connect = vi.fn();
  readonly start = vi.fn();
  readonly stop = vi.fn();
}

class FakeContext {
  state: 'suspended' | 'running' | 'interrupted' = 'suspended';
  onstatechange: (() => void) | null = null;
  readonly currentTime = 10;
  readonly destination = {};
  readonly oscillators: FakeOscillator[] = [];
  readonly resume = vi.fn(() => {
    this.state = 'running';
    return Promise.resolve();
  });
  readonly close = vi.fn(() => Promise.resolve());

  // What the browser does when it suspends or interrupts the audio on its own.
  moveTo(state: 'suspended' | 'running' | 'interrupted') {
    this.state = state;
    this.onstatechange?.();
  }

  createOscillator() {
    const oscillator = new FakeOscillator();
    this.oscillators.push(oscillator);
    return oscillator;
  }

  createGain() {
    return {
      gain: { setValueAtTime: vi.fn(), linearRampToValueAtTime: vi.fn() },
      connect: vi.fn(),
    };
  }
}

describe('BoardSound', () => {
  let context: FakeContext;

  function create(factory: () => FakeContext | null = () => context) {
    TestBed.configureTestingModule({
      providers: [BoardSound, { provide: AUDIO_CONTEXT_FACTORY, useValue: factory }],
    });
    return TestBed.inject(BoardSound);
  }

  beforeEach(() => {
    context = new FakeContext();
  });

  it('starts silent and unable to play until someone taps', () => {
    const sound = create();

    expect(sound.enabled()).toBe(false);
    sound.play(['NEW']);
    expect(context.oscillators).toEqual([]);
  });

  it('enables on the tap, wakes the audio context and confirms with a chime', async () => {
    const sound = create();

    await sound.enable();

    expect(sound.enabled()).toBe(true);
    expect(context.resume).toHaveBeenCalled();
    expect(context.oscillators.length).toBeGreaterThan(0);
  });

  it('gives each kind of marker its own sound', () => {
    const signature = (kind: 'NEW' | 'MODIFIED' | 'VOID') =>
      JSON.stringify(SOUND_PATTERNS[kind].map(({ frequency, type, at, length }) => [frequency, type, at, length]));

    expect(new Set([signature('NEW'), signature('MODIFIED'), signature('VOID')]).size).toBe(3);
  });

  it.each(['NEW', 'MODIFIED', 'VOID'] as const)('plays the notes of %s through oscillators', async (kind) => {
    const sound = create();
    await sound.enable();
    context.oscillators.length = 0;

    sound.play([kind]);

    expect(context.oscillators.map((oscillator) => oscillator.frequency.setValueAtTime.mock.calls[0][0])).toEqual(
      SOUND_PATTERNS[kind].map((note) => note.frequency),
    );
    for (const oscillator of context.oscillators) {
      expect(oscillator.start).toHaveBeenCalledTimes(1);
      expect(oscillator.stop).toHaveBeenCalledTimes(1);
    }
  });

  it('plays several kinds one after the other, not on top of each other', async () => {
    const sound = create();
    await sound.enable();
    context.oscillators.length = 0;

    sound.play(['VOID', 'NEW']);

    const starts = context.oscillators.map((oscillator) => oscillator.start.mock.calls[0][0] as number);
    const voidNotes = SOUND_PATTERNS.VOID.length;
    expect(Math.min(...starts.slice(voidNotes))).toBeGreaterThan(Math.max(...starts.slice(0, voidNotes)));
  });

  it('stays off and says so when the browser has no audio', async () => {
    const sound = create(() => null);

    await sound.enable();

    expect(sound.enabled()).toBe(false);
    expect(sound.unsupported()).toBe(true);
  });

  it('stays off when the audio context refuses to start', async () => {
    context.resume.mockRejectedValueOnce(new Error('blocked'));
    const sound = create();

    await sound.enable();

    expect(sound.enabled()).toBe(false);
  });

  it.each(['suspended', 'interrupted'] as const)('turns itself off when the audio becomes %s', async (state) => {
    const sound = create();
    await sound.enable();

    context.moveTo(state);

    expect(sound.enabled()).toBe(false);
    context.oscillators.length = 0;
    sound.play(['NEW']);
    expect(context.oscillators).toEqual([]);
  });

  it('turns itself back on when the audio runs again, and can be enabled again with a tap', async () => {
    const sound = create();
    await sound.enable();
    context.moveTo('suspended');

    context.moveTo('running');
    expect(sound.enabled()).toBe(true);

    context.moveTo('suspended');
    await sound.enable();
    expect(sound.enabled()).toBe(true);
  });

  it('stops listening to the audio context when it is destroyed', async () => {
    const sound = create();
    await sound.enable();

    TestBed.resetTestingModule();

    expect(context.onstatechange).toBeNull();
    context.moveTo('running');
    expect(sound.enabled()).toBe(false);
  });

  it('releases the audio context when it is destroyed', async () => {
    const sound = create();
    await sound.enable();

    TestBed.resetTestingModule();

    expect(context.close).toHaveBeenCalled();
    expect(sound.enabled()).toBe(false);
  });
});
