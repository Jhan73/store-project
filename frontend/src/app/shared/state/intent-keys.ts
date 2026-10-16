// One idempotency key per user intent: every retry of the same intent reuses it until the intent succeeds.
export class IntentKeys {
  private readonly keys = new Map<string, string>();

  keyFor(intent: string): string {
    let key = this.keys.get(intent);
    if (key === undefined) {
      key = crypto.randomUUID();
      this.keys.set(intent, key);
    }
    return key;
  }

  release(intent: string): void {
    this.keys.delete(intent);
  }
}
