import type { ReactNode } from 'react';
import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { createApiClient, ApiClientProvider } from '../../lib/api';
import type { Recipient } from '../../api/recipients';
import { recipientFixture } from '../../tests/recipientFixtures';
import { jsonResponse } from '../../tests/authFixtures';
import { noteListFixture } from '../../tests/noteFixtures';
import {
  LATEST_NOTE_METADATA_CONCURRENCY,
  mapLatestNoteMetadata,
  useLatestNoteMetadata,
} from './useLatestNoteMetadata';

function historyResponse(content: unknown[], totalElements = content.length) {
  return {
    content, number: 0, size: 1, totalElements, totalPages: totalElements,
    first: true, last: true,
  };
}
function recipient(id: string, lifecycleStatus: Recipient['lifecycleStatus'] = 'ACTIVE'): Recipient {
  const inactive = lifecycleStatus !== 'ACTIVE';
  return {
    ...recipientFixture, id, name: id, active: !inactive, lifecycleStatus,
    archivedAt: lifecycleStatus === 'ARCHIVED' ? '2026-10-01T00:00:00Z' : null,
    archivedBy: lifecycleStatus === 'ARCHIVED' ? recipientFixture.id : null,
  };
}
function renderMetadata(recipients: Recipient[], fetcher: ReturnType<typeof vi.fn>) {
  const client = createApiClient({ apiBaseUrl: '' }, fetcher as typeof fetch);
  const wrapper = ({ children }: { children: ReactNode }) => <ApiClientProvider client={client}>{children}</ApiClientProvider>;
  return renderHook(() => useLatestNoteMetadata(recipients), { wrapper });
}
function urlRecipient(path: string) {
  return new URL(path, 'http://localhost').searchParams.get('recipientId')!;
}

const first = recipientFixture.id;
const second = 'b2222222-2222-4222-8222-222222222222';
const third = 'c3333333-3333-4333-8333-333333333333';

describe('latest-note metadata mapping', () => {
  it('maps the newest history item to note metadata using its createdAt', () => {
    const page = historyResponse([{ ...noteListFixture, recipientId: first, createdAt: '2026-10-18T10:00:00Z' }], 2);
    expect(mapLatestNoteMetadata(first, page as never)).toEqual({
      recipientId: first, noteId: noteListFixture.id,
      lastUpdatedAt: '2026-10-18T10:00:00Z', state: 'recorded',
    });
  });

  it('marks only a confirmed zero-result page missing', () => {
    expect(mapLatestNoteMetadata(first, historyResponse([]) as never)).toEqual({
      recipientId: first, noteId: null, lastUpdatedAt: null, state: 'missing',
    });
    expect(mapLatestNoteMetadata(first, historyResponse([], 1) as never).state).toBe('error');
  });

  it('marks a note without a resolvable createdAt as an error', () => {
    const page = historyResponse([{ ...noteListFixture, recipientId: first, createdAt: undefined }]);
    expect(mapLatestNoteMetadata(first, page as never)).toEqual({
      recipientId: first, noteId: null, lastUpdatedAt: null, state: 'error',
    });
  });
});

