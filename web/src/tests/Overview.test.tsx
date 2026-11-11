import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../app/App';
import { AuthProvider } from '../features/auth/AuthProvider';
import { I18nProvider } from '../i18n/I18nProvider';
import { ApiProvider } from '../lib/api';
import { jsonResponse, testToken } from './authFixtures';
import { recipientFixture } from './recipientFixtures';
import { noteFixture, noteListFixture } from './noteFixtures';

const inactive = { ...recipientFixture, id: 'b2222222-2222-4222-8222-222222222222', name: 'Mona', active: false, lifecycleStatus: 'ARCHIVED' as const, archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
const legacyInactive = { ...recipientFixture, id: 'd4444444-4444-4444-8444-444444444444', name: 'Legacy recipient', active: false, lifecycleStatus: 'LEGACY_INACTIVE_REVIEW' as const };
const missing = { ...recipientFixture, id: 'c3333333-3333-4333-8333-333333333333', name: 'No note recipient' };
function renderOverview(path: string, fetcher: ReturnType<typeof vi.fn>, language = 'en') {
  vi.stubGlobal('fetch', fetcher);
  localStorage.setItem('caregiver-language', language);
  sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider><MemoryRouter initialEntries={[path]}><Location /><App /></MemoryRouter></AuthProvider></ApiProvider></I18nProvider>);
}

function Location() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.search}</output>;
}

function historyResponse(content: unknown[], number = 0, totalElements = content.length, totalPages = content.length ? 1 : 0) {
  return jsonResponse({ content, number, size: 100, totalElements, totalPages, first: number === 0, last: number >= totalPages - 1 });
}

function serving() {
  return vi.fn().mockImplementation((path: string) => {
    if (path === '/api/recipients') return Promise.resolve(jsonResponse([recipientFixture, inactive, missing]));
    if (path.startsWith('/api/notes?')) {
      const date = new URL(path, 'http://localhost').searchParams.get('from');
      return Promise.resolve(historyResponse(date === noteListFixture.date ? [noteListFixture] : []));
    }
    return Promise.resolve(jsonResponse({}, 404));
  });
}

