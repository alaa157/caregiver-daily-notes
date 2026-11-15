import type { ReactNode } from 'react';
import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { NoteListItem } from '../../api/notes';
import { jsonResponse } from '../../tests/authFixtures';
import { historyPage } from '../../tests/historyFixtures';
import { noteListFixture } from '../../tests/noteFixtures';
import { createApiClient, ApiClientProvider } from '../../lib/api';
import { useTodayNotes } from './useTodayNotes';

const DATE = '2026-10-18';
const FIRST_RECIPIENT = '11111111-1111-4111-8111-111111111111';
const SECOND_RECIPIENT = '22222222-2222-4222-8222-222222222222';

function noteFor(id: string, recipientId: string): NoteListItem {
  return { ...noteListFixture, id, recipientId, date: DATE };
}

function renderTodayNotes(fetcher: ReturnType<typeof vi.fn>, date = DATE) {
  const client = createApiClient({ apiBaseUrl: '' }, fetcher as typeof fetch);
  const wrapper = ({ children }: { children: ReactNode }) => <ApiClientProvider client={client}>{children}</ApiClientProvider>;
  return renderHook(() => useTodayNotes(date), { wrapper });
}

function requestUrl(call: unknown[]) {
  return new URL(String(call[0]), 'http://localhost');
}

describe('useTodayNotes', () => {
  it('builds the recipient map from a single history page', async () => {
    const first = noteFor('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', FIRST_RECIPIENT);
    const second = noteFor('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', SECOND_RECIPIENT);
    const fetcher = vi.fn().mockImplementation(() => Promise.resolve(jsonResponse(historyPage([first, second], 0, 100, 2))));
    const { result } = renderTodayNotes(fetcher);

    await waitFor(() => expect(result.current.state).toBe('ready'));
    expect(result.current.byRecipient).toBeInstanceOf(Map);
    expect(result.current.byRecipient.size).toBe(2);
    expect(result.current.byRecipient.get(FIRST_RECIPIENT)).toMatchObject({ id: first.id, recipientId: FIRST_RECIPIENT });
    expect(result.current.byRecipient.get(SECOND_RECIPIENT)).toMatchObject({ id: second.id, recipientId: SECOND_RECIPIENT });
    expect(fetcher).toHaveBeenCalledTimes(1);
    const url = requestUrl(fetcher.mock.calls[0]);
    expect(url.pathname).toBe('/api/notes');
    expect(url.searchParams.get('from')).toBe(DATE);
    expect(url.searchParams.get('to')).toBe(DATE);
    expect(url.searchParams.get('page')).toBe('0');
    expect(url.searchParams.get('size')).toBe('100');
  });

  it('reads and merges every history page', async () => {
    const first = noteFor('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', FIRST_RECIPIENT);
    const second = noteFor('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', SECOND_RECIPIENT);
    const fetcher = vi.fn().mockImplementation((path: string) => {
      const page = new URL(path, 'http://localhost').searchParams.get('page');
      return Promise.resolve(jsonResponse(historyPage(page === '1' ? [second] : [first], Number(page), 100, 101)));
    });
    const { result } = renderTodayNotes(fetcher);

    await waitFor(() => expect(result.current.state).toBe('ready'));
    expect(result.current.byRecipient.size).toBe(2);
    expect(result.current.byRecipient.get(FIRST_RECIPIENT)?.id).toBe(first.id);
    expect(result.current.byRecipient.get(SECOND_RECIPIENT)?.id).toBe(second.id);
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(fetcher.mock.calls.map((call) => requestUrl(call).searchParams.get('page'))).toEqual(['0', '1']);
    expect(fetcher.mock.calls.every((call) => {
      const url = requestUrl(call);
      return url.searchParams.get('from') === DATE && url.searchParams.get('to') === DATE && url.searchParams.get('size') === '100';
    })).toBe(true);
  });

  it('reports an error when the history request fails', async () => {
    const fetcher = vi.fn().mockImplementation(() => Promise.resolve(jsonResponse({ message: 'unavailable' }, 500)));
    const { result } = renderTodayNotes(fetcher);

    await waitFor(() => expect(result.current.state).toBe('error'));
    expect(result.current.byRecipient.size).toBe(0);
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it('aborts the in-flight request on unmount', async () => {
    let requestSignal: AbortSignal | null | undefined;
    const fetcher = vi.fn().mockImplementation((_path: string, init?: RequestInit) => {
      requestSignal = init?.signal;
      return new Promise<Response>(() => {});
    });
    const { unmount } = renderTodayNotes(fetcher);

    await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1));
    unmount();
    expect(requestSignal?.aborted).toBe(true);
  });

  it('reloads from the first page', async () => {
    const first = noteFor('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', FIRST_RECIPIENT);
    const second = noteFor('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', SECOND_RECIPIENT);
    let calls = 0;
    const fetcher = vi.fn().mockImplementation(() => {
      calls += 1;
      return Promise.resolve(jsonResponse(historyPage([calls === 1 ? first : second], 0, 100, 1)));
    });
    const { result } = renderTodayNotes(fetcher);

    await waitFor(() => expect(result.current.state).toBe('ready'));
    expect(result.current.byRecipient.get(FIRST_RECIPIENT)?.id).toBe(first.id);
    act(() => result.current.reload());
    await waitFor(() => expect(result.current.byRecipient.get(SECOND_RECIPIENT)?.id).toBe(second.id));
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(fetcher.mock.calls.map((call) => requestUrl(call).searchParams.get('page'))).toEqual(['0', '0']);
  });
});
