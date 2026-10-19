import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../app/App';
import { AuthProvider } from '../features/auth/AuthProvider';
import { I18nProvider } from '../i18n/I18nProvider';
import { ApiProvider } from '../lib/api';
import { jsonResponse, testToken } from './authFixtures';
import { recipientFixture } from './recipientFixtures';
import { addendumFixture, noteFixture, noteObservations } from './noteFixtures';

const root = '/app/notes';
const detail = `${root}/${noteFixture.id}`;
function renderNotes(path: string, fetcher: ReturnType<typeof vi.fn>, language = 'en', authenticated = true) {
  vi.stubGlobal('fetch', fetcher);
  localStorage.setItem('caregiver-language', language);
  if (authenticated) sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider><MemoryRouter initialEntries={[path]}><App /></MemoryRouter></AuthProvider></ApiProvider></I18nProvider>);
}
function dataFetcher(overrides?: (path: string, init: RequestInit) => Response | Promise<Response> | undefined) {
  return vi.fn().mockImplementation((path: string, init: RequestInit) => {
    const override = overrides?.(path, init);
    if (override) return Promise.resolve(override);
    return Promise.resolve(jsonResponse(path === '/api/recipients' ? [recipientFixture]
      : path === `/api/recipients/${recipientFixture.id}` ? recipientFixture
        : path.endsWith('/addenda') ? [] : noteFixture));
  });
}
async function metadata() {
  await userEvent.selectOptions(await screen.findByLabelText('Care recipient'), recipientFixture.id);
  fireEvent.change(screen.getByLabelText('Observation date'), { target: { value: noteFixture.date } });
}

