/** Public, non-secret configuration fetched when the browser opens the app. */
export interface RuntimeConfig {
  apiBaseUrl: string;
}

export function parseRuntimeConfig(value: unknown): RuntimeConfig {
  if (typeof value !== 'object' || value === null || !('apiBaseUrl' in value)) {
    throw new Error('Runtime config must define apiBaseUrl.');
  }
  const raw = value.apiBaseUrl;
  if (typeof raw !== 'string') {
    throw new Error('Runtime config apiBaseUrl must be a string.');
  }
  const base = raw.trim().replace(/\/+$/, '');
  if (!base) return { apiBaseUrl: '' }; // Explicit same-origin configuration.
  let url: URL;
  try {
    url = new URL(base);
  } catch {
    throw new Error('Runtime config apiBaseUrl must be an absolute URL.');
  }
  const loopback = url.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname);
  if ((!loopback && url.protocol !== 'https:') || url.username || url.password || url.search || url.hash) {
    throw new Error('Runtime config requires HTTPS (or loopback HTTP) without embedded credentials, query, or fragment.');
  }
  return { apiBaseUrl: base };
}

export async function loadRuntimeConfig(
  fetcher: typeof fetch = fetch,
  configPath = `${import.meta.env.BASE_URL}config.json`,
): Promise<RuntimeConfig> {
  const response = await fetcher(configPath, { cache: 'no-store' });
  if (!response.ok) throw new Error(`Cannot load runtime config (HTTP ${response.status}).`);
  return parseRuntimeConfig(await response.json());
}
