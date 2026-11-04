import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../app/App';
import { AuthProvider } from '../features/auth/AuthProvider';
import { defaultHistoryFilters } from '../features/history/historyFilters';
import { I18nProvider } from '../i18n/I18nProvider';
import { ApiProvider } from '../lib/api';
import { jsonResponse, testToken } from './authFixtures';
import { correctionHistoryFixture, historyNote, historyPage } from './historyFixtures';
import { addendumFixture, noteFixture, noteObservations } from './noteFixtures';
import { recipientFixture } from './recipientFixtures';

function forceTimelineOverflow(body: HTMLElement) {
  Object.defineProperty(body, 'scrollHeight', { configurable: true, value: 600 });
  fireEvent(window, new Event('resize'));
}

const root = '/app/notes';
const range = `${root}?from=2026-10-01&to=2026-10-22`;
const otherRecipient = { ...recipientFixture, id: 'b2222222-2222-4222-8222-222222222222', name: 'ليلى Smith', active: false, lifecycleStatus: 'ARCHIVED', archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
function Location() { const location = useLocation(); return <output data-testid="location">{location.pathname}{location.search}</output>; }
function HistoryNavigationControls() {
  const navigate = useNavigate();
  return <div><button onClick={() => navigate(-1)}>Test Back</button><button onClick={() => navigate(1)}>Test Forward</button></div>;
}
function renderHistory(fetcher: ReturnType<typeof vi.fn>, path = range, language = 'en', authenticated = true, navigationControls = false) {
  vi.stubGlobal('fetch', fetcher);
  localStorage.setItem('caregiver-language', language);
  if (authenticated) sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider><MemoryRouter initialEntries={[path]}>{navigationControls && <HistoryNavigationControls />}<App /><Location /></MemoryRouter></AuthProvider></ApiProvider></I18nProvider>);
}
function dataFetcher(override?: (url: URL, init: RequestInit) => Response | Promise<Response> | undefined) {
  return vi.fn().mockImplementation((path: string, init: RequestInit) => {
    const url = new URL(path, 'http://localhost');
    return Promise.resolve(override?.(url, init) ?? jsonResponse(url.pathname === '/api/recipients' ? [recipientFixture, otherRecipient]
      : url.pathname.endsWith('/addenda') ? [addendumFixture]
        : url.pathname.endsWith('/corrections') ? correctionHistoryFixture() : historyPage()));
  });
}
const queries = (fetcher: ReturnType<typeof vi.fn>) => fetcher.mock.calls.map(([path]) => new URL(path, 'http://localhost')).filter((url) => url.pathname === '/api/notes');
async function loaded() { return screen.findByRole('article', { name: recipientFixture.name }); }
function date(label: string, value: string) { fireEvent.change(screen.getByLabelText(label), { target: { value } }); }

