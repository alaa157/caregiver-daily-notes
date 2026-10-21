import { createContext, useContext, useMemo, type ReactNode } from 'react';
import type { RuntimeConfig } from './config';

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface ApiRequest<TBody = unknown> {
  path: `/${string}`;
  method?: HttpMethod;
  body?: TBody;
  headers?: HeadersInit;
  signal?: AbortSignal;
  /** Supplied by the session provider for authenticated requests. */
  accessToken?: string;
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly details: unknown,
  ) {
    super(`API request failed (HTTP ${status}).`);
    this.name = 'ApiError';
  }
}

/** Central, typed API transport. The backend remains authoritative for endpoints and validation. */
export function createApiClient(config: RuntimeConfig, fetcher: typeof fetch = fetch) {
  return {
    baseUrl: config.apiBaseUrl,
    async request<TResponse, TBody = unknown>({
      path, method = 'GET', body, headers, signal, accessToken,
    }: ApiRequest<TBody>): Promise<TResponse> {
      if (!path.startsWith('/') || path.startsWith('//') || path.includes('\\') || /[\u0000-\u001f\u007f]/.test(path)) {
        throw new Error('API paths must be root-relative paths, not external URLs.');
      }
      if (body !== undefined && (method === 'GET')) throw new Error('GET requests cannot include a body.');
      // Preserve an optional base path such as https://host.example/api.
      const base = config.apiBaseUrl.replace(/\/+$/, '');
      const endpoint = base.endsWith('/api') && path.startsWith('/api/') ? path.slice(4) : path;
      const url = `${base}${endpoint}`;
      const requestHeaders = new Headers(headers);
      if (body !== undefined && !requestHeaders.has('Content-Type')) requestHeaders.set('Content-Type', 'application/json');
      if (!requestHeaders.has('Accept')) requestHeaders.set('Accept', 'application/json');
      if (accessToken) requestHeaders.set('Authorization', `Bearer ${accessToken}`);
      const response = await fetcher(url, {
        method,
        headers: requestHeaders,
        signal,
        cache: 'no-store',
        credentials: 'omit',
        redirect: 'error',
        referrerPolicy: 'no-referrer',
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      if (response.status === 204) return undefined as TResponse;
      const contentType = response.headers.get('content-type') ?? '';
      let payload: unknown;
      try {
        payload = contentType.includes('json') ? await response.json() : await response.text();
      } catch {
        if (response.ok) throw new Error('The API returned an invalid response.');
        // An invalid error body must not hide the HTTP status, especially a 401.
        payload = null;
      }
      if (!response.ok) throw new ApiError(response.status, payload);
      return payload as TResponse;
    },
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;
const ApiContext = createContext<ApiClient | null>(null);

export function ApiProvider({ config, children }: { config: RuntimeConfig; children: ReactNode }) {
  const client = useMemo(() => createApiClient(config), [config.apiBaseUrl]);
  return <ApiClientProvider client={client}>{children}</ApiClientProvider>;
}

/** Lets the auth boundary add bearer headers and central 401 handling. */
export function ApiClientProvider({ client, children }: { client: ApiClient; children: ReactNode }) {
  return <ApiContext.Provider value={client}>{children}</ApiContext.Provider>;
}

export function useApiClient(): ApiClient {
  const client = useContext(ApiContext);
  if (!client) throw new Error('useApiClient must be used inside ApiProvider.');
  return client;
}
