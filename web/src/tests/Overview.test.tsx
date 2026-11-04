import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../app/App';
import { AuthProvider } from '../features/auth/AuthProvider';
import { I18nProvider } from '../i18n/I18nProvider';
import { ApiProvider } from '../lib/api';
import { jsonResponse, testToken } from './authFixtures';
import { recipientFixture } from './recipientFixtures';
import { noteFixture } from './noteFixtures';

const inactive = { ...recipientFixture, id: 'b2222222-2222-4222-8222-222222222222', name: 'Mona', active: false, lifecycleStatus: 'ARCHIVED' as const, archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
const missing = { ...recipientFixture, id: 'c3333333-3333-4333-8333-333333333333', name: 'No note recipient' };
function renderOverview(path: string, fetcher: ReturnType<typeof vi.fn>, language = 'en') {
  vi.stubGlobal('fetch', fetcher);
  localStorage.setItem('caregiver-language', language);
  sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider><MemoryRouter initialEntries={[path]}><App /></MemoryRouter></AuthProvider></ApiProvider></I18nProvider>);
}

function historyResponse(content: unknown[], number = 0, totalElements = content.length, totalPages = content.length ? 1 : 0) {
  return jsonResponse({ content, number, size: 100, totalElements, totalPages, first: number === 0, last: number >= totalPages - 1 });
}

function serving() {
  return vi.fn().mockImplementation((path: string) => {
    if (path === '/api/recipients') return Promise.resolve(jsonResponse([recipientFixture, inactive, missing]));
    if (path.startsWith('/api/notes?')) {
      const date = new URL(path, 'http://localhost').searchParams.get('from');
      return Promise.resolve(historyResponse(date === noteFixture.date ? [noteFixture] : []));
    }
    return Promise.resolve(jsonResponse({}, 404));
  });
}

describe('authenticated workspace overview', () => {
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
  it('loads every page before treating a recipient as having no note', async () => {
    const laterPageNote = { ...noteFixture, id: 'e5555555-5555-4555-8555-555555555555', recipientId: missing.id };
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