describe('history screen, filters and immutable records', () => {
  beforeEach(() => {
    // Freeze calendar time only; async UI and request timers still run normally.
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-27T12:00:00Z'));
  });
  it('protects history and its alias before making API requests', () => {
    const fetcher = vi.fn();
    renderHistory(fetcher, `${root}/history`, 'en', false);
    expect(screen.getByRole('heading', { name: 'Caregiver sign in' })).toBeInTheDocument();
    expect(fetcher).not.toHaveBeenCalled();
  });
  it('announces loading, then shows structured values and verbatim mixed text without fetching addenda', async () => {
    let resolve!: (value: Response) => void;
    const fetcher = dataFetcher((url) => url.pathname === '/api/notes' ? new Promise<Response>((done) => { resolve = done; }) : undefined);
    renderHistory(fetcher);
    expect(screen.getByText('Loading notes history…')).toBeInTheDocument();
    resolve(jsonResponse(historyPage()));
    const card = await loaded();
    expect(document.title).toContain('Notes history');
    expect(within(card).getByText('Original note')).toBeInTheDocument();
    await userEvent.click(within(card).getByText('View original observations and text'));
    expect(within(card).getByText('Unknown')).toBeInTheDocument();
    expect(within(card).getByText('0')).toBeInTheDocument();
    expect(within(card).getByText('No')).toBeInTheDocument();
    expect(within(card).getByText(/غير متأكد من الدواء/).textContent).toBe(noteObservations.freeText);
    expect(document.querySelector('script')).not.toBeInTheDocument();
    expect(fetcher.mock.calls.some(([path]) => path.includes('/addenda'))).toBe(false);
  });
  it('applies recipient/date filters together only on submission, including inactive recipients', async () => {
    const fetcher = dataFetcher((url) => url.searchParams.has('recipientId') ? jsonResponse(historyPage([{ ...historyNote, recipientId: otherRecipient.id }])) : undefined);
    renderHistory(fetcher);
    await loaded();
    await userEvent.selectOptions(screen.getByLabelText('Care recipient'), otherRecipient.id);
    date('From date', '2026-10-10'); date('To date', '2026-10-20');
    expect(queries(fetcher)).toHaveLength(1);
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    expect(await screen.findByRole('article', { name: otherRecipient.name })).toBeInTheDocument();
    expect(Object.fromEntries(queries(fetcher).at(-1)!.searchParams)).toEqual({ from: '2026-10-10', to: '2026-10-20', recipientId: otherRecipient.id, page: '0', size: '20' });
    expect(screen.getByRole('heading', { name: 'History results' })).toHaveFocus();
    expect(screen.getByTestId('location')).toHaveTextContent(`recipientId=${otherRecipient.id}`);
  });
  it('uses identical inclusive dates for a single day and supports date-only filtering', async () => {
    const fetcher = dataFetcher(); renderHistory(fetcher); await loaded();
    await userEvent.click(screen.getByRole('radio', { name: 'Single date' }));
    date('Date', noteFixture.date);
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    await loaded();
    expect(Object.fromEntries(queries(fetcher).at(-1)!.searchParams)).toEqual({ from: noteFixture.date, to: noteFixture.date, page: '0', size: '20' });
    expect(screen.queryByLabelText('To date')).not.toBeInTheDocument();
  });
  it('resets applied filters, page size, page and unsubmitted edits to the inclusive default range', async () => {
    const fetcher = dataFetcher((url) => url.pathname === '/api/notes' ? jsonResponse(historyPage([historyNote], Number(url.searchParams.get('page')), Number(url.searchParams.get('size')), 101)) : undefined);
    const defaults = defaultHistoryFilters();
    renderHistory(fetcher, `${range}&recipientId=${recipientFixture.id}&page=1&size=50`); await loaded();
    date('From date', '2026-01-01');
    await userEvent.click(screen.getByRole('button', { name: 'Reset filters' }));
    await loaded();
    expect(screen.getByLabelText('Care recipient')).toHaveValue('');
    expect(screen.getByLabelText('From date')).toHaveValue(defaults.from);
    expect(screen.getByLabelText('To date')).toHaveValue(defaults.to);
    expect(screen.getByLabelText('Notes per page')).toHaveValue('20');
    expect(Object.fromEntries(new URLSearchParams(screen.getByTestId('location').textContent!.split('?')[1]))).toEqual({
      from: defaults.from, to: defaults.to,
    });
    expect(queries(fetcher).at(-1)!.searchParams.get('page')).toBe('0');
    expect(queries(fetcher).at(-1)!.searchParams.get('size')).toBe('20');
    expect(queries(fetcher).at(-1)!.searchParams.has('recipientId')).toBe(false);
    date('From date', '2026-01-01');
    await userEvent.click(screen.getByRole('button', { name: 'Reset filters' }));
    expect(screen.getByLabelText('From date')).toHaveValue(defaults.from);
  });
  it('validates empty/reversed dates, focuses the field and retains the current results without a request', async () => {
    const fetcher = dataFetcher(); renderHistory(fetcher); await loaded();
    date('From date', '');
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    expect(screen.getByLabelText('From date')).toHaveFocus();
    expect(screen.getByLabelText('From date')).toHaveAttribute('aria-invalid', 'true');
    date('From date', '2026-11-01');
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    expect(screen.getByLabelText('To date')).toHaveFocus();
    expect(screen.getByRole('alert')).toHaveTextContent('end date');
    expect(queries(fetcher)).toHaveLength(1);
  });
  it('restores filters/pagination from the URL and resets the page after changing size or filters', async () => {
    const fetcher = dataFetcher((url) => url.pathname === '/api/notes' ? jsonResponse(historyPage([historyNote], Number(url.searchParams.get('page')), Number(url.searchParams.get('size')), 61)) : undefined);
    renderHistory(fetcher, `${range}&page=2&recipientId=${recipientFixture.id}`); await loaded();
    expect(screen.getByLabelText('Care recipient')).toHaveValue(recipientFixture.id);
    expect(screen.getByText('Page 3 of 4')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Previous page' })); await loaded();
    expect(queries(fetcher).at(-1)!.searchParams.get('page')).toBe('1');
    await userEvent.click(screen.getByRole('button', { name: 'Next page' })); await loaded();
    expect(queries(fetcher).at(-1)!.searchParams.get('page')).toBe('2');
    await userEvent.selectOptions(screen.getByLabelText('Notes per page'), '50'); await loaded();
    expect(queries(fetcher).at(-1)!.searchParams.get('page')).toBe('0');
    expect(queries(fetcher).at(-1)!.searchParams.get('size')).toBe('50');
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Next page' })); await loaded();
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled();
    date('From date', '2026-10-02');
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' })); await loaded();
    expect(queries(fetcher).at(-1)!.searchParams.get('page')).toBe('0');
  });
  it('restores recipient and date filters after refresh', async () => {
    const path = `${root}?from=2026-10-01&to=2026-10-22&recipientId=${recipientFixture.id}&page=1&size=50`;
    const fetcher = dataFetcher((url) => url.pathname === '/api/notes'
      ? jsonResponse(historyPage([historyNote], Number(url.searchParams.get('page')), Number(url.searchParams.get('size')), 101))
      : undefined);
    const first = renderHistory(fetcher, path);
    await loaded();
    expect(screen.getByLabelText('From date')).toHaveValue('2026-10-01');
    expect(screen.getByLabelText('To date')).toHaveValue('2026-10-22');
    expect(screen.getByLabelText('Care recipient')).toHaveValue(recipientFixture.id);
    expect(screen.getByLabelText('Notes per page')).toHaveValue('50');
    expect(Object.fromEntries(queries(fetcher).at(-1)!.searchParams)).toEqual({
      from: '2026-10-01', to: '2026-10-22', recipientId: recipientFixture.id, page: '1', size: '50',
    });
    first.unmount();

    renderHistory(fetcher, path);
    await loaded();
    expect(screen.getByLabelText('From date')).toHaveValue('2026-10-01');
    expect(screen.getByLabelText('To date')).toHaveValue('2026-10-22');
    expect(screen.getByLabelText('Care recipient')).toHaveValue(recipientFixture.id);
    expect(screen.getByLabelText('Notes per page')).toHaveValue('50');
    expect(Object.fromEntries(queries(fetcher).at(-1)!.searchParams)).toEqual({
      from: '2026-10-01', to: '2026-10-22', recipientId: recipientFixture.id, page: '1', size: '50',
    });
  });
  it('does not normalize invalid pagination parameters into a history request', async () => {
    const fetcher = dataFetcher();
    renderHistory(fetcher, `${root}?from=2026-10-01&to=2026-10-22&page=invalid&size=30`);
    expect(await screen.findByText('Check the filters before loading notes.')).toBeInTheDocument();
    expect(screen.getByLabelText('Notes per page')).toHaveValue('20');
    expect(queries(fetcher)).toHaveLength(0);
  });
  it('restores prior filter state with browser back', async () => {
    const fetcher = dataFetcher((url) => url.pathname === '/api/notes'
      ? jsonResponse(historyPage([], Number(url.searchParams.get('page')), Number(url.searchParams.get('size'))))
      : undefined);
    renderHistory(fetcher, root, 'en', true, true);

    await screen.findByText('No notes match these filters.');
    await userEvent.selectOptions(screen.getByLabelText('Care recipient'), otherRecipient.id);
    date('From date', '2026-10-10');
    date('To date', '2026-10-20');
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    await screen.findByText('No notes match these filters.');
    await waitFor(() => expect(Object.fromEntries(new URLSearchParams(screen.getByTestId('location').textContent!.split('?')[1]))).toMatchObject({
      from: '2026-10-10', to: '2026-10-20', recipientId: otherRecipient.id,
    }));
    expect(screen.getByLabelText('Care recipient')).toHaveValue(otherRecipient.id);
    expect(Object.fromEntries(queries(fetcher).at(-1)!.searchParams)).toMatchObject({
      from: '2026-10-10', to: '2026-10-20', recipientId: otherRecipient.id,
    });

    await userEvent.selectOptions(screen.getByLabelText('Care recipient'), recipientFixture.id);
    date('From date', '2026-10-11');
    date('To date', '2026-10-21');
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' }));
    await screen.findByText('No notes match these filters.');
    await waitFor(() => expect(screen.getByLabelText('From date')).toHaveValue('2026-10-11'));
    expect(screen.getByLabelText('Care recipient')).toHaveValue(recipientFixture.id);
    expect(Object.fromEntries(queries(fetcher).at(-1)!.searchParams)).toMatchObject({
      from: '2026-10-11', to: '2026-10-21', recipientId: recipientFixture.id,
    });

    await userEvent.click(screen.getByRole('button', { name: 'Test Back' }));
    await waitFor(() => {
      expect(screen.getByLabelText('From date')).toHaveValue('2026-10-10');
      expect(screen.getByLabelText('To date')).toHaveValue('2026-10-20');
      expect(screen.getByLabelText('Care recipient')).toHaveValue(otherRecipient.id);
    });
    await waitFor(() => expect(Object.fromEntries(queries(fetcher).at(-1)!.searchParams)).toMatchObject({
      from: '2026-10-10', to: '2026-10-20', recipientId: otherRecipient.id,
    }));

    await userEvent.click(screen.getByRole('button', { name: 'Test Forward' }));
    await waitFor(() => {
      expect(screen.getByLabelText('From date')).toHaveValue('2026-10-11');
      expect(screen.getByLabelText('To date')).toHaveValue('2026-10-21');
      expect(screen.getByLabelText('Care recipient')).toHaveValue(recipientFixture.id);
    });
    await waitFor(() => expect(Object.fromEntries(queries(fetcher).at(-1)!.searchParams)).toMatchObject({
      from: '2026-10-11', to: '2026-10-21', recipientId: recipientFixture.id,
    }));
  });
  it('cancels a superseded request and ignores its late response', async () => {
    let resolve!: (value: Response) => void; let abandoned!: AbortSignal;
    const fetcher = dataFetcher((url, init) => url.pathname === '/api/notes' && !url.searchParams.has('recipientId')
      ? new Promise<Response>((done) => { resolve = done; abandoned = init.signal!; }) : undefined);
    renderHistory(fetcher);
    await waitFor(() => expect(screen.getByLabelText('Care recipient')).toBeEnabled());
    await userEvent.selectOptions(screen.getByLabelText('Care recipient'), recipientFixture.id);
    await userEvent.click(screen.getByRole('button', { name: 'Apply filters' })); await loaded();
    expect(abandoned.aborted).toBe(true);
    await act(async () => resolve(jsonResponse(historyPage([]))));
    expect(screen.getByRole('article', { name: recipientFixture.name })).toBeInTheDocument();
    expect(screen.queryByText('No notes match these filters.')).not.toBeInTheDocument();
  });
  it('distinguishes an empty result from a page that disappeared and allows return to page one', async () => {
    const fetcher = dataFetcher((url) => url.pathname === '/api/notes' ? jsonResponse(url.searchParams.get('page') === '2' ? historyPage([], 2, 20, 1) : historyPage([])) : undefined);
    renderHistory(fetcher, `${range}&page=2`);
    expect(await screen.findByText('This page no longer contains notes.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Go to first page' }));
    expect(await screen.findByText('No notes match these filters.')).toBeInTheDocument();
  });
  it('retries a failed history request without exposing backend diagnostics', async () => {
    let failed = true;
    const fetcher = dataFetcher((url) => url.pathname === '/api/notes' && failed ? jsonResponse({ message: 'private diagnostics' }, 500) : undefined);
    renderHistory(fetcher);
    expect(await screen.findByRole('alert')).not.toHaveTextContent('private diagnostics');
    failed = false;
    await userEvent.click(screen.getByRole('button', { name: 'Retry' })); await loaded();
  });
  it('loads/retries corrections under the correct original, rendering changed fields only', async () => {
    let failed = true;
    const fetcher = dataFetcher((url) => url.pathname.endsWith('/addenda') && failed ? jsonResponse({}, 500) : undefined);
    renderHistory(fetcher); const card = await loaded();
    await userEvent.click(within(card).getByRole('button', { name: 'Show corrections (1)' }));
    expect(within(card).getByRole('button', { name: 'Hide corrections (1)' })).toHaveAttribute('aria-expanded', 'true');
    expect(await within(card).findByRole('alert')).toBeInTheDocument();
    failed = false;
    await userEvent.click(within(card).getByRole('button', { name: 'Retry' }));
    const correction = await within(card).findByRole('article', { name: 'Correction 1' });
    expect(within(correction).getByText('Missed')).toBeInTheDocument();
    expect(within(correction).queryByText('Mood')).not.toBeInTheDocument();
    expect(within(correction).getByText(addendumFixture.freeText!)).toBeInTheDocument();
    expect(within(card).getByText(/غير متأكد من الدواء/).textContent).toBe(noteObservations.freeText);
    await userEvent.click(within(card).getByRole('button', { name: 'Hide corrections (1)' }));
    expect(screen.queryByRole('article', { name: 'Correction 1' })).not.toBeInTheDocument();
    expect(fetcher.mock.calls.every(([, init]) => init.method === 'GET')).toBe(true);
  });
  it('renders structured correction events in shared timeline rows with visible provenance', async () => {
    const history = correctionHistoryFixture();
    const actorId = 'f5555555-5555-4555-8555-555555555555';
    const eventId = 'e5555555-5555-4555-8555-555555555555';
    const previousEffective = {
      state: 'RECORDED', value: 'okay', rawValue: null, lastOperation: null,
      eventId: null, actorId: null, updatedAt: historyNote.createdAt,
    };
    const effectiveAfter = {
      ...previousEffective, value: 'calm', lastOperation: 'SET',
      eventId, actorId, updatedAt: '2026-10-18T12:00:00Z',
    };
    const event = {
      id: eventId, noteId: historyNote.id, sequenceNo: 1, createdAt: '2026-10-18T12:00:00Z',
      actorId, field: 'mood', operation: 'SET', value: 'calm', previousEffective, effectiveAfter,
    };
    const fetcher = dataFetcher((url) => url.pathname.endsWith('/corrections')
      ? jsonResponse({ ...history, events: [event], effectiveObservations: { ...history.effectiveObservations, mood: effectiveAfter } })
      : undefined);
    renderHistory(fetcher);
    const card = await loaded();
    await userEvent.click(within(card).getByRole('button', { name: 'Show corrections (1)' }));

    const correction = await within(card).findByRole('article', { name: /Correction event 1.*Mood/ });
    expect(within(correction).getByText(actorId)).toBeVisible();
    expect(within(correction).getByText(eventId)).toBeVisible();
    expect(within(correction).getByText('Previous effective value')).toBeVisible();
    expect(within(correction).getByText('New effective value')).toBeVisible();
    expect(within(correction).getByText('okay')).toBeVisible();
    expect(within(correction).getByText('Set to calm')).toBeVisible();
    expect(within(card).getByRole('heading', { name: 'Current effective value' })).toBeVisible();
    expect(within(card).getByText('calm')).toBeVisible();
    expect(within(correction).queryByRole('button', { name: 'Show more' })).not.toBeInTheDocument();
  });
  it('keeps timeline recipient, date, note link, and correction provenance visible', async () => {
    const longText = Array.from({ length: 10 }, (_, index) => `تصحيح ${index + 1} — mixed English`).join('\n');
    const longAddendum = {
      ...addendumFixture,
      freeText: longText,
      observations: { ...addendumFixture.observations, freeText: { state: 'RECORDED' as const, value: longText } },
    };
    const fetcher = dataFetcher((url) => url.pathname.endsWith('/addenda')
      ? jsonResponse([longAddendum]) : undefined);
    renderHistory(fetcher);

    const card = await loaded();
    const original = within(card).getByRole('article', { name: 'Original note' });
    expect(within(original).getByText(recipientFixture.name)).toBeVisible();
    expect(within(original).getByRole('time')).toHaveAttribute('dateTime', historyNote.date);
    expect(within(original).getByRole('link', { name: 'Open full note' })).toHaveAttribute('href', `/app/notes/${historyNote.id}`);
    await userEvent.click(within(original).getByText('View original observations and text'));
    expect(within(original).getByText('Unknown')).toBeVisible();

    await userEvent.click(within(card).getByRole('button', { name: 'Show corrections (1)' }));
    const correction = await within(card).findByRole('article', { name: 'Correction 1' });
    const body = within(correction).getByTestId('note-timeline-body');
    forceTimelineOverflow(body);
    await within(correction).findByRole('button', { name: 'Show more' });
    expect(body).toHaveAttribute('data-collapsed', 'true');
    expect(within(correction).getByText('Source ID:')).toBeVisible();
    expect(within(correction).getByText(addendumFixture.id)).toBeVisible();
    expect(within(correction).getByRole('time')).toHaveAttribute('dateTime', addendumFixture.createdAt);
    expect(within(correction).getByText(/تصحيح 1/).textContent).toBe(longText);
  });
  it('keeps history available if recipient names cannot load, then retries metadata', async () => {
    let failed = true;
    const fetcher = dataFetcher((url) => url.pathname === '/api/recipients' && failed ? jsonResponse({}, 500) : undefined);
    renderHistory(fetcher);
    expect(await screen.findByRole('article', { name: recipientFixture.id })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('names could not be loaded');
    failed = false;
    await userEvent.click(screen.getByRole('button', { name: 'Retry' })); await loaded();
    expect(queries(fetcher)).toHaveLength(1);
  });
  it('blocks invalid URL dates before history requests and allows recovery using reset', async () => {
    const fetcher = dataFetcher(); renderHistory(fetcher, `${root}?from=2026-02-30&to=2026-10-22`);
    await screen.findByText('Check the filters before loading notes.');
    expect(queries(fetcher)).toHaveLength(0);
    await userEvent.click(screen.getByRole('button', { name: 'Reset filters' })); await loaded();
  });
  it('renders Arabic, RTL, isolated names and untouched mixed text while switching language without refetch', async () => {
    const fetcher = dataFetcher(); renderHistory(fetcher, range, 'ar');
    const card = await loaded();
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    expect(screen.getByRole('heading', { name: 'سجل الملاحظات' })).toBeInTheDocument();
    expect(within(card).getByText(recipientFixture.name).tagName).toBe('BDI');
    expect(within(card).getByText(/غير متأكد من الدواء/)).toHaveAttribute('dir', 'auto');
    await userEvent.click(screen.getByRole('button', { name: 'Switch to English' }));
    expect(document.documentElement).toHaveAttribute('dir', 'ltr');
    expect(queries(fetcher)).toHaveLength(1);
  });
  it('clears a rejected session and redirects to sign in', async () => {
    renderHistory(dataFetcher((url) => url.pathname === '/api/notes' ? jsonResponse({}, 401) : undefined));
    expect(await screen.findByRole('heading', { name: 'Caregiver sign in' })).toBeInTheDocument();
    expect(sessionStorage.getItem('caregiver-auth-session-v1')).toBeNull();
  });
});
