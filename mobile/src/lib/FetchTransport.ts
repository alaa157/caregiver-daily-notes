import {ServerUrlStore} from './ServerUrlStore';
import {TokenStore} from './TokenStore';
import {ApiError, toApiError} from './errors';
import {HttpMethod, RequestOptions, Transport} from './Transport';

const TIMEOUT_MS = 30000;

/** Production transport: fetch against the runtime server URL with the stored token. */
export class FetchTransport implements Transport {
  async request<T>(method: HttpMethod, path: string, options?: RequestOptions): Promise<T> {
    const baseUrl = await ServerUrlStore.get();
    const token = await TokenStore.get();
    const headers: Record<string, string> = {'Content-Type': 'application/json'};
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
    try {
      const response = await fetch(`${baseUrl}${path}`, {
        method,
        headers,
        signal: controller.signal,
        body: options?.body !== undefined ? JSON.stringify(options.body) : undefined,
      });
      let body: unknown = null;
      try {
        body = await response.json();
      } catch {
        body = null;
      }
      if (!response.ok) {
        throw toApiError(response.status, body);
      }
      if (typeof body === 'object' && body !== null && 'error' in body) {
        const marker = (body as {error: unknown}).error;
        if (typeof marker === 'string' && marker.startsWith('AI_UNAVAILABLE')) {
          throw toApiError(response.status, body);
        }
      }
      return body as T;
    } catch (e) {
      if (e instanceof ApiError) {
        throw e;
      }
      throw new ApiError('NETWORK', e instanceof Error ? e.message : 'Network request failed');
    } finally {
      clearTimeout(timer);
    }
  }
}
