import {ApiClient} from '../lib/ApiClient';
import {ApiError} from '../lib/errors';
import {FakeTransport} from '../lib/FakeTransport';
import {FetchTransport} from '../lib/FetchTransport';
import {TokenStore} from '../lib/TokenStore';

jest.mock('@react-native-async-storage/async-storage', () => require('../test-utils/inMemoryStorage').mock);
jest.mock('../lib/ServerUrlStore', () => ({
  ServerUrlStore: {
    get: jest.fn(async () => 'https://test.tunnel'),
    set: jest.fn(),
    reset: jest.fn(),
  },
}));

function okJson(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as Response;
}

describe('API client', () => {
  beforeEach(async () => {
    await TokenStore.clear();
    (globalThis.fetch as unknown as jest.Mock | undefined)?.mockClear?.();
  });

  it('returns typed bodies from scripted routes and records calls', async () => {
    const transport = new FakeTransport({'GET /api/recipients': {status: 200, body: [{id: 'r1'}]}});
    const client = new ApiClient(transport);

    const body = await client.get<Array<{id: string}>>('/api/recipients');

    expect(body).toEqual([{id: 'r1'}]);
    expect(transport.calls).toEqual([{method: 'GET', path: '/api/recipients'}]);
  });

  it('maps 401 to UNAUTHORIZED', async () => {
    const transport = new FakeTransport({'GET /api/me': {status: 401, body: {message: 'nope'}}});
    const client = new ApiClient(transport);

    const err = await client.get('/api/me').catch(e => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).code).toBe('UNAUTHORIZED');
  });

  it('maps transport throws to NETWORK', async () => {
    const transport = new FakeTransport({'GET /api/me': new Error('boom')});
    const client = new ApiClient(transport);

    const err = await client.get('/api/me').catch(e => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).code).toBe('NETWORK');
  });

  it('detects AI-unavailable marker bodies', async () => {
    const transport = new FakeTransport({
      'POST /api/summaries': {status: 200, body: {error: 'AI_UNAVAILABLE: down'}},
    });
    const client = new ApiClient(transport);

    const err = await client.post('/api/summaries', {}).catch(e => e);
    expect((err as ApiError).code).toBe('AI_UNAVAILABLE');
  });

  it('maps 403/422/500 to FORBIDDEN/VALIDATION/SERVER', async () => {
    const transport = new FakeTransport({
      'GET /a': {status: 403, body: {}},
      'GET /b': {status: 422, body: {}},
      'GET /c': {status: 500, body: {}},
    });
    const client = new ApiClient(transport);

    for (const [path, code] of [
      ['/a', 'FORBIDDEN'],
      ['/b', 'VALIDATION'],
      ['/c', 'SERVER'],
    ] as const) {
      const err = await client.get(path).catch(e => e);
      expect((err as ApiError).code).toBe(code);
    }
  });

  it('sends no auth header without a token, Bearer with one', async () => {
    const client = new ApiClient(new FakeTransport({}));
    expect(await client.authHeader()).toBeUndefined();

    await TokenStore.set('tok123');
    expect(await client.authHeader()).toBe('Bearer tok123');
  });

  it('round-trips the token store', async () => {
    expect(await TokenStore.get()).toBeNull();
    await TokenStore.set('abc');
    expect(await TokenStore.get()).toBe('abc');
    await TokenStore.clear();
    expect(await TokenStore.get()).toBeNull();
  });

  it('FetchTransport builds serverUrl + path with auth', async () => {
    await TokenStore.set('tok123');
    const fetchMock = jest.fn(async () => okJson({ok: true}));
    globalThis.fetch = fetchMock as unknown as typeof fetch;
    const client = new ApiClient(new FetchTransport());

    const body = await client.get<{ok: boolean}>('/api/ping');

    expect(body).toEqual({ok: true});
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('https://test.tunnel/api/ping');
    expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer tok123');
  });

  it('uses a changed server URL on the next request and preserves the token', async () => {
    await TokenStore.set('tok123');
    const {ServerUrlStore} = jest.requireMock('../lib/ServerUrlStore') as {
      ServerUrlStore: {get: jest.Mock};
    };
    ServerUrlStore.get
      .mockResolvedValueOnce('https://first.tunnel')
      .mockResolvedValueOnce('https://second.tunnel');
    const fetchMock: jest.Mock = jest.fn(async () => okJson({ok: true}));
    globalThis.fetch = fetchMock as unknown as typeof fetch;
    const transport = new FetchTransport();

    await transport.request('GET', '/api/first');
    await transport.request('GET', '/api/second');

    const calls = fetchMock.mock.calls as unknown as Array<[string, RequestInit]>;
    expect(calls.map(([url]) => url)).toEqual([
      'https://first.tunnel/api/first',
      'https://second.tunnel/api/second',
    ]);
    for (const [, init] of calls) {
      expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer tok123');
    }
  });
});