describe('authenticated workspace overview', () => {
  it('defaults to the caregiver local calendar date', async () => {
    vi.stubEnv('TZ', 'America/Los_Angeles');
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-19T01:00:00Z'));
    try {
      const fetcher = vi.fn().mockImplementation((path: string) => path === '/api/recipients'
        ? Promise.resolve(jsonResponse([recipientFixture]))
        : Promise.resolve(historyResponse([])));
      renderOverview('/app', fetcher);
      expect(await screen.findByLabelText('Calendar date')).toHaveValue('2026-10-18');
      expect(fetcher.mock.calls.some(([path]) => path === '/api/notes?from=2026-10-18&to=2026-10-18&page=0&size=100')).toBe(true);
    } finally {
      vi.unstubAllEnvs();
    }
  });

  it('renders recipient summary cards with semantic headings and status-specific actions', async () => {
    const fetcher = serving();
    renderOverview('/app?date=2026-10-18', fetcher);

    expect(await screen.findByRole('heading', { name: recipientFixture.name, level: 3 })).toBeInTheDocument();
    const recordedCard = screen.getByRole('heading', { name: recipientFixture.name, level: 3 }).closest('li');
    const missingCard = screen.getByRole('heading', { name: missing.name, level: 3 }).closest('li');
    expect(recordedCard).toHaveTextContent('Note recorded');
    expect(recordedCard?.querySelector(`a[href="/app/notes/${noteFixture.id}"]`)).not.toBeNull();
    expect(missingCard).toHaveTextContent('No note recorded');
    expect(missingCard?.querySelector('a[href^="/app/notes/new"]')).not.toBeNull();
    expect(missingCard).not.toHaveTextContent('Note status unavailable');
    expect(fetcher).toHaveBeenCalled();
  });

  it('shows factual per-date note status and links to source and relevant workflows', async () => {
    const fetcher = serving();
    renderOverview('/app?date=2026-10-18', fetcher);
    expect(await screen.findByRole('link', { name: 'Open note' })).toHaveAttribute('href', `/app/notes/${noteFixture.id}`);
    expect(screen.getByLabelText('Calendar date')).toHaveValue('2026-10-18');
    expect(screen.getAllByText('No note recorded')).toHaveLength(2);
    expect(screen.getByText(/A missing note means no note was found/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Record a note' })).toHaveAttribute('href', `/app/notes/new?recipientId=${missing.id}&date=2026-10-18`);
    const inactiveRow = screen.getByRole('link', { name: inactive.name }).closest('li');
    expect(inactiveRow).toHaveTextContent('Inactive');
    expect(inactiveRow?.querySelector('a[href^="/app/notes/new"]')).toBeNull();
    expect(fetcher.mock.calls.some(([path]) => path === '/api/notes?from=2026-10-18&to=2026-10-18&page=0&size=100')).toBe(true);
  });

  it('does not offer note creation for an archived recipient', async () => {
    const fetcher = vi.fn().mockImplementation((path: string) => path === '/api/recipients'
      ? Promise.resolve(jsonResponse([inactive]))
      : Promise.resolve(historyResponse([])));
    renderOverview('/app?date=2026-10-18', fetcher);

    const row = (await screen.findByRole('link', { name: inactive.name })).closest('li');
    expect(row).toHaveTextContent(/Inactive/);
    expect(row).toHaveTextContent('No note recorded');
    expect(row?.querySelector('a[href^="/app/notes/new"]')).toBeNull();
    expect(row?.querySelector(`a[href="/app/recipients/${inactive.id}"]`)).not.toBeNull();
  });

  it('keeps legacy-inactive recipients visible without offering note creation', async () => {
    const fetcher = vi.fn().mockImplementation((path: string) => path === '/api/recipients'
      ? Promise.resolve(jsonResponse([legacyInactive]))
      : Promise.resolve(historyResponse([])));
    renderOverview('/app?date=2026-10-18', fetcher);

    const row = (await screen.findByRole('link', { name: legacyInactive.name })).closest('li');
    expect(row).toHaveTextContent(/Inactive/);
    expect(row).toHaveTextContent('No note recorded');
    expect(row?.querySelector('a[href^="/app/notes/new"]')).toBeNull();
  });

  it('keeps recipient-list failures distinct from note-history failures', async () => {
    const fetcher = vi.fn().mockResolvedValue(jsonResponse({ message: 'private server diagnostics' }, 500));
    renderOverview('/app?date=2026-10-18', fetcher);

    expect(await screen.findByText('We couldn’t complete this request. Please try again.')).toBeInTheDocument();
    expect(screen.queryByText('Note status unavailable')).not.toBeInTheDocument();
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(String(fetcher.mock.calls[0][0])).toBe('/api/recipients');
  });

  it('does not turn a note request failure into a missing observation', async () => {
    let failed = true;
    const fetcher = vi.fn().mockImplementation((path: string) => {
      if (path === '/api/recipients') return Promise.resolve(jsonResponse([recipientFixture]));
      return Promise.resolve(failed ? jsonResponse({ message: 'private server diagnostics' }, 500) : historyResponse([]));
    });
    renderOverview('/app?date=2026-10-18', fetcher);

    const row = await screen.findByRole('link', { name: recipientFixture.name }).then((link) => link.closest('li'));
    expect(await screen.findByText('Note status unavailable')).toBeInTheDocument();
    expect(row).not.toHaveTextContent('No note recorded');
    expect(screen.queryByText('private server diagnostics')).not.toBeInTheDocument();
    failed = false;
    await userEvent.click(row!.querySelector('button')!);
    expect(await screen.findByText('No note recorded', { exact: true })).toBeInTheDocument();
    expect(screen.queryByText('Note status unavailable', { exact: true })).not.toBeInTheDocument();
  });

  it('preserves recorded rows when a later history page fails', async () => {
    const fetcher = vi.fn().mockImplementation((path: string) => {
      if (path === '/api/recipients') return Promise.resolve(jsonResponse([recipientFixture, missing]));
      if (path.includes('page=0')) return Promise.resolve(historyResponse([noteListFixture], 0, 101, 2));
      return Promise.resolve(jsonResponse({ message: 'private server diagnostics' }, 500));
    });
    renderOverview('/app?date=2026-10-18', fetcher);

    expect(await screen.findByRole('link', { name: 'Open note' })).toHaveAttribute('href', `/app/notes/${noteFixture.id}`);
    const missingRow = screen.getByRole('link', { name: missing.name }).closest('li');
    expect(await screen.findByText('Note status unavailable')).toBeInTheDocument();
    expect(missingRow).not.toHaveTextContent('No note recorded');
    expect(screen.getByText('Some note statuses could not be loaded. Retry those rows to refresh the overview.')).toBeInTheDocument();
  });

  it('keeps selected date in the URL', async () => {
    const fetcher = serving();
    renderOverview('/app?date=2026-10-18', fetcher);
    await screen.findByLabelText('Calendar date');
    fireEvent.change(screen.getByLabelText('Calendar date'), { target: { value: '2026-10-19' } });

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/app?date=2026-10-19'));
    expect(screen.getByLabelText('Calendar date')).toHaveValue('2026-10-19');
    await waitFor(() => expect(fetcher.mock.calls.some(([path]) => path === '/api/notes?from=2026-10-19&to=2026-10-19&page=0&size=100')).toBe(true));
  });

  it('cancels the previous date request and ignores its late result', async () => {
    const pending = new Map<string, { signal: AbortSignal | undefined; resolve: (response: Response) => void }>();
    const fetcher = vi.fn().mockImplementation((path: string, init?: RequestInit) => {
      if (path === '/api/recipients') return Promise.resolve(jsonResponse([recipientFixture]));
      const date = new URL(path, 'http://localhost').searchParams.get('from')!;
      return new Promise<Response>((resolve) => pending.set(date, { signal: init?.signal ?? undefined, resolve }));
    });
    const view = renderOverview('/app?date=2026-10-18', fetcher);

    await waitFor(() => expect(pending.has('2026-10-18')).toBe(true));
    fireEvent.change(screen.getByLabelText('Calendar date'), { target: { value: '2026-10-19' } });
    await waitFor(() => expect(pending.has('2026-10-19')).toBe(true));
    expect(pending.get('2026-10-18')?.signal?.aborted).toBe(true);

    pending.get('2026-10-18')?.resolve(historyResponse([noteListFixture]));
    pending.get('2026-10-19')?.resolve(historyResponse([]));
    expect(await screen.findByText('No note recorded')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Open note' })).not.toBeInTheDocument();

    view.unmount();
    expect(pending.get('2026-10-19')?.signal?.aborted).toBe(true);
  });

  it('aborts an in-flight request on unmount and ignores its late result', async () => {
    let resolveHistory: ((response: Response) => void) | undefined;
    let historySignal: AbortSignal | undefined;
    const fetcher = vi.fn().mockImplementation((path: string, init?: RequestInit) => {
      if (path === '/api/recipients') return Promise.resolve(jsonResponse([recipientFixture]));
      historySignal = init?.signal ?? undefined;
      return new Promise<Response>((resolve) => { resolveHistory = resolve; });
    });
    const view = renderOverview('/app?date=2026-10-18', fetcher);

    await waitFor(() => expect(resolveHistory).toBeTypeOf('function'));
    view.unmount();
    expect(historySignal?.aborted).toBe(true);

    await act(async () => {
      resolveHistory?.(historyResponse([noteListFixture]));
      await Promise.resolve();
    });
    expect(view.container).toBeEmptyDOMElement();
  });

  it('does not send an invalid URL date to the API', async () => {
    const fetcher = serving();
    renderOverview('/app?date=2026-02-31', fetcher);
    const input = await screen.findByLabelText('Calendar date');

    expect(input).not.toHaveValue('2026-02-31');
    const historyUrl = fetcher.mock.calls.map(([path]) => path).find((path: string) => path.startsWith('/api/notes?'));
    expect(historyUrl).toBeDefined();
    expect(new URL(historyUrl!, 'http://localhost').searchParams.get('from')).toBe((input as HTMLInputElement).value);
  });

  it('does not mark a recipient recorded for another recipient or date', async () => {
    const otherRecipientNote = { ...noteListFixture, recipientId: missing.id };
    const fetcher = vi.fn().mockImplementation((path: string) => path === '/api/recipients'
      ? Promise.resolve(jsonResponse([recipientFixture, missing]))
      : Promise.resolve(historyResponse([otherRecipientNote])));
    const view = renderOverview('/app?date=2026-10-18', fetcher);
    const recipientRow = await screen.findByRole('link', { name: recipientFixture.name }).then((link) => link.closest('li'));
    const otherRow = screen.getByRole('link', { name: missing.name }).closest('li');
    expect(recipientRow).toHaveTextContent('No note recorded');
    expect(recipientRow?.querySelector(`a[href="/app/notes/${noteFixture.id}"]`)).toBeNull();
    expect(otherRow).toHaveTextContent('Note recorded');
    view.unmount();

    const wrongDateFetcher = vi.fn().mockImplementation((path: string) => path === '/api/recipients'
      ? Promise.resolve(jsonResponse([recipientFixture]))
      : Promise.resolve(historyResponse([{ ...noteListFixture, date: '2026-10-17' }])));
    renderOverview('/app?date=2026-10-18', wrongDateFetcher);
    const wrongDateRow = await screen.findByRole('link', { name: recipientFixture.name }).then((link) => link.closest('li'));
    expect(wrongDateRow).not.toHaveTextContent('Note recorded');
    expect(wrongDateRow).not.toHaveTextContent('No note recorded');
    expect(wrongDateRow).toHaveTextContent('Note status unavailable');
  });

  it('loads every page before treating a recipient as having no note', async () => {
    const laterPageNote = { ...noteListFixture, id: 'e5555555-5555-4555-8555-555555555555', recipientId: missing.id };
    const fetcher = vi.fn().mockImplementation((path: string) => {
      if (path === '/api/recipients') return Promise.resolve(jsonResponse([recipientFixture, missing]));
      if (path.startsWith('/api/notes?')) {
        const page = Number(new URL(path, 'http://localhost').searchParams.get('page'));
        return Promise.resolve(page === 0
          ? historyResponse([], 0, 101, 2)
          : historyResponse([laterPageNote], 1, 101, 2));
      }
      return Promise.resolve(jsonResponse({}, 404));
    });
    renderOverview('/app?date=2026-10-18', fetcher);
    expect(await screen.findByRole('link', { name: 'Open note' })).toHaveAttribute('href', `/app/notes/${laterPageNote.id}`);
    expect(fetcher.mock.calls.some(([path]) => path.includes('page=1&size=100'))).toBe(true);
    expect(screen.getAllByText('No note recorded')).toHaveLength(1);
  });


  it('updates date in URL state and reports a partial per-recipient load failure', async () => {
    const fetcher = serving();
    fetcher.mockImplementation((path: string) => {
      if (path === '/api/recipients') return Promise.resolve(jsonResponse([recipientFixture, missing]));
      if (path.startsWith('/api/notes?')) return Promise.reject(new TypeError('network failure'));
    });
    const view = renderOverview('/app?date=2026-10-18', fetcher);
    expect(await screen.findAllByText('Note status unavailable')).toHaveLength(2);
    expect(await screen.findByText('Some note statuses could not be loaded. Retry those rows to refresh the overview.')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Calendar date'), { target: { value: '2026-10-19' } });
    expect(view.container.querySelector('h1')).toHaveTextContent('Caregiver workspace');
    expect(await screen.findByLabelText('Calendar date')).toHaveValue('2026-10-19');
    await waitFor(() => expect(fetcher.mock.calls.some(([path]) => path.includes('from=2026-10-19&to=2026-10-19'))).toBe(true));
  });

  it('offers recipient creation from the factual empty state', async () => {
    const fetcher = vi.fn().mockResolvedValue(jsonResponse([]));
    renderOverview('/app', fetcher);
    await userEvent.click(await screen.findByRole('link', { name: 'Add care recipient' }));
    expect(screen.getByRole('form', { name: 'Add care recipient' })).toBeInTheDocument();
  });
});
