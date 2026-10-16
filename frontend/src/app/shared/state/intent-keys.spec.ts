import { IntentKeys } from './intent-keys';

describe('IntentKeys', () => {
  it('reuses the key of an intent until it is released', () => {
    const keys = new IntentKeys();

    const first = keys.keyFor('open:t1');

    expect(keys.keyFor('open:t1')).toBe(first);
    expect(keys.keyFor('open:t2')).not.toBe(first);
  });

  it('gives a released intent a new key', () => {
    const keys = new IntentKeys();
    const first = keys.keyFor('open:t1');

    keys.release('open:t1');

    expect(keys.keyFor('open:t1')).not.toBe(first);
  });
});
