import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../app/App';
import { AuthProvider } from '../features/auth/AuthProvider';
import { SESSION_STORAGE_KEY } from '../features/auth/session';
import { I18nProvider } from '../i18n/I18nProvider';
import { ApiProvider } from '../lib/api';
import { jsonResponse, testToken } from './authFixtures';
import { recipientFixture } from './recipientFixtures';

function selectedText(): string {
  const select = document.getElementById('app-recipient') as HTMLSelectElement | null;
  return select?.selectedOptions[0]?.textContent ?? '';
}

function renderShell(path: string) {
  const fetcher = vi.fn().mockImplementation((input: RequestInfo | URL) => {
    const url = String(input);
    if (url === '/api/recipients') return Promise.resolve(jsonResponse([recipientFixture]));
    if (url.startsWith('/api/recipients/')) return Promise.resolve(jsonResponse(recipientFixture));
    if (url.startsWith('/api/notes?')) return Promise.resolve(jsonResponse({
      content: [], number: 0, size: 100, totalElements: 0, totalPages: 0, first: true, last: true,
    }));
    return Promise.resolve(jsonResponse([]));
  });
  vi.stubGlobal('fetch', fetcher);
  localStorage.setItem('caregiver-language', 'en');
  sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ token: testToken(), baseUrl: '' }));
  render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider><MemoryRouter initialEntries={[path]}><App /></MemoryRouter></AuthProvider></ApiProvider></I18nProvider>);
  return fetcher;
}

function recipientListCalls(fetcher: ReturnType<typeof vi.fn>) {
  return fetcher.mock.calls.filter(([input]) => String(input) === '/api/recipients');
}

describe('top bar selected recipient (issue #712)', () => {
  it('shows the route recipient name without any focus event', async () => {
    const fetcher = renderShell(`/app/recipients/${recipientFixture.id}`);

    await waitFor(() => expect(selectedText()).toBe(recipientFixture.name));
    expect(document.activeElement?.id).not.toBe('app-recipient');
    expect(recipientListCalls(fetcher)).toHaveLength(1);
  });

  it('shows the query-string recipient name without any focus event', async () => {
    const fetcher = renderShell(`/app/notes?recipientId=${recipientFixture.id}`);

    await waitFor(() => expect(selectedText()).toBe(recipientFixture.name));
    expect(recipientListCalls(fetcher)).toHaveLength(1);
  });

  it('still marks a genuinely missing selected recipient unavailable', async () => {
    const fetcher = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (url === '/api/recipients') return Promise.resolve(jsonResponse([]));
      return Promise.resolve(jsonResponse([]));
    });
    vi.stubGlobal('fetch', fetcher);
    localStorage.setItem('caregiver-language', 'en');
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ token: testToken(), baseUrl: '' }));
    render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider><MemoryRouter initialEntries={[`/app/recipients/${recipientFixture.id}`]}><App /></MemoryRouter></AuthProvider></ApiProvider></I18nProvider>);

    expect(await screen.findByRole('option', { name: 'Selected recipient unavailable' })).toBeInTheDocument();
    await waitFor(() => expect(selectedText()).toBe('Selected recipient unavailable'));
  });

  it('shows the placeholder when nothing is selected', async () => {
    renderShell('/app/recipients');

    expect(await screen.findByRole('combobox', { name: 'Recipient' })).toBeInTheDocument();
    expect(selectedText()).toBe('Choose a recipient');
  });
});
