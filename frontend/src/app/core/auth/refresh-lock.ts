// Tabs share one refresh cookie, so refreshes must not overlap across tabs: the backend reads an overlap as a
// stolen-token replay and revokes the whole session. Without the Web Locks API the tab refreshes on its own.
export function runExclusive<T>(name: string, task: () => Promise<T>): Promise<T> {
  const locks = typeof navigator === 'undefined' ? undefined : navigator.locks;
  return locks ? locks.request(name, task) : task();
}