describe('daily-note creation and immutable corrections', () => {
  it('protects direct note/addendum URLs before any API request', () => {
    const fetcher = vi.fn();
    renderNotes(`${detail}/addenda/new`, fetcher, 'en', false);
    expect(screen.getByRole('heading', { name: 'Caregiver sign in' })).toBeInTheDocument();
    expect(fetcher).not.toHaveBeenCalled();
  });
  it('handles loading and the empty-recipient state', async () => {
    let resolve!: (response: Response) => void;
    renderNotes(`${root}/new`, vi.fn().mockImplementation(() => new Promise<Response>((done) => { resolve = done; })));
    expect(screen.getByText('Loading note information…')).toBeInTheDocument();
    resolve(jsonResponse([]));
    expect(await screen.findByText('No care recipients are available.')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Care recipients' }).at(-1)).toHaveAttribute('href', '/app/recipients');
  });
  it('retries a loading error without exposing backend details', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse({ message: 'private diagnostics' }, 500)).mockResolvedValueOnce(jsonResponse([recipientFixture]));
    renderNotes(`${root}/new`, fetcher);
    expect(await screen.findByRole('alert')).not.toHaveTextContent('private diagnostics');
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByLabelText('Care recipient')).toBeInTheDocument();
  });
  it('validates required metadata, focuses its first field, and makes no write', async () => {
    const fetcher = dataFetcher();
    renderNotes(`${root}/new`, fetcher);
    await screen.findByLabelText('Care recipient');
    fireEvent.change(screen.getByLabelText('Observation date'), { target: { value: '' } });
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    expect(screen.getByLabelText('Care recipient')).toHaveFocus();
    expect(screen.getByLabelText('Observation date')).toHaveAttribute('aria-invalid', 'true');
    expect(fetcher.mock.calls.filter(([, init]) => init.method !== 'GET')).toHaveLength(0);
  });
  it('submits every structured field and mixed free text unchanged, then shows a read-only original', async () => {
    let saved: Record<string, unknown> | null = null;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') { saved = JSON.parse(init.body as string); return jsonResponse(noteFixture, 201); }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    for (const [label, value] of [['Mood', noteObservations.mood], ['Appetite', noteObservations.appetite], ['Sleep', noteObservations.sleep], ['Mobility', noteObservations.mobility]] as const) {
      fireEvent.change(screen.getByLabelText(label), { target: { value } });
    }
    await userEvent.selectOptions(screen.getByLabelText('Medication taken'), 'UNKNOWN');
    await userEvent.selectOptions(screen.getByLabelText('Medication report verified'), 'false');
    fireEvent.change(screen.getByLabelText('Pain score (0–10)'), { target: { value: '0' } });
    await userEvent.selectOptions(screen.getByLabelText('Fall reported'), 'false');
    fireEvent.change(screen.getByLabelText('Free-text observations'), { target: { value: noteObservations.freeText } });
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    expect(await screen.findByText('Original note saved.')).toHaveFocus();
    expect(saved).toEqual({ ...noteObservations, recipientId: recipientFixture.id, date: noteFixture.date });
    expect(screen.getByRole('article', { name: 'Original note — read only' })).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.getByText(/غير متأكد من الدواء/).textContent).toBe(noteObservations.freeText);
    expect(document.querySelector('script')).not.toBeInTheDocument();
  });
  it('blocks duplicate clicks and safely reuses the identical payload/key after a lost response', async () => {
    const posts: RequestInit[] = [];
    let reject!: (error: unknown) => void;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') {
        posts.push(init);
        return posts.length === 1 ? new Promise<Response>((_, fail) => { reject = fail; }) : jsonResponse(noteFixture, 201);
      }
    });
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    const form = screen.getByRole('form', { name: 'Record a daily note' });
    fireEvent.submit(form);
    fireEvent.submit(form);
    expect(posts).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Saving…' })).toBeDisabled();
    reject(new TypeError('response lost'));
    expect(await screen.findByRole('alert')).toHaveTextContent('couldn’t confirm');
    expect(screen.getByLabelText('Free-text observations')).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Retry the same save' }));
    await screen.findByText('Original note saved.');
    expect(posts).toHaveLength(2);
    expect(posts[1].body).toBe(posts[0].body);
    expect(new Headers(posts[1].headers).get('Idempotency-Key')).toBe(new Headers(posts[0].headers).get('Idempotency-Key'));
  });
  it('keeps values editable after explicit backend validation failure', async () => {
    renderNotes(`${root}/new`, dataFetcher((path, init) => path === '/api/notes' && init.method === 'POST' ? jsonResponse({ message: 'private validation' }, 400) : undefined));
    await metadata();
    fireEvent.change(screen.getByLabelText('Free-text observations'), { target: { value: 'القيمة الأصلية' } });
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Check the entered values');
    expect(screen.getByLabelText('Free-text observations')).toBeEnabled();
    expect(screen.getByLabelText('Free-text observations')).toHaveValue('القيمة الأصلية');
    expect(screen.queryByText('private validation')).not.toBeInTheDocument();
  });
  it('opens the existing note after a recipient/date conflict without another POST', async () => {
    const fetcher = dataFetcher((path, init) => path === '/api/notes' && init.method === 'POST' ? jsonResponse({ code: 'NOTE_CONFLICT' }, 409) : undefined);
    renderNotes(`${root}/new`, fetcher);
    await metadata();
    await userEvent.click(screen.getByRole('button', { name: 'Save original note' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('conflicts with an existing record');
    await userEvent.click(screen.getByRole('button', { name: 'Open note for this date' }));
    await screen.findByRole('heading', { name: 'Daily note details' });
    await screen.findByRole('heading', { name: 'Original note — read only' });
    expect(fetcher.mock.calls.filter(([, init]) => init.method === 'POST')).toHaveLength(1);
  });
  it('can open an inactive recipient’s existing note but cannot create one', async () => {
    renderNotes(`${root}/new`, dataFetcher((path) => path === '/api/recipients' ? jsonResponse([{ ...recipientFixture, active: false }]) : undefined));
    await metadata();
    expect(screen.getByRole('button', { name: 'Save original note' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Open note for this date' }));
    expect(await screen.findByRole('heading', { name: 'Original note — read only' })).toBeInTheDocument();
  });
  it('reports a missing note on lookup and retains the selected metadata', async () => {
    renderNotes(`${root}/new`, dataFetcher((path) => path.includes('/by-recipient/') ? jsonResponse({}, 404) : undefined));
    await metadata();
    await userEvent.click(screen.getByRole('button', { name: 'Open note for this date' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('No note was found');
    expect(screen.getByLabelText('Observation date')).toHaveValue(noteFixture.date);
  });
  it('records only selected correction fields and leaves the original immutable', async () => {
    const writes: RequestInit[] = [];
    let appended = false;
    const fetcher = dataFetcher((path, init) => {
      if (path.endsWith('/addenda') && init.method === 'POST') { appended = true; writes.push(init); return jsonResponse(addendumFixture, 201); }
      if (path.endsWith('/addenda')) return jsonResponse(appended ? [addendumFixture] : []);
    });
    renderNotes(`${detail}/addenda/new`, fetcher);
    const form = await screen.findByRole('form', { name: 'Add a correction' });
    await userEvent.click(within(form).getByRole('button', { name: 'Save addendum' }));
    expect(screen.getByRole('checkbox', { name: 'Correct this field: Mood' })).toHaveFocus();
    expect(writes).toHaveLength(0);
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Medication taken' }));
    await userEvent.selectOptions(screen.getByLabelText('Medication taken', { exact: true }), 'MISSED');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Medication report verified' }));
    await userEvent.selectOptions(screen.getByLabelText('Medication report verified', { exact: true }), 'false');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Free-text observations' }));
    fireEvent.change(screen.getByLabelText('Free-text observations', { exact: true }), { target: { value: addendumFixture.freeText } });
    await userEvent.click(screen.getByRole('button', { name: 'Save addendum' }));
    expect(await screen.findByText('Addendum saved. The original note is unchanged.')).toHaveFocus();
    expect(JSON.parse(writes[0].body as string)).toEqual({ medicationStatus: 'MISSED', medicationVerified: false, freeText: addendumFixture.freeText });
    const original = screen.getByRole('article', { name: 'Original note — read only' });
    expect(within(original).getByText(/غير متأكد/).textContent).toBe(noteObservations.freeText);
    expect(within(original).getByText('Unknown / unsure')).toBeInTheDocument();
    expect(screen.getByText('Missed')).toBeInTheDocument();
    expect(fetcher.mock.calls.some(([, init]) => init.method === 'PUT' || init.method === 'PATCH' || init.method === 'DELETE')).toBe(false);
  });
  it('validates a selected empty correction without inferring values', async () => {
    renderNotes(`${detail}/addenda/new`, dataFetcher());
    await screen.findByRole('form', { name: 'Add a correction' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Correct this field: Medication taken' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save addendum' }));
    expect(screen.getByLabelText('Medication taken', { exact: true })).toHaveFocus();
    expect(screen.getByLabelText('Medication taken', { exact: true })).toHaveValue('');
    expect(screen.getByRole('alert')).toHaveTextContent('replacement value');
  });
  it('renders Arabic/RTL and mixed text safely without altering its content', async () => {
    renderNotes(detail, dataFetcher(), 'ar');
    await screen.findByRole('heading', { name: 'الملاحظة الأصلية — للقراءة فقط' });
    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    const text = screen.getByText(/غير متأكد من الدواء/);
    expect(text).toHaveAttribute('dir', 'auto');
    expect(text.textContent).toBe(noteObservations.freeText);
    expect(screen.getByText('غير معروف / غير متأكد')).toBeInTheDocument();
  });
  it('handles unavailable note details generically', async () => {
    renderNotes(detail, vi.fn().mockResolvedValue(jsonResponse({ message: 'owner private' }, 404)));
    expect(await screen.findByRole('alert')).toHaveTextContent('unavailable');
    expect(screen.queryByText('owner private')).not.toBeInTheDocument();
  });
  it('cancels abandoned requests and does not publish a late successful save', async () => {
    let signal!: AbortSignal;
    let resolve!: (value: Response) => void;
    const fetcher = dataFetcher((path, init) => {
      if (path === '/api/notes' && init.method === 'POST') { signal = init.signal!; return new Promise<Response>((done) => { resolve = done; }); }
    });
    const view = renderNotes(`${root}/new`, fetcher);
    await metadata();
    fireEvent.submit(screen.getByRole('form', { name: 'Record a daily note' }));
    view.unmount();
    expect(signal.aborted).toBe(true);
    resolve(jsonResponse(noteFixture, 201));
    await waitFor(() => expect(fetcher.mock.calls.filter(([, init]) => init.method === 'POST')).toHaveLength(1));
  });
});
