const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]']);
const PRODUCTION_HOSTS = new Set(['jugueria.jhanantezana.com', 'api.jugueria.jhanantezana.com']);

function hostOf(name: string, url: string): string {
  try {
    return new URL(url).hostname.toLowerCase();
  } catch {
    throw new Error(`${name} is not a valid URL.`);
  }
}

function isLocal(host: string): boolean {
  return LOCAL_HOSTS.has(host) || host.endsWith('.localhost');
}

function looksLikeProduction(host: string): boolean {
  return host.includes('prod') || PRODUCTION_HOSTS.has(host);
}

// Throws when the suite would run against a non-local or production target without explicit consent.
export function assertSafeTarget(urls: Record<string, string>, allowRemote: boolean): void {
  for (const [name, url] of Object.entries(urls)) {
    const host = hostOf(name, url);
    if (looksLikeProduction(host)) {
      throw new Error(`${name} points at a production host (${host}); the e2e suite never runs against production.`);
    }
    if (!isLocal(host) && !allowRemote) {
      throw new Error(`${name} points at a non-local host (${host}); set E2E_ALLOW_REMOTE=1 to run against it.`);
    }
  }
}
