import { describe, expect, it, vi } from 'vitest';
import { ApiError, createApiClient } from './api';

describe('typed API transport', () => {
  it('uses a runtime base URL including a path prefix', async () => {
    const fetcher = vi.fn().mockResolvedValue({ ok: true, status: 200, headers: new Headers({ 'content-type': 'application/json' }), json: async () => ({ id: 'a1' }) });
    const api = createApiClient({ apiBaseUrl: 'https://tunnel.example/api' }, fetcher as typeof fetch);
    const response = await api.request<{ id: string }>({ path: '/caregivers' });
    expect(response).toEqual({ id: 'a1' });
    expect(fetcher.mock.calls[0][0]).toBe('https://tunnel.example/api/caregivers');
  });
  it('supports same-origin calls and typed JSON payloads', async () => {
    const fetcher = vi.fn().mockResolvedValue({ ok: true, status: 204, headers: new Headers() });
    const api = createApiClient({ apiBaseUrl: '' }, fetcher as typeof fetch);
    await api.request<void, { name: string }>({ path: '/any-endpoint', method: 'POST', body: { name: 'Test' } });
    expect(fetcher.mock.calls[0][0]).toBe('/any-endpoint');
    expect(fetcher.mock.calls[0][1].body).toBe('{"name":"Test"}');
    expect(fetcher.mock.calls[0][1].headers.get('Content-Type')).toBe('application/json');
  });
  it('converts API failures into typed errors', async () => {
    const fetcher = vi.fn().mockResolvedValue({ ok: false, status: 403, headers: new Headers({ 'content-type': 'application/json' }), json: async () => ({ code: 'FORBIDDEN' }) });
    const api = createApiClient({ apiBaseUrl: '' }, fetcher as typeof fetch);
    await expect(api.request({ path: '/private' })).rejects.toBeInstanceOf(ApiError);
  });
  it('rejects external paths and GET request bodies', async () => {
    const api = createApiClient({ apiBaseUrl: '' });
    await expect(api.request({ path: '//evil.example' })).rejects.toThrow();
    await expect(api.request({ path: '/notes', body: { x: 1 } })).rejects.toThrow();
  });
});
