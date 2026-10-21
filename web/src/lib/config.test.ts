import { describe, expect, it, vi } from 'vitest';
import { loadRuntimeConfig, parseRuntimeConfig } from './config';

describe('runtime configuration', () => {
  it('supports same-origin API and configurable HTTPS tunnel without a compiled URL', () => {
    expect(parseRuntimeConfig({ apiBaseUrl: '' }).apiBaseUrl).toBe('');
    expect(parseRuntimeConfig({ apiBaseUrl: 'https://tunnel.example/api/' }).apiBaseUrl).toBe('https://tunnel.example/api');
  });
  it('refuses unsafe URLs and secrets in the URL', () => {
    expect(() => parseRuntimeConfig({ apiBaseUrl: 'http://public.example' })).toThrow();
    expect(() => parseRuntimeConfig({ apiBaseUrl: 'https://user:pass@host.example' })).toThrow();
    expect(() => parseRuntimeConfig({ apiBaseUrl: 'https://host.example?key=foo' })).toThrow();
    expect(() => parseRuntimeConfig({})).toThrow();
  });
  it('fetches the config on each page load without using browser cache', async () => {
    const fetcher = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ apiBaseUrl: 'https://new-tunnel.example' }) });
    const result = await loadRuntimeConfig(fetcher as unknown as typeof fetch, '/config.json');
    expect(result.apiBaseUrl).toBe('https://new-tunnel.example');
    expect(fetcher).toHaveBeenCalledWith('/config.json', { cache: 'no-store' });
  });
});
