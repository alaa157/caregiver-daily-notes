import { describe, expect, it, vi } from 'vitest';
import { createNotesApi } from '../api/notes';
import { createApiClient } from '../lib/api';
import { jsonResponse } from './authFixtures';
import { historyNote, historyPage } from './historyFixtures';
import { recipientFixture } from './recipientFixtures';

const query = { from: '2026-10-01', to: '2026-10-22' };
function setup(body: unknown = historyPage()) {
  const fetcher = vi.fn().mockImplementation(() => Promise.resolve(jsonResponse(body)));
  return { api: createNotesApi(createApiClient({ apiBaseUrl: '' }, fetcher)), fetcher };
}
describe('history API contract', () => {
  it('uses inclusive backend dates, optional recipient, pagination and cancellation without ownership fields', async () => {
    const { api, fetcher } = setup(historyPage([historyNote], 2, 20, 41));
    const controller = new AbortController();
    expect(await api.history({ ...query, recipientId: recipientFixture.id, page: 2 }, controller.signal)).toEqual(historyPage([historyNote], 2, 20, 41));
    const [path, init] = fetcher.mock.calls[0];
    const url = new URL(path, 'http://localhost');
    expect(Object.fromEntries(url.searchParams)).toEqual({ ...query, recipientId: recipientFixture.id, page: '2', size: '20' });
    expect(init).toMatchObject({ method: 'GET', signal: controller.signal, cache: 'no-store' });
    expect(init.body).toBeUndefined();
    expect(url.searchParams.has('caregiverId')).toBe(false);
  });
  it('omits the recipient for all-recipient history and handles empty and out-of-range pages', async () => {
    const { api, fetcher } = setup(historyPage([]));
    expect(await api.history(query)).toEqual(historyPage([]));
    expect(fetcher.mock.calls[0][0]).not.toContain('recipientId');
    fetcher.mockResolvedValue(jsonResponse(historyPage([], 3, 20, 2)));
    expect(await api.history({ ...query, page: 3 })).toMatchObject({ content: [], totalElements: 2, last: true });
  });
  it('sends date-only history values unchanged', async () => {
    const { api, fetcher } = setup(historyPage([]));
    await api.history({ from: '2026-10-01', to: '2026-10-22' });
    const url = new URL(fetcher.mock.calls[0][0], 'http://localhost');
    expect(url.searchParams.get('from')).toBe('2026-10-01');
    expect(url.searchParams.get('to')).toBe('2026-10-22');
    expect(url.search).not.toMatch(/T\d{2}:\d{2}|Z/);
  });
  it('rejects reversed date ranges before fetch', async () => {
    const { api, fetcher } = setup();
    await expect(api.history({ from: '2026-10-22', to: '2026-10-01' })).rejects.toMatchObject({ status: 400 });
    expect(fetcher).not.toHaveBeenCalled();
  });
  it.each([
    { from: '' }, { to: '2026-02-30' }, { from: '2026-11-01' }, { recipientId: '../other' },
    { recipientId: '' }, { page: -1 }, { page: 0.5 }, { page: Number.MAX_SAFE_INTEGER + 1 }, { size: 0 }, { size: 101 }, { size: 1.5 },
  ])('rejects invalid queries before a request: %j', async (override) => {
    const { api, fetcher } = setup();
    await expect(api.history({ ...query, ...override })).rejects.toMatchObject({ status: 400 });
    expect(fetcher).not.toHaveBeenCalled();
  });
  it.each([
    null, [], {}, { ...historyPage(), content: null }, { ...historyPage(), totalElements: -1 },
    { ...historyPage(), totalElements: 1.5 }, { ...historyPage(), totalPages: 5 },
    { ...historyPage(), totalPages: -1 }, { ...historyPage(), totalPages: 0.5 },
    { ...historyPage(), number: 1 }, { ...historyPage(), size: 100 }, { ...historyPage(), last: false },
    { ...historyPage(), first: false }, historyPage([{ ...historyNote, date: '2026-09-30' }]),
    historyPage([{ ...historyNote, medicationStatus: 'YES' } as unknown as typeof historyNote]),
    historyPage([historyNote, historyNote]), { ...historyPage(), totalElements: 0, totalPages: 0 },
  ])('rejects malformed, duplicate or out-of-filter results: %j', async (body) => {
    await expect(setup(body).api.history(query)).rejects.toThrow();
  });
  it('rejects inconsistent page metadata', async () => {
    const responses = [
      { ...historyPage(), number: 1 },
      { ...historyPage(), number: -1 },
      { ...historyPage(), totalElements: -1 },
      { ...historyPage(), totalPages: -1 },
      { ...historyPage(), size: 0 },
      { ...historyPage(), size: 50 },
      { ...historyPage(), first: 'true' },
      { ...historyPage(), last: 1 },
    ];
    for (const body of responses) {
      await expect(setup(body).api.history(query)).rejects.toThrow('Invalid history response.');
    }
  });
  it('rejects a recipient mismatch and strips extra private response fields', async () => {
    await expect(setup().api.history({ ...query, recipientId: 'b2222222-2222-4222-8222-222222222222' })).rejects.toThrow();
    const body = { ...historyPage([{ ...historyNote, caregiverId: 'private' } as typeof historyNote]), internal: 'private' };
    expect(await setup(body).api.history(query)).toEqual(historyPage());
  });
});