describe('latest-note metadata hook', () => {
  it('returns recorded and positively missing rows in the original recipient order, including inactive recipients', async () => {
    const archived = recipient(second, 'ARCHIVED');
    const legacyInactive = recipient(third, 'LEGACY_INACTIVE_REVIEW');
    const recipients = [legacyInactive, archived, recipientFixture];
    const fetcher = vi.fn().mockImplementation((path: string) => {
      const recipientId = urlRecipient(path);
      if (recipientId === second) return Promise.resolve(jsonResponse(historyResponse([])));
      return Promise.resolve(jsonResponse(historyResponse([
        { ...noteListFixture, recipientId, createdAt: `2026-10-${recipientId === first ? '18' : '16'}T10:00:00Z` },
      ], 1)));
    });
    const { result } = renderMetadata(recipients, fetcher);

    await waitFor(() => expect(result.current.state.status).toBe('ready'));
    if (result.current.state.status !== 'ready') throw new Error('Expected ready metadata.');
    expect(result.current.state.data.map(({ recipientId, state }) => [recipientId, state])).toEqual([
      [third, 'recorded'], [second, 'missing'], [first, 'recorded'],
    ]);
    expect(result.current.state.data[0].lastUpdatedAt).toBe('2026-10-16T10:00:00Z');
    expect(result.current.state.data[1]).toMatchObject({ noteId: null, lastUpdatedAt: null });
    expect(result.current.state.data[2]).toMatchObject({ noteId: noteListFixture.id, lastUpdatedAt: '2026-10-18T10:00:00Z' });
    expect(fetcher.mock.calls).toHaveLength(3);
    expect(fetcher.mock.calls.every(([path]) => {
      const url = new URL(path, 'http://localhost');
      return url.pathname === '/api/notes' && url.searchParams.get('from') === '0001-01-01'
        && url.searchParams.get('to') === '9999-12-31' && url.searchParams.get('size') === '1';
    })).toBe(true);
  });

  it('keeps one recipient request failure as an error without hiding other rows', async () => {
    const recipients = [recipientFixture, recipient(second), recipient(third)];
    const fetcher = vi.fn().mockImplementation((path: string) => {
      const recipientId = urlRecipient(path);
      if (recipientId === second) return Promise.resolve(jsonResponse({ message: 'private failure' }, 500));
      if (recipientId === third) return Promise.resolve(jsonResponse(historyResponse([])));
      return Promise.resolve(jsonResponse(historyResponse([{ ...noteListFixture, recipientId: first }], 1)));
    });
    const { result } = renderMetadata(recipients, fetcher);

    await waitFor(() => expect(result.current.state.status).toBe('ready'));
    if (result.current.state.status !== 'ready') throw new Error('Expected ready metadata.');
    expect(result.current.state.data.map(({ state }) => state)).toEqual(['recorded', 'error', 'missing']);
    expect(result.current.state.data[1]).toMatchObject({ noteId: null, lastUpdatedAt: null });
  });

  it('cancels in-flight work on unmount and does not start queued recipient requests after abort', async () => {
    const recipients = Array.from({ length: 7 }, (_, index) => recipient(`d0000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`));
    const pending: Array<{ signal?: AbortSignal | null; resolve: (response: Response) => void }> = [];
    const fetcher = vi.fn().mockImplementation((_path: string, init?: RequestInit) =>
      new Promise<Response>((resolve) => pending.push({ signal: init?.signal, resolve })));
    const { unmount } = renderMetadata(recipients, fetcher);
    await waitFor(() => expect(pending).toHaveLength(LATEST_NOTE_METADATA_CONCURRENCY));
    unmount();

    expect(pending.every(({ signal }) => signal?.aborted)).toBe(true);
    await act(async () => {
      pending.forEach(({ resolve }) => resolve(jsonResponse(historyResponse([]))));
      await Promise.resolve();
    });
    expect(fetcher).toHaveBeenCalledTimes(LATEST_NOTE_METADATA_CONCURRENCY);
  });

  it('keeps request concurrency at the configured bound', async () => {
    const recipients = Array.from({ length: 9 }, (_, index) => recipient(`e0000000-0000-4000-8000-${String(index + 1).padStart(12, '0')}`));
    let inFlight = 0;
    let maximumInFlight = 0;
    const pending: Array<{ recipientId: string; resolve: (response: Response) => void }> = [];
    const fetcher = vi.fn().mockImplementation((path: string) => {
      inFlight += 1;
      maximumInFlight = Math.max(maximumInFlight, inFlight);
      const recipientId = urlRecipient(path);
      return new Promise<Response>((resolve) => pending.push({
        recipientId,
        resolve: (response) => { inFlight -= 1; resolve(response); },
      }));
    });
    const { result } = renderMetadata(recipients, fetcher);

    for (let index = 0; index < recipients.length; index += 1) {
      await waitFor(() => expect(pending.length).toBeGreaterThan(index));
      const request = pending[index];
      request.resolve(jsonResponse(historyResponse([{ ...noteListFixture, recipientId: request.recipientId }], 1)));
    }
    await waitFor(() => expect(result.current.state.status).toBe('ready'));
    expect(maximumInFlight).toBe(LATEST_NOTE_METADATA_CONCURRENCY);
    expect(fetcher).toHaveBeenCalledTimes(recipients.length);
  });
});
