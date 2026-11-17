import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { App } from '../app/App';
import { AuthProvider } from '../features/auth/AuthProvider';
import { I18nProvider } from '../i18n/I18nProvider';
import { ApiProvider } from '../lib/api';
import { jsonResponse, testToken } from './authFixtures';
import { recipientFixture as recipient } from './recipientFixtures';
import { historyNote, historyPage } from './historyFixtures';
import { MedicationListPanel } from '../features/medications/MedicationListPanel';

const root = '/app/recipients';
const detail = `${root}/${recipient.id}`;
const fullCareDetails = {
  dateOfBirth: '1950-04-12',
  allergies: 'Penicillin',
  conditions: 'Type 2 diabetes',
  emergencyContactName: 'Layla Hassan',
  emergencyContactPhone: '+20 (100) 123-4567',
};
const showModal = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'showModal');
const close = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'close');

beforeAll(() => {
  // jsdom has no dialog top layer. Actual modal behavior is covered in Chromium.
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value() { this.setAttribute('open', ''); } });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value() { this.removeAttribute('open'); } });
});
afterAll(() => {
  if (showModal) Object.defineProperty(HTMLDialogElement.prototype, 'showModal', showModal);
  else Reflect.deleteProperty(HTMLDialogElement.prototype, 'showModal');
  if (close) Object.defineProperty(HTMLDialogElement.prototype, 'close', close);
  else Reflect.deleteProperty(HTMLDialogElement.prototype, 'close');
});

function renderRecipients(path: string, fetcher: ReturnType<typeof vi.fn>, language = 'en', authenticated = true, locationState?: unknown) {
  vi.stubGlobal('fetch', fetcher);
  localStorage.setItem('caregiver-language', language);
  if (authenticated) sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
  const entry = locationState === undefined ? path : { pathname: path, state: locationState };
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider><MemoryRouter initialEntries={[entry]}><App /></MemoryRouter></AuthProvider></ApiProvider></I18nProvider>);
}

function recipientDetailFetcher(value: typeof recipient) {
  return vi.fn().mockImplementation((requestPath: string) => {
    const path = new URL(requestPath, 'http://localhost').pathname;
    return Promise.resolve(path === `/api/recipients/${recipient.id}` ? jsonResponse(value) : jsonResponse([]));
  });
}

function creationFetcher(existing: (typeof recipient)[], createdName: string) {
  const created = { ...recipient, id: 'b2222222-2222-4222-8222-222222222222', name: createdName };
  return vi.fn().mockImplementation((path: string, options: RequestInit = {}) => {
    if (path === '/api/recipients' && options.method === 'POST') {
      return Promise.resolve(jsonResponse({ ...created, createdAt: null, updatedAt: null }, 201));
    }
    if (path === '/api/recipients') return Promise.resolve(jsonResponse(existing));
    if (path === `/api/recipients/${created.id}`) return Promise.resolve(jsonResponse(created));
    return Promise.resolve(jsonResponse([]));
  });
}

function postedRecipient(fetcher: ReturnType<typeof vi.fn>) {
  const post = fetcher.mock.calls.find(([, options]) => options.method === 'POST');
  return post ? JSON.parse(post[1].body) : undefined;
}

describe('care recipients screens', () => {
  it('keeps recipient tabs on deep-linkable, recipient-scoped routes', async () => {
    const fetcher = vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      return Promise.resolve(url.pathname === '/api/recipients'
        ? jsonResponse([recipient])
        : url.pathname === '/api/notes'
          ? jsonResponse(historyPage([{ ...historyNote, recipientId: recipient.id }]))
          : jsonResponse([]));
    });
    renderRecipients(`${detail}/notes`, fetcher);

    const tabs = await screen.findByRole('navigation', { name: 'Recipient sections' }, { timeout: 5000 });
    expect(within(tabs).getByRole('link', { name: 'Notes' })).toHaveAttribute('aria-current', 'page');
    expect(within(tabs).getByRole('link', { name: 'Overview' })).toHaveAttribute('href', detail);
    expect(within(tabs).getByRole('link', { name: 'Summaries' })).toHaveAttribute('href', `${detail}/summaries`);
    expect(within(tabs).getByRole('link', { name: 'Plan' })).toHaveAttribute('href', `${detail}/plans`);
    expect(await screen.findByRole('table', { name: 'Notes, ordered newest first, with their original observations and corrections.' })).toBeInTheDocument();
    expect(screen.getByLabelText('Care recipient')).toHaveValue(recipient.id);
    expect(screen.getByLabelText('Care recipient')).toBeDisabled();
    expect(fetcher.mock.calls.some(([path]) => {
      const url = new URL(path, 'http://localhost');
      return url.pathname === '/api/notes' && url.searchParams.get('recipientId') === recipient.id;
    })).toBe(true);

    await userEvent.click(within(screen.getByRole('navigation', { name: 'Recipient sections' })).getByRole('link', { name: 'Summaries' }));
    const summaryRecipient = await screen.findByLabelText('Care recipient', { exact: true });
    expect(summaryRecipient).toHaveValue(recipient.id);
    expect(summaryRecipient).toBeDisabled();
    expect(screen.getByText('For care support only. This summary is not a diagnosis, a prescription, or medical advice. It may be incomplete or incorrect; review the source notes and discuss care decisions with a qualified professional.')).toBeVisible();

    await userEvent.click(within(screen.getByRole('navigation', { name: 'Recipient sections' })).getByRole('link', { name: 'Plan' }));
    const planRecipient = await screen.findByLabelText('Care recipient', { exact: true });
    expect(planRecipient).toHaveAttribute('id', 'plans-recipient');
    expect(planRecipient).toHaveValue(recipient.id);
    expect(planRecipient).toBeDisabled();
  });

  it('protects direct recipient routes before making any API request', async () => {
    const fetcher = vi.fn();
    renderRecipients(`${detail}/edit`, fetcher, 'en', false);
    expect(await screen.findByRole('heading', { name: 'Caregiver sign in' })).toBeInTheDocument();
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('renders loading, owned recipients and active status without ownership internals', async () => {
    let resolve!: (response: Response) => void;
    const fetcher = vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
      return new Promise<Response>((done) => { resolve = done; });
    });
    renderRecipients(root, fetcher);
    expect(screen.getByText('Loading your care recipients…')).toBeInTheDocument();
    resolve(jsonResponse([recipient, { ...recipient, id: 'e0000000-0000-4000-8000-000000000099', name: 'Archived Extra',
      active: false, lifecycleStatus: 'ARCHIVED', archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' }]));
    expect(await screen.findByRole('link', { name: recipient.name })).toHaveAttribute('href', detail);
    expect(within(screen.getByRole('group', { name: 'Filter recipients by lifecycle status' })).getByRole('button', { name: 'Active' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByText('secret-owner')).not.toBeInTheDocument();
    const recipientsCall = fetcher.mock.calls.find(([path]) => new URL(path, 'http://localhost').pathname === '/api/recipients');
    expect(new Headers(recipientsCall![1].headers).get('Authorization')).toMatch(/^Bearer /);
    expect(screen.getByRole('navigation').querySelector('[aria-current="page"]')).toHaveTextContent('People');
  });

  it('offers creation from the empty state', async () => {
    renderRecipients(root, vi.fn().mockResolvedValue(jsonResponse([])));
    expect(await screen.findByText('No active care recipients', { exact: true })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('link', { name: 'Add care recipient' }));
    expect(screen.getByRole('form', { name: 'Add care recipient' })).toBeInTheDocument();
  });
  it('searches recipients by name or relationship', async () => {
    const fillers = Array.from({ length: 4 }, (_, index) => ({ ...recipient, id: `d0000000-0000-4000-8000-00000000000${index}`, name: `Filler ${index}` }));
    const fetcher = vi.fn().mockResolvedValue(jsonResponse([recipient, { ...recipient, id: 'b2222222-2222-4222-8222-222222222222', name: 'Mona', relationship: 'Aunt', active: true }, ...fillers]));
    renderRecipients(root, fetcher);
    expect(await screen.findByRole('link', { name: recipient.name })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Mona' })).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Search name or relationship'), 'aunt');
    expect(screen.getByRole('link', { name: 'Mona' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: recipient.name })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Clear' }));
    expect(screen.getByRole('link', { name: recipient.name })).toBeInTheDocument();
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it('filters recipients by case-insensitive name and relationship', async () => {
    const nameMatch = { ...recipient, name: 'caregiver Samir', relationship: 'Son' };
    const relationshipMatch = { ...recipient, id: 'b2222222-2222-4222-8222-222222222222', name: 'Mona', relationship: 'Primary caregiver' };
    const unrelated = { ...recipient, id: 'c3333333-3333-4333-8333-333333333333', name: 'Laila', relationship: 'Aunt' };
    const fillers = Array.from({ length: 3 }, (_, index) => ({ ...recipient, id: `e0000000-0000-4000-8000-00000000000${index}`, name: `Filler ${index}` }));
    renderRecipients(root, vi.fn().mockResolvedValue(jsonResponse([nameMatch, relationshipMatch, unrelated, ...fillers])));

    await screen.findByRole('link', { name: nameMatch.name });
    await userEvent.type(screen.getByLabelText('Search name or relationship'), 'CaReGiVeR');
    expect(screen.getByRole('link', { name: nameMatch.name })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: relationshipMatch.name })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: unrelated.name })).not.toBeInTheDocument();
  });

  it('shows no-match state separately from empty recipient list', async () => {
    const fillers = Array.from({ length: 5 }, (_, index) => ({ ...recipient, id: `f0000000-0000-4000-8000-00000000000${index}`, name: `Filler ${index}` }));
    const populated = vi.fn().mockResolvedValue(jsonResponse([recipient, ...fillers]));
    const view = renderRecipients(root, populated);
    await screen.findByRole('link', { name: recipient.name });
    await userEvent.type(screen.getByLabelText('Search name or relationship'), 'not found');
    expect(screen.getByText('No matching recipients')).toBeInTheDocument();
    expect(screen.queryByText('No active care recipients')).not.toBeInTheDocument();
    view.unmount();

    const empty = vi.fn().mockResolvedValue(jsonResponse([]));
    renderRecipients(root, empty);
    expect(await screen.findByText('No active care recipients')).toBeInTheDocument();
    expect(screen.queryByText('No matching recipients')).not.toBeInTheDocument();
  });

  it('clears recipient search without changing lifecycle filter', async () => {
    const archived = { ...recipient, id: 'b2222222-2222-4222-8222-222222222222', name: 'Archived Mona',
      active: false, lifecycleStatus: 'ARCHIVED' as const, archivedAt: '2026-10-15T10:00:00Z',
      archivedBy: 'b1111111-1111-4111-8111-111111111111', relationship: 'Aunt' };
    const active = { ...recipient, id: 'c3333333-3333-4333-8333-333333333333', name: 'Active Laila' };
    const archivedFillers = Array.from({ length: 5 }, (_, index) => ({ ...recipient, id: `b0000000-0000-4000-8000-00000000000${index}`,
      name: `Archived Filler ${index}`, active: false, lifecycleStatus: 'ARCHIVED' as const,
      archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' }));
    const fetcher = vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
      return Promise.resolve(jsonResponse([active, archived, ...archivedFillers]));
    });
    renderRecipients(root, fetcher);

    await userEvent.click(await screen.findByRole('button', { name: 'Archived' }));
    expect(await screen.findByRole('link', { name: archived.name })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: active.name })).not.toBeInTheDocument();
    const filterGroup = screen.getByRole('group', { name: 'Filter recipients by lifecycle status' });
    expect(within(filterGroup).getByRole('button', { name: 'Archived' })).toHaveAttribute('aria-pressed', 'true');

    await userEvent.type(screen.getByLabelText('Search name or relationship'), 'no result');
    expect(screen.getByText('No matching recipients')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Clear' }));

    expect(within(filterGroup).getByRole('button', { name: 'Archived' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('link', { name: archived.name })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: active.name })).not.toBeInTheDocument();
    expect(fetcher).toHaveBeenCalledWith('/api/recipients', expect.anything());
  });

  it('retries a list error and does not display raw server messages', async () => {
    let recipientCalls = 0;
    const fetcher = vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
      recipientCalls += 1;
      return Promise.resolve(recipientCalls === 1 ? jsonResponse({ message: 'private diagnostics' }, 500) : jsonResponse([recipient]));
    });
    renderRecipients(root, fetcher);
    expect(await screen.findByRole('alert')).toHaveTextContent('We couldn’t complete this request.');
    expect(screen.queryByText('private diagnostics')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('link', { name: recipient.name })).toBeInTheDocument();
  });

  it('validates create before submitting, trims fields and opens saved details', async () => {
    // POST currently returns null timestamps; GET returns the committed dates.
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({ ...recipient, createdAt: null, updatedAt: null }, 201))
      .mockResolvedValueOnce(jsonResponse(recipient));
    renderRecipients(`${root}/new`, fetcher);
    await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));
    expect(screen.getByRole('textbox', { name: 'Name' })).toHaveFocus();
    expect(screen.getByRole('alert')).toHaveTextContent('Enter the recipient’s name.');
    expect(fetcher).not.toHaveBeenCalled();
    await userEvent.type(screen.getByRole('textbox', { name: 'Name' }), ' Samir Hassan ');
    await userEvent.click(screen.getByRole('radio', { name: 'Father' }));
    await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));
    expect(await screen.findByRole('heading', { name: recipient.name })).toBeInTheDocument();
    expect(screen.getByText('Care recipient added.')).toHaveFocus();
    const post = fetcher.mock.calls.find(([, options]) => options.method === 'POST');
    expect(post).toBeDefined();
    expect(JSON.parse(post![1].body)).toEqual({ name: recipient.name, relationship: 'FATHER' });
    expect(post![1].method).toBe('POST');
  });

  it('warns when a name matches after trimming, collapsing whitespace and lowercasing', async () => {
    const fetcher = creationFetcher([recipient], 'Another Samir');
    renderRecipients(`${root}/new`, fetcher);
    await userEvent.type(screen.getByLabelText('Name'), '  samir   hassan  ');
    await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));

    const warning = await screen.findByRole('alert');
    expect(warning).toHaveTextContent('A recipient with this name already exists');
    expect(warning).toHaveTextContent('You already have a recipient with this name.');
    expect(warning).toHaveTextContent(recipient.name);
    expect(warning).toHaveTextContent('Active');
    expect(within(warning).getByRole('link', { name: 'Open existing' })).toHaveAttribute('href', detail);
    expect(within(warning).getByRole('button', { name: 'Create anyway' })).toBeVisible();
    expect(within(warning).getByRole('button', { name: 'Cancel' })).toBeVisible();
    expect(fetcher.mock.calls.filter(([, options]) => options.method === 'POST')).toHaveLength(0);
  });

  it('creates directly when no normalized name matches', async () => {
    const fetcher = creationFetcher([recipient], 'Mona Hassan');
    renderRecipients(`${root}/new`, fetcher);
    await userEvent.type(screen.getByLabelText('Name'), 'Mona Hassan');
    await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));

    expect(await screen.findByText('Care recipient added.')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(fetcher.mock.calls.filter(([, options]) => options.method === 'POST')).toHaveLength(1);
  });

  it('continues creation when the duplicate-list request fails', async () => {
    const created = { ...recipient, id: 'b2222222-2222-4222-8222-222222222222', name: 'Mona Hassan' };
    const fetcher = vi.fn().mockImplementation((path: string, options: RequestInit = {}) => {
      if (path === '/api/recipients' && options.method === 'POST') {
        return Promise.resolve(jsonResponse({ ...created, createdAt: null, updatedAt: null }, 201));
      }
      if (path === '/api/recipients') return Promise.reject(new TypeError('Network unavailable'));
      if (path === `/api/recipients/${created.id}`) return Promise.resolve(jsonResponse(created));
      return Promise.resolve(jsonResponse([]));
    });
    renderRecipients(`${root}/new`, fetcher);
    await userEvent.type(screen.getByLabelText('Name'), created.name);
    await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));

    expect(await screen.findByText('Care recipient added.')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(fetcher.mock.calls.filter(([, options]) => options.method === 'POST')).toHaveLength(1);
  });

  it('creates anyway without checking for the duplicate again', async () => {
    const fetcher = creationFetcher([recipient], 'Another Samir');
    renderRecipients(`${root}/new`, fetcher);
    await userEvent.type(screen.getByLabelText('Name'), recipient.name);
    await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));
    const warning = await screen.findByRole('alert');
    await userEvent.click(within(warning).getByRole('button', { name: 'Create anyway' }));

    expect(await screen.findByText('Care recipient added.')).toBeInTheDocument();
    expect(fetcher.mock.calls.filter(([path, options]) => path === '/api/recipients' && options.method !== 'POST')).toHaveLength(1);
    expect(fetcher.mock.calls.filter(([, options]) => options.method === 'POST')).toHaveLength(1);
  });

  it('matches an existing Arabic-script name and renders the warning in Arabic RTL', async () => {
    const arabicRecipient = { ...recipient, name: 'سمير حسن' };
    const fetcher = creationFetcher([arabicRecipient], 'مستفيد آخر');
    renderRecipients(`${root}/new`, fetcher, 'ar');
    await userEvent.type(screen.getByLabelText('الاسم'), 'سمير حسن');
    await userEvent.click(screen.getByRole('button', { name: 'إضافة مستفيد من الرعاية' }));

    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    const warning = await screen.findByRole('alert');
    expect(warning).toHaveTextContent('يوجد بالفعل مستفيد من الرعاية بهذا الاسم');
    expect(warning).toHaveTextContent(arabicRecipient.name);
    expect(within(warning).getByRole('link', { name: 'فتح المستفيد الحالي' })).toHaveAttribute('href', detail);
    expect(within(warning).getByRole('button', { name: 'إنشاء رغم ذلك' })).toBeVisible();
    expect(fetcher.mock.calls.filter(([, options]) => options.method === 'POST')).toHaveLength(0);
  });

  it('rejects overlength values and links validation to the affected field', async () => {
    const fetcher = vi.fn();
    renderRecipients(`${root}/new`, fetcher);
    fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'x'.repeat(121) } });
    await userEvent.click(screen.getByRole('radio', { name: 'Other' }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Describe the relationship' }), { target: { value: 'x'.repeat(81) } });
    await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));
    expect(screen.getAllByRole('alert')).toHaveLength(2);
    expect(screen.getByRole('textbox', { name: 'Name' })).toHaveAttribute('aria-describedby', expect.stringContaining('recipient-name-error'));
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('preserves form values after backend validation errors and permits correction', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({ code: 'VALIDATION_ERROR', message: 'private diagnostics' }, 422))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse(recipient, 201))
      .mockResolvedValueOnce(jsonResponse(recipient));
    renderRecipients(`${root}/new`, fetcher);
    await userEvent.type(screen.getByLabelText('Name'), recipient.name);
    await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));
    expect(await screen.findByRole('alert')).toHaveFocus();
    expect(screen.getByLabelText('Name')).toHaveValue(recipient.name);
    expect(screen.queryByText('private diagnostics')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));
    expect(await screen.findByText('Care recipient added.')).toBeInTheDocument();
  });

  it('reuses the same Idempotency-Key when retrying a failed create', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({ ...recipient, createdAt: null, updatedAt: null }, 201))
      .mockResolvedValueOnce(jsonResponse(recipient));
    renderRecipients(`${root}/new`, fetcher);
    await userEvent.type(screen.getByLabelText('Name'), recipient.name);
    await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));
    expect(await screen.findByText('Care recipient added.')).toBeInTheDocument();
    const posts = fetcher.mock.calls.filter(([, options]) => options.method === 'POST');
    const first = new Headers(posts[0][1].headers).get('Idempotency-Key');
    const second = new Headers(posts[1][1].headers).get('Idempotency-Key');
    expect(first).toMatch(/^[0-9a-f]{32}$/);
    expect(second).toBe(first);
  });

  it('generates a new Idempotency-Key when the input changes before retrying', async () => {
    const fetcher = vi.fn()
      .mockResolvedValueOnce(jsonResponse([]))
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({ ...recipient, createdAt: null, updatedAt: null }, 201))
      .mockResolvedValueOnce(jsonResponse(recipient));
    renderRecipients(`${root}/new`, fetcher);
    await userEvent.type(screen.getByLabelText('Name'), 'First name');
    await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText('Name'));
    await userEvent.type(screen.getByLabelText('Name'), 'Second name');
    await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));
    expect(await screen.findByText('Care recipient added.')).toBeInTheDocument();
    const posts = fetcher.mock.calls.filter(([, options]) => options.method === 'POST');
    const first = new Headers(posts[0][1].headers).get('Idempotency-Key');
    const second = new Headers(posts[1][1].headers).get('Idempotency-Key');
    expect(first).toMatch(/^[0-9a-f]{32}$/);
    expect(second).toMatch(/^[0-9a-f]{32}$/);
    expect(second).not.toBe(first);
  });

  it('sends no Idempotency-Key when editing a recipient', async () => {
    const updated = { ...recipient, name: 'Samir Updated', relationship: null };
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(recipient))
      .mockResolvedValueOnce(jsonResponse(updated)).mockResolvedValueOnce(jsonResponse(updated));
    renderRecipients(`${detail}/edit`, fetcher);
    expect(await screen.findByLabelText('Name')).toHaveValue(recipient.name);
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Recipient details updated.')).toBeInTheDocument();
    const putCall = fetcher.mock.calls.find(([, init]) => (init as RequestInit).method === 'PUT');
    expect(putCall).toBeDefined();
    expect(new Headers((putCall![1] as RequestInit).headers).get('Idempotency-Key')).toBeNull();
  });

  it('edits current data with PUT, including removing the optional relationship', async () => {
    const updated = { ...recipient, name: 'Samir Updated', relationship: null };
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(recipient))
      .mockResolvedValueOnce(jsonResponse(updated)).mockResolvedValueOnce(jsonResponse(updated));
    renderRecipients(`${detail}/edit`, fetcher);
    expect(await screen.findByLabelText('Name')).toHaveValue(recipient.name);
    await userEvent.clear(screen.getByLabelText('Name'));
    await userEvent.type(screen.getByLabelText('Name'), updated.name);
    await userEvent.click(screen.getByRole('radio', { name: 'Father' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Recipient details updated.')).toBeInTheDocument();
    expect(JSON.parse(fetcher.mock.calls[1][1].body)).toEqual({ name: updated.name, relationship: null });
    expect(fetcher.mock.calls[1][1].method).toBe('PUT');
    expect(await screen.findByRole('heading', { name: updated.name })).toBeInTheDocument();
  });

  it('saves all care details with PATCH and shows a saved notice', async () => {
    const updated = { ...recipient, ...fullCareDetails };
    const fetcher = vi.fn().mockImplementation((path: string, options: RequestInit = {}) =>
      path === `/api/recipients/${recipient.id}/care-details` && options.method === 'PATCH'
        ? Promise.resolve(jsonResponse(updated))
        : Promise.resolve(jsonResponse(recipient)));
    renderRecipients(`${detail}/edit`, fetcher);
    const form = await screen.findByRole('form', { name: 'Care details' });

    fireEvent.change(within(form).getByLabelText('Date of birth'), { target: { value: fullCareDetails.dateOfBirth } });
    await userEvent.type(within(form).getByLabelText('Allergies'), fullCareDetails.allergies);
    await userEvent.type(within(form).getByLabelText('Conditions'), fullCareDetails.conditions);
    await userEvent.type(within(form).getByLabelText('Emergency contact name'), fullCareDetails.emergencyContactName);
    await userEvent.type(within(form).getByLabelText('Emergency contact phone'), fullCareDetails.emergencyContactPhone);
    await userEvent.click(within(form).getByRole('button', { name: 'Save care details' }));

    expect(await within(form).findByText('Care details saved')).toBeInTheDocument();
    const patch = fetcher.mock.calls.find(([path, options]) => path === `/api/recipients/${recipient.id}/care-details`
      && (options as RequestInit).method === 'PATCH');
    expect(patch).toBeDefined();
    expect(JSON.parse((patch![1] as RequestInit).body as string)).toEqual(fullCareDetails);
  });

  it('sends null when a care-detail field is cleared', async () => {
    const existing = { ...recipient, ...fullCareDetails };
    const updated = { ...existing, allergies: null };
    const fetcher = vi.fn().mockImplementation((path: string, options: RequestInit = {}) =>
      path === `/api/recipients/${recipient.id}/care-details` && options.method === 'PATCH'
        ? Promise.resolve(jsonResponse(updated))
        : Promise.resolve(jsonResponse(existing)));
    renderRecipients(`${detail}/edit`, fetcher);
    const form = await screen.findByRole('form', { name: 'Care details' });
    await userEvent.clear(within(form).getByLabelText('Allergies'));
    await userEvent.click(within(form).getByRole('button', { name: 'Save care details' }));

    expect(await within(form).findByText('Care details saved')).toBeInTheDocument();
    const patch = fetcher.mock.calls.find(([path, options]) => path === `/api/recipients/${recipient.id}/care-details`
      && (options as RequestInit).method === 'PATCH');
    expect(JSON.parse((patch![1] as RequestInit).body as string)).toEqual({
      dateOfBirth: fullCareDetails.dateOfBirth,
      allergies: null,
      conditions: fullCareDetails.conditions,
      emergencyContactName: fullCareDetails.emergencyContactName,
      emergencyContactPhone: fullCareDetails.emergencyContactPhone,
    });
  });

  it('rejects a future date of birth', async () => {
    const fetcher = vi.fn().mockResolvedValue(jsonResponse(recipient));
    renderRecipients(`${detail}/edit`, fetcher);
    const form = await screen.findByRole('form', { name: 'Care details' });
    const dateOfBirth = within(form).getByLabelText('Date of birth');
    const now = new Date();
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
    expect(dateOfBirth).toHaveAttribute('type', 'date');
    expect(dateOfBirth).toHaveAttribute('dir', 'ltr');
    expect(dateOfBirth).toHaveAttribute('max', today);
    fireEvent.change(dateOfBirth, { target: { value: '2099-12-31' } });
    await userEvent.click(within(form).getByRole('button', { name: 'Save care details' }));

    expect(await screen.findByText('Enter a valid date of birth')).toBeInTheDocument();
    expect(fetcher.mock.calls.some(([, options]) => (options as RequestInit).method === 'PATCH')).toBe(false);
  });

  it('shows the phone error for invalid emergency contact phone', async () => {
    const fetcher = vi.fn().mockResolvedValue(jsonResponse(recipient));
    renderRecipients(`${detail}/edit`, fetcher);
    const form = await screen.findByRole('form', { name: 'Care details' });
    const phone = within(form).getByLabelText('Emergency contact phone');
    expect(phone).toHaveAttribute('type', 'tel');
    expect(phone).toHaveAttribute('dir', 'ltr');
    fireEvent.change(phone, { target: { value: '1' } });
    await userEvent.click(within(form).getByRole('button', { name: 'Save care details' }));

    expect(await screen.findByText('Enter a valid phone number')).toBeInTheDocument();
    expect(fetcher.mock.calls.some(([, options]) => (options as RequestInit).method === 'PATCH')).toBe(false);
  });

  it('does not render the care-details form for an archived recipient', async () => {
    const archived = {
      ...recipient,
      active: false,
      lifecycleStatus: 'ARCHIVED' as const,
      archivedAt: '2026-10-15T10:00:00Z',
      archivedBy: 'b1111111-1111-4111-8111-111111111111',
    };
    renderRecipients(`${detail}/edit`, vi.fn().mockResolvedValue(jsonResponse(archived)));

    expect(await screen.findByText(/This recipient is archived and read-only/)).toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Care details' })).not.toBeInTheDocument();
  });

  describe('relationship chips', () => {
    const chipCases = [
      ['MOTHER', 'Mother'],
      ['FATHER', 'Father'],
      ['SPOUSE', 'Spouse'],
      ['GRANDMOTHER', 'Grandmother'],
      ['GRANDFATHER', 'Grandfather'],
      ['CHILD', 'Child'],
      ['SIBLING', 'Sibling'],
      ['RELATIVE', 'Relative'],
      ['FRIEND', 'Friend'],
      ['CLIENT', 'Client'],
    ] as const;

    it.each(chipCases)('sends %s for the %s chip', async (code, label) => {
      const name = `Chip ${code}`;
      const fetcher = creationFetcher([], name);
      renderRecipients(`${root}/new`, fetcher);
      await userEvent.type(await screen.findByLabelText('Name'), name);
      await userEvent.click(screen.getByRole('radio', { name: label }));
      await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));

      expect(await screen.findByText('Care recipient added.')).toBeInTheDocument();
      expect(postedRecipient(fetcher)).toEqual({ name, relationship: code });
    });

    it('sends null when the selected chip is tapped again', async () => {
      const fetcher = creationFetcher([], 'Deselected chip');
      renderRecipients(`${root}/new`, fetcher);
      await userEvent.type(await screen.findByLabelText('Name'), 'Deselected chip');
      const chip = screen.getByRole('radio', { name: 'Mother' });
      await userEvent.click(chip);
      await userEvent.click(chip);
      await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));

      expect(await screen.findByText('Care recipient added.')).toBeInTheDocument();
      expect(postedRecipient(fetcher)).toEqual({ name: 'Deselected chip', relationship: null });
    });

    it('sends null when Other has no text', async () => {
      const fetcher = creationFetcher([], 'Empty other');
      renderRecipients(`${root}/new`, fetcher);
      await userEvent.type(await screen.findByLabelText('Name'), 'Empty other');
      await userEvent.click(screen.getByRole('radio', { name: 'Other' }));
      await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));

      expect(await screen.findByText('Care recipient added.')).toBeInTheDocument();
      expect(postedRecipient(fetcher)).toEqual({ name: 'Empty other', relationship: null });
    });

    it('sends trimmed Other text', async () => {
      const fetcher = creationFetcher([], 'Other text');
      renderRecipients(`${root}/new`, fetcher);
      await userEvent.type(await screen.findByLabelText('Name'), 'Other text');
      await userEvent.click(screen.getByRole('radio', { name: 'Other' }));
      await userEvent.type(screen.getByRole('textbox', { name: 'Describe the relationship' }), '  My uncle  ');
      await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));

      expect(await screen.findByText('Care recipient added.')).toBeInTheDocument();
      expect(postedRecipient(fetcher)).toEqual({ name: 'Other text', relationship: 'My uncle' });
    });

    it('selects the FATHER chip for a legacy Father value when editing', async () => {
      renderRecipients(`${detail}/edit`, vi.fn().mockResolvedValue(jsonResponse(recipient)));
      expect(await screen.findByRole('radio', { name: 'Father' })).toHaveAttribute('aria-checked', 'true');
    });

    it('selects Other with prefilled text for a legacy relationship when editing', async () => {
      renderRecipients(`${detail}/edit`, vi.fn().mockResolvedValue(jsonResponse({ ...recipient, relationship: 'My uncle' })));
      expect(await screen.findByRole('radio', { name: 'Other' })).toHaveAttribute('aria-checked', 'true');
      expect(await screen.findByRole('textbox', { name: 'Describe the relationship' })).toHaveValue('My uncle');
    });

    it('renders relationship chips in Arabic RTL', async () => {
      renderRecipients(`${root}/new`, vi.fn(), 'ar');
      expect(document.documentElement).toHaveAttribute('dir', 'rtl');
      const group = await screen.findByRole('radiogroup', { name: 'صلة القرابة أو العلاقة (اختياري)' });
      expect(within(group).getByRole('radio', { name: 'الأم' })).toBeInTheDocument();
      await userEvent.click(within(group).getByRole('radio', { name: 'أخرى' }));
      expect(screen.getByRole('textbox', { name: 'صف العلاقة' })).toHaveAttribute('dir', 'auto');
    });
  });

  describe('relationship labels', () => {
    it('renders the English label for a known relationship code', async () => {
      const coded = { ...recipient, relationship: 'MOTHER' };
      const list = renderRecipients(root, vi.fn().mockResolvedValue(jsonResponse([coded])));
      expect(await screen.findByText('Mother')).toBeInTheDocument();
      list.unmount();
      renderRecipients(detail, vi.fn().mockResolvedValue(jsonResponse(coded)));
      expect(await screen.findByText('Mother')).toBeInTheDocument();
    });

    it('renders the Arabic label for a known relationship code', async () => {
      renderRecipients(root, vi.fn().mockResolvedValue(jsonResponse([{ ...recipient, relationship: 'MOTHER' }])), 'ar');
      expect(await screen.findByText('الأم')).toBeInTheDocument();
    });

    it('renders a legacy relationship string unchanged', async () => {
      renderRecipients(root, vi.fn().mockResolvedValue(jsonResponse([{ ...recipient, relationship: 'My uncle' }])));
      expect(await screen.findByText('My uncle')).toBeInTheDocument();
    });

    it('finds recipients by the displayed relationship label', async () => {
      const coded = { ...recipient, relationship: 'MOTHER' };
      const other = { ...recipient, id: 'b2222222-2222-4222-8222-222222222222', name: 'Mona', relationship: 'Aunt', active: true };
      const fillers = Array.from({ length: 4 }, (_, index) => ({ ...recipient, id: `c0000000-0000-4000-8000-00000000000${index}`, name: `Filler ${index}` }));
      renderRecipients(root, vi.fn().mockResolvedValue(jsonResponse([coded, other, ...fillers])));
      await screen.findByRole('link', { name: recipient.name });
      await userEvent.type(screen.getByLabelText('Search name or relationship'), 'Mother');
      expect(screen.getByRole('link', { name: recipient.name })).toBeInTheDocument();
      expect(screen.queryByRole('link', { name: 'Mona' })).not.toBeInTheDocument();
    });
  });

  describe('care details overview', () => {
    it('hides the section when all care-detail fields are empty', async () => {
      const empty = {
        ...recipient,
        dateOfBirth: null,
        allergies: null,
        conditions: null,
        emergencyContactName: null,
        emergencyContactPhone: null,
      };
      const fetcher = recipientDetailFetcher(empty);
      renderRecipients(detail, fetcher);
      await waitFor(() => expect(fetcher).toHaveBeenCalled(), { timeout: 5000 });

      expect(await screen.findByRole('heading', { name: recipient.name }, { timeout: 5000 })).toBeInTheDocument();
      expect(screen.queryByRole('region', { name: 'Care details' })).not.toBeInTheDocument();
    });

    it('renders only populated fields and links active recipients to edit care details', async () => {
      const partial = {
        ...recipient,
        dateOfBirth: '1950-04-12',
        allergies: 'Penicillin\nLatex',
        conditions: '   ',
        emergencyContactName: null,
        emergencyContactPhone: '+20 (100) 123-4567',
      };
      renderRecipients(detail, recipientDetailFetcher(partial));
      const section = await screen.findByRole('region', { name: 'Care details' });

      expect(within(section).getByText('Date of birth')).toBeInTheDocument();
      expect(within(section).getByText('Allergies')).toBeInTheDocument();
      expect(within(section).getByText((_content, element) =>
        element?.tagName === 'DD' && element.textContent === 'Penicillin\nLatex')).toHaveAttribute('dir', 'auto');
      expect(within(section).queryByText('Conditions')).not.toBeInTheDocument();
      expect(within(section).queryByText('Emergency contact name')).not.toBeInTheDocument();
      expect(within(section).getByRole('link', { name: 'Edit care details' })).toHaveAttribute('href', `${detail}/edit`);

      const dateOfBirth = within(section).getByText((_content, element) => element?.tagName === 'TIME');
      expect(dateOfBirth).toHaveAttribute('datetime', '1950-04-12');
      expect(dateOfBirth).toHaveTextContent(new Intl.DateTimeFormat('en', {
        dateStyle: 'medium', timeZone: 'UTC',
      }).format(new Date('1950-04-12T00:00:00.000Z')));
    });

    it('builds the emergency phone link from digits and a leading plus', async () => {
      const phone = '+20 (100) 123-4567';
      renderRecipients(detail, recipientDetailFetcher({ ...recipient, emergencyContactPhone: phone }));
      const section = await screen.findByRole('region', { name: 'Care details' });
      const link = within(section).getByRole('link', { name: phone });

      expect(link).toHaveAttribute('href', 'tel:+201001234567');
      expect(link).toHaveAttribute('dir', 'ltr');
    });

    it('uses automatic RTL direction for Arabic care-detail values', async () => {
      const arabicDetails = {
        ...recipient,
        allergies: 'حساسية البنسلين',
        emergencyContactName: 'ليلى حسن',
      };
      renderRecipients(detail, recipientDetailFetcher(arabicDetails), 'ar');
      const section = await screen.findByRole('region', { name: 'تفاصيل الرعاية' });

      expect(document.documentElement).toHaveAttribute('dir', 'rtl');
      expect(within(section).getByText('حساسية البنسلين')).toHaveAttribute('dir', 'auto');
      expect(within(section).getByText('ليلى حسن')).toHaveAttribute('dir', 'auto');
    });
  });

  describe('today note status', () => {
    const localToday = () => {
      const now = new Date();
      return [now.getFullYear(), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0')].join('-');
    };
    const statusFetcher = (recipients: (typeof recipient)[], notes: 'error' | (typeof historyNote)[] = []) => vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === '/api/recipients') return Promise.resolve(jsonResponse(recipients));
      if (url.pathname === '/api/notes') {
        if (notes === 'error') return Promise.reject(new Error('notes unavailable'));
        return Promise.resolve(jsonResponse(historyPage(notes, 0, 100)));
      }
      return Promise.resolve(jsonResponse([]));
    });

    it('shows Open note for a recipient with today\'s note', async () => {
      const note = { ...historyNote, date: localToday() };
      renderRecipients(root, statusFetcher([recipient], [note]));
      expect(await screen.findByText('Note recorded')).toBeInTheDocument();
      expect(screen.getByRole('link', { name: /Open note/ })).toHaveAttribute('href', `/app/notes/${note.id}`);
    });

    it('shows Record note for a recipient without today\'s note', async () => {
      const today = localToday();
      renderRecipients(root, statusFetcher([recipient]));
      expect(await screen.findByText('No note recorded')).toBeInTheDocument();
      expect(screen.getByRole('link', { name: /Record a note/ }))
        .toHaveAttribute('href', `/app/notes/new?recipientId=${recipient.id}&date=${today}`);
    });

    it('shows the unavailable status when today\'s notes fail to load', async () => {
      renderRecipients(root, statusFetcher([recipient], 'error'));
      expect(await screen.findByRole('link', { name: recipient.name })).toBeInTheDocument();
      expect(screen.getByText('Note status unavailable')).toBeInTheDocument();
    });

    it('leaves archived cards without today status', async () => {
      const archived = { ...recipient, active: false, lifecycleStatus: 'ARCHIVED' as const,
        archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
      renderRecipients(root, statusFetcher([archived], [{ ...historyNote, date: localToday() }]));
      await userEvent.click(await screen.findByRole('button', { name: 'Archived' }));
      expect(await screen.findByRole('link', { name: recipient.name })).toBeInTheDocument();
      expect(screen.queryByText('Note recorded')).not.toBeInTheDocument();
      expect(screen.queryByText('No note recorded')).not.toBeInTheDocument();
      expect(screen.queryByRole('link', { name: /Record a note/ })).not.toBeInTheDocument();
    });

    it('renders today status in Arabic RTL at 320px without overflow', async () => {
      const originalWidth = window.innerWidth;
      Object.defineProperty(window, 'innerWidth', { writable: true, configurable: true, value: 320 });
      try {
        renderRecipients(root, statusFetcher([recipient], [{ ...historyNote, date: localToday() }]), 'ar');
        expect(document.documentElement).toHaveAttribute('dir', 'rtl');
        expect(await screen.findByText('تم تسجيل ملاحظة')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: /فتح الملاحظة/ })).toBeInTheDocument();
        expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(320);
      } finally {
        Object.defineProperty(window, 'innerWidth', { writable: true, configurable: true, value: originalWidth });
      }
    });
  });

  describe('overview today action', () => {
    const localToday = () => {
      const now = new Date();
      return [now.getFullYear(), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0')].join('-');
    };
    const overviewFetcher = (notes: 'error' | (typeof historyNote)[] = []) => vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(recipient));
      if (url.pathname === '/api/notes') {
        if (notes === 'error') return Promise.reject(new Error('notes unavailable'));
        return Promise.resolve(jsonResponse(historyPage(notes, 0, 100)));
      }
      return Promise.resolve(jsonResponse([]));
    });
    const oldCreateHref = `/app/notes/new?recipientId=${recipient.id}`;
    const oldCreateGone = () => expect(screen.queryAllByRole('link')
      .some((link) => link.getAttribute('href') === oldCreateHref)).toBe(false);

    it('shows Record today\'s note as a primary button when no note exists', async () => {
      const today = localToday();
      renderRecipients(detail, overviewFetcher());
      const action = await screen.findByRole('link', { name: "Record today's note" });
      expect(action).toHaveClass('button--primary');
      expect(action).toHaveAttribute('href', `/app/notes/new?recipientId=${recipient.id}&date=${today}`);
      oldCreateGone();
    });

    it('shows Open today\'s note when a note exists today', async () => {
      const note = { ...historyNote, recipientId: recipient.id, date: localToday() };
      renderRecipients(detail, overviewFetcher([note]));
      expect(await screen.findByText('Note recorded')).toBeInTheDocument();
      expect(screen.getByRole('link', { name: "Open today's note" })).toHaveAttribute('href', `/app/notes/${note.id}`);
      oldCreateGone();
    });

    it('falls back to Record today\'s note when the status fails to load', async () => {
      renderRecipients(detail, overviewFetcher('error'));
      expect(await screen.findByText('Note status unavailable')).toBeInTheDocument();
      expect(screen.getByRole('link', { name: "Record today's note" })).toHaveClass('button--primary');
      oldCreateGone();
    });

    it('does not show the today action for archived recipients', async () => {
      const archived = { ...recipient, active: false, lifecycleStatus: 'ARCHIVED' as const,
        archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
      const fetcher = vi.fn().mockImplementation((path: string) => {
        const url = new URL(path, 'http://localhost');
        if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(archived));
        if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
        return Promise.resolve(jsonResponse([]));
      });
      renderRecipients(detail, fetcher);
      expect(await screen.findByRole('heading', { name: recipient.name })).toBeInTheDocument();
      await waitFor(() => expect(fetcher.mock.calls.some(([path]) => new URL(path, 'http://localhost').pathname === '/api/notes')).toBe(false));
      expect(screen.queryByRole('link', { name: "Record today's note" })).not.toBeInTheDocument();
      expect(screen.queryByRole('link', { name: "Open today's note" })).not.toBeInTheDocument();
      oldCreateGone();
    });
  });

  describe('simplified overview actions', () => {
    const manageFetcher = (value: typeof recipient) => vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(value));
      if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
      return Promise.resolve(jsonResponse([]));
    });

    it('orders the primary today action before a secondary Edit and drops History', async () => {
      renderRecipients(detail, manageFetcher(recipient));
      const todayAction = await screen.findByRole('link', { name: "Record today's note" });
      const edit = screen.getByRole('link', { name: 'Edit recipient' });
      expect(todayAction.compareDocumentPosition(edit) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
      expect(edit).toHaveClass('button--secondary');
      expect(screen.queryByRole('link', { name: 'Notes history' })).not.toBeInTheDocument();
    });

    it('archives the recipient from the manage section', async () => {
      const archived = { ...recipient, active: false, lifecycleStatus: 'ARCHIVED' as const,
        archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
      const fetcher = vi.fn().mockImplementation((path: string) => {
        const url = new URL(path, 'http://localhost');
        if (url.pathname === `/api/recipients/${recipient.id}/archive`) return Promise.resolve(jsonResponse(archived));
        if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(recipient));
        if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
        return Promise.resolve(jsonResponse([]));
      });
      renderRecipients(detail, fetcher);
      const manage = await screen.findByRole('region', { name: 'Manage recipient' });
      await userEvent.click(within(manage).getByRole('button', { name: 'Archive recipient' }));
      await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archive recipient' }));
      expect(await screen.findByText('Archived')).toBeInTheDocument();
      expect(screen.getByText('Recipient archived. Their history has been preserved.')).toBeInTheDocument();
    });

    it('restores an archived recipient from the manage section', async () => {
      const archived = { ...recipient, active: false, lifecycleStatus: 'ARCHIVED' as const,
        archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
      const restored = { ...recipient, restoredAt: '2026-10-16T10:00:00Z', restoredBy: 'b1111111-1111-4111-8111-111111111111' };
      const fetcher = vi.fn().mockImplementation((path: string) => {
        const url = new URL(path, 'http://localhost');
        if (url.pathname === `/api/recipients/${recipient.id}/restore`) return Promise.resolve(jsonResponse(restored));
        if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(archived));
        if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
        return Promise.resolve(jsonResponse([]));
      });
      renderRecipients(detail, fetcher);
      const manage = await screen.findByRole('region', { name: 'Manage recipient' });
      await userEvent.click(within(manage).getByRole('button', { name: 'Restore recipient' }));
      await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Restore recipient' }));
      expect(await screen.findByText('Active')).toBeInTheDocument();
      expect(screen.getByText('Recipient restored to active status.')).toBeInTheDocument();
    });
  });

  describe('next steps panel', () => {
    const panelFetcher = () => vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(recipient));
      if (url.pathname === '/api/recipients') return Promise.resolve(jsonResponse([recipient]));
      if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
      return Promise.resolve(jsonResponse([]));
    });

    it('shows the next steps panel after creating a recipient', async () => {
      renderRecipients(detail, panelFetcher(), 'en', true, { recipientNotice: 'created' });
      const panel = await screen.findByRole('region', { name: 'Next steps' });
      expect(within(panel).getByRole('link', { name: 'Add care details' })).toHaveAttribute('href', `${detail}/edit`);
      expect(within(panel).getByRole('link', { name: 'Add medications' })).toBeInTheDocument();
      expect(within(panel).getByRole('link', { name: "Record first note" })).toBeInTheDocument();
    });

    it('does not show the panel on a normal visit without the notice', async () => {
      renderRecipients(detail, panelFetcher());
      expect(await screen.findByRole('link', { name: "Record today's note" })).toBeInTheDocument();
      expect(screen.queryByRole('region', { name: 'Next steps' })).not.toBeInTheDocument();
    });

    it('focuses the medications section from the panel', async () => {
      renderRecipients(detail, panelFetcher(), 'en', true, { recipientNotice: 'created' });
      const panel = await screen.findByRole('region', { name: 'Next steps' });
      await userEvent.click(within(panel).getByRole('link', { name: 'Add medications' }));
      const section = document.getElementById('recipient-medications');
      expect(section).not.toBeNull();
      expect(section).toHaveFocus();
    });

    it('opens the create-note modal from the panel', async () => {
      renderRecipients(detail, panelFetcher(), 'en', true, { recipientNotice: 'created' });
      const panel = await screen.findByRole('region', { name: 'Next steps' });
      await userEvent.click(within(panel).getByRole('link', { name: "Record first note" }));
      expect(await screen.findByRole('dialog')).toBeInTheDocument();
    });
  });

  describe('conditional search box', () => {
    const searchRecipients = (count: number) => Array.from({ length: count }, (_, index) => ({
      ...recipient,
      id: `a0000000-0000-4000-8000-${String(index).padStart(12, '0')}`,
      name: `Recipient ${index + 1}`,
    }));
    const searchFetcher = (count: number) => vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === '/api/recipients') return Promise.resolve(jsonResponse(searchRecipients(count)));
      if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
      return Promise.resolve(jsonResponse([]));
    });

    it('hides the search box for five recipients', async () => {
      renderRecipients(root, searchFetcher(5));
      expect(await screen.findByRole('link', { name: 'Recipient 5' })).toBeInTheDocument();
      expect(screen.queryByLabelText('Search name or relationship')).not.toBeInTheDocument();
    });

    it('shows the search box for six recipients', async () => {
      renderRecipients(root, searchFetcher(6));
      expect(await screen.findByLabelText('Search name or relationship')).toBeInTheDocument();
    });

    it('resets the query when the list drops to five and clears it when it grows back', async () => {
      let count = 6;
      const fetcher = () => vi.fn().mockImplementation((path: string) => {
        const url = new URL(path, 'http://localhost');
        if (url.pathname === '/api/recipients') return Promise.resolve(jsonResponse(searchRecipients(count)));
        if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
        return Promise.resolve(jsonResponse([]));
      });
      const view = renderRecipients(root, fetcher());
      const search = await screen.findByLabelText('Search name or relationship');
      await userEvent.type(search, 'Recipient 1');
      expect(search).toHaveValue('Recipient 1');
      view.unmount();

      count = 5;
      renderRecipients(root, fetcher());
      await waitFor(() => expect(screen.queryByLabelText('Search name or relationship')).not.toBeInTheDocument());
      expect(screen.getByRole('link', { name: 'Recipient 5' })).toBeInTheDocument();

      count = 6;
      renderRecipients(root, fetcher());
      const restored = await screen.findByLabelText('Search name or relationship');
      expect(restored).toHaveValue('');
    });
  });

  describe('lifecycle filters', () => {
    const archivedExtra = { ...recipient, id: 'a0000000-0000-4000-8000-0000000000aa', name: 'Archived Extra',
      active: false, lifecycleStatus: 'ARCHIVED' as const,
      archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
    const reviewExtra = { ...recipient, id: 'a0000000-0000-4000-8000-0000000000bb', name: 'Review Extra',
      active: false, lifecycleStatus: 'LEGACY_INACTIVE_REVIEW' as const };
    const activeExtra = { ...recipient, id: 'a0000000-0000-4000-8000-0000000000cc', name: 'Active Extra' };
    const filterFetcher = (list: (typeof recipient)[]) => vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === '/api/recipients') return Promise.resolve(jsonResponse(list));
      if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
      return Promise.resolve(jsonResponse([]));
    });

    it('shows no filter group when every recipient is active', async () => {
      renderRecipients(root, filterFetcher([recipient, activeExtra]));
      expect(await screen.findByRole('link', { name: 'Active Extra' })).toBeInTheDocument();
      expect(screen.queryByRole('group', { name: 'Filter recipients by lifecycle status' })).not.toBeInTheDocument();
    });

    it('shows only populated filters with counts on a mixed list', async () => {
      renderRecipients(root, filterFetcher([recipient, activeExtra, archivedExtra, reviewExtra]));
      const group = await screen.findByRole('group', { name: 'Filter recipients by lifecycle status' });
      expect(within(group).getByRole('button', { name: 'Active' })).toHaveTextContent('Active (2)');
      expect(within(group).getByRole('button', { name: 'Archived' })).toHaveTextContent('Archived (1)');
      expect(within(group).getByRole('button', { name: 'Needs review' })).toHaveTextContent('Needs review (1)');
      expect(within(group).getAllByRole('button')).toHaveLength(3);
      expect(within(group).getByRole('button', { name: 'Active' })).toHaveAttribute('aria-pressed', 'true');
    });

    it('removes the Archived filter and falls back to Active when the last archived recipient is restored', async () => {
      const view = renderRecipients(root, filterFetcher([recipient, activeExtra, archivedExtra]));
      await userEvent.click(await screen.findByRole('button', { name: 'Archived' }));
      expect(await screen.findByRole('link', { name: 'Archived Extra' })).toBeInTheDocument();
      view.unmount();

      renderRecipients(root, filterFetcher([recipient, activeExtra]));
      expect(await screen.findByRole('link', { name: 'Active Extra' })).toBeInTheDocument();
      expect(screen.queryByRole('link', { name: 'Archived Extra' })).not.toBeInTheDocument();
      expect(screen.queryByRole('group', { name: 'Filter recipients by lifecycle status' })).not.toBeInTheDocument();
    });
  });

  it('keeps archived recipient fields read only', async () => {
    const archived = { ...recipient, active: false, lifecycleStatus: 'ARCHIVED' as const,
      archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
    renderRecipients(detail, vi.fn().mockResolvedValue(jsonResponse(archived)));

    expect(await screen.findByRole('heading', { name: recipient.name })).toBeInTheDocument();
    expect(screen.getByText('Father')).toBeInTheDocument();
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Edit recipient' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Restore recipient' })).toBeInTheDocument();
  });

  it('shows archive and restore success messages', async () => {
    const archived = { ...recipient, active: false, lifecycleStatus: 'ARCHIVED' as const,
      archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
    const restored = { ...recipient, restoredAt: '2026-10-16T10:00:00Z', restoredBy: 'b1111111-1111-4111-8111-111111111111' };
    const fetcher = vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === `/api/recipients/${recipient.id}/archive`) return Promise.resolve(jsonResponse(archived));
      if (url.pathname === `/api/recipients/${recipient.id}/restore`) return Promise.resolve(jsonResponse(restored));
      if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(recipient));
      if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
      return Promise.resolve(jsonResponse([]));
    });
    renderRecipients(detail, fetcher);

    await userEvent.click(await screen.findByRole('button', { name: 'Archive recipient' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archive recipient' }));
    expect(await screen.findByText('Archived')).toBeInTheDocument();
    expect(screen.getByText('Recipient archived. Their history has been preserved.')).toBeVisible();
    expect(screen.getByRole('button', { name: 'Restore recipient' })).toBeVisible();

    await userEvent.click(screen.getByRole('button', { name: 'Restore recipient' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Restore recipient' }));
    expect(await screen.findByText('Active')).toBeInTheDocument();
    expect(screen.getByText('Recipient restored to active status.')).toBeVisible();
    expect(screen.getByRole('link', { name: 'Edit recipient' })).toBeVisible();
  });

  it('reloads an archived conflict without silently losing the entered values', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(recipient)).mockResolvedValueOnce(jsonResponse({ code: 'INACTIVE_RECIPIENT' }, 409)).mockResolvedValueOnce(jsonResponse({ ...recipient, active: false, lifecycleStatus: 'ARCHIVED', archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' }));
    renderRecipients(`${detail}/edit`, fetcher);
    const name = await screen.findByLabelText('Name');
    await userEvent.clear(name);
    await userEvent.type(name, 'Unsaved name');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('This recipient’s lifecycle changed or requires review');
    expect(name).toHaveValue('Unsaved name');
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Reload recipient details' }));
    expect(await screen.findByText('Archived')).toBeInTheDocument();
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
    expect(screen.getByText(/This recipient is archived and read-only/)).toBeInTheDocument();
  });

  it('keeps legacy-review detail/edit routes read-only with no lifecycle transition', async () => {
    renderRecipients(`${detail}/edit`, vi.fn().mockResolvedValue(jsonResponse({ ...recipient, active: false, lifecycleStatus: 'LEGACY_INACTIVE_REVIEW' })));
    expect(await screen.findByText('Needs review')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Archive|Delete|Restore/ })).not.toBeInTheDocument();
  });

  it.each([403, 404])('uses a safe unavailable message for HTTP %i', async (status) => {
    renderRecipients(detail, vi.fn().mockResolvedValue(jsonResponse({ message: 'belongs to other caregiver' }, status)));
    expect(await screen.findByRole('alert')).toHaveTextContent('This recipient is unavailable.');
    expect(screen.queryByText('belongs to other caregiver')).not.toBeInTheDocument();
  });

  it('shows detail loading and retries an unavailable response without exposing diagnostics', async () => {
    let resolve!: (response: Response) => void;
    const archived = { ...recipient, active: false, lifecycleStatus: 'ARCHIVED' as const,
      archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
    const fetcher = vi.fn()
      .mockImplementationOnce(() => new Promise<Response>((done) => { resolve = done; }))
      .mockResolvedValueOnce(jsonResponse(archived)).mockResolvedValueOnce(jsonResponse([]));
    renderRecipients(detail, fetcher);

    expect(screen.getByText('Loading recipient details…')).toBeInTheDocument();
    resolve(jsonResponse({ message: 'private detail diagnostics' }, 500));
    expect(await screen.findByRole('alert')).toHaveTextContent('We couldn’t complete this request.');
    expect(screen.queryByText('private detail diagnostics')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('heading', { name: recipient.name })).toBeInTheDocument();
    expect(screen.getByText('Archived')).toBeInTheDocument();
    expect(fetcher).toHaveBeenCalledTimes(4);
  });

  it('rejects malformed route IDs without calling the backend', async () => {
    const fetcher = vi.fn();
    renderRecipients(`${root}/not-a-uuid`, fetcher);
    expect(await screen.findByRole('alert')).toHaveTextContent('This recipient is unavailable.');
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('requires confirmation before archive and restores focus when cancelled', async () => {
    const fetcher = vi.fn().mockImplementation((path: string) => Promise.resolve(
      new URL(path, 'http://localhost').pathname === '/api/notes'
        ? jsonResponse(historyPage([], 0, 100))
        : jsonResponse(path.endsWith('/medications') ? [] : recipient)));
    renderRecipients(detail, fetcher);
    const trigger = await screen.findByRole('button', { name: 'Archive recipient' });
    await userEvent.click(trigger);
    const dialog = screen.getByRole('dialog', { name: 'Archive this recipient?' });
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toHaveFocus();
    expect(dialog).toHaveTextContent('Existing notes, corrections, plans, and history will be preserved.');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
    expect(fetcher).toHaveBeenCalledTimes(4);
  });

  it('archives the recipient through the API and makes the detail read-only', async () => {
    const archived = { ...recipient, active: false, lifecycleStatus: 'ARCHIVED' as const, archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
    const fetcher = vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === `/api/recipients/${recipient.id}/archive`) return Promise.resolve(jsonResponse(archived));
      if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(recipient));
      if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
      return Promise.resolve(jsonResponse([]));
    });
    renderRecipients(detail, fetcher);
    await userEvent.click(await screen.findByRole('button', { name: 'Archive recipient' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archive recipient' }));
    expect(await screen.findByText('Archived')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Edit recipient' })).not.toBeInTheDocument();
    expect(screen.getByText('Recipient archived. Their history has been preserved.')).toHaveFocus();
    const archiveCall = fetcher.mock.calls.find(([path]) => new URL(path, 'http://localhost').pathname === `${detail.replace('/app/', '/api/')}/archive`);
    expect(archiveCall?.[1].method).toBe('PATCH');
    expect(archiveCall?.[1].body).toBeUndefined();
  });

  it('restores an archived recipient to active status', async () => {
    const archived = { ...recipient, active: false, lifecycleStatus: 'ARCHIVED' as const, archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
    const restored = { ...recipient, restoredAt: '2026-10-16T10:00:00Z', restoredBy: 'b1111111-1111-4111-8111-111111111111' };
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(archived)).mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse(restored));
    renderRecipients(detail, fetcher);
    await userEvent.click(await screen.findByRole('button', { name: 'Restore recipient' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Restore recipient' }));
    expect(await screen.findByText('Active')).toBeInTheDocument();
    expect(screen.getByText('Recipient restored to active status.')).toHaveFocus();
    expect(fetcher.mock.calls[2][0]).toBe(`${detail.replace('/app/', '/api/')}/restore`);
  });

  it('keeps the archive dialog open after a failed request and allows retry', async () => {
    const archived = { ...recipient, active: false, lifecycleStatus: 'ARCHIVED' as const, archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
    let archiveAttempts = 0;
    const fetcher = vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === `/api/recipients/${recipient.id}/archive`) {
        archiveAttempts += 1;
        return archiveAttempts === 1 ? Promise.reject(new TypeError('Failed to fetch')) : Promise.resolve(jsonResponse(archived));
      }
      if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(recipient));
      if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
      return Promise.resolve(jsonResponse([]));
    });
    renderRecipients(detail, fetcher);
    await userEvent.click(await screen.findByRole('button', { name: 'Archive recipient' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archive recipient' }));
    expect(await screen.findByRole('alert')).toHaveFocus();
    expect(screen.getByRole('dialog')).toHaveTextContent('We couldn’t connect.');
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archive recipient' }));
    expect(await screen.findByText('Archived')).toBeInTheDocument();
  });

  it('keeps legacy inactive records in a review-only state', async () => {
    const legacy = { ...recipient, active: false, lifecycleStatus: 'LEGACY_INACTIVE_REVIEW' as const };
    renderRecipients(detail, vi.fn().mockResolvedValue(jsonResponse(legacy)));
    expect(await screen.findByText('Needs review')).toBeInTheDocument();
    expect(screen.getByText(/historical meaning is unknown/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Restore recipient' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Archive recipient' })).not.toBeInTheDocument();
  });

  it('shows review resolution actions only for legacy-review recipients', async () => {
    const legacy = { ...recipient, active: false, lifecycleStatus: 'LEGACY_INACTIVE_REVIEW' as const };
    renderRecipients(detail, vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(legacy));
      return Promise.resolve(jsonResponse([]));
    }));
    expect(await screen.findByRole('button', { name: 'Mark as archived' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Mark as active' })).toBeInTheDocument();
  });

  it.each([
    ['active', recipient],
    ['archived', { ...recipient, active: false, lifecycleStatus: 'ARCHIVED' as const,
      archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' }],
  ])('hides review resolution actions for %s recipients', async (_label, value) => {
    renderRecipients(detail, vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(value));
      if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
      return Promise.resolve(jsonResponse([]));
    }));
    await screen.findByRole('heading', { name: recipient.name });
    expect(screen.queryByRole('button', { name: 'Mark as archived' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Mark as active' })).not.toBeInTheDocument();
  });

  it('marks a legacy-review recipient as archived through the API', async () => {
    const legacy = { ...recipient, active: false, lifecycleStatus: 'LEGACY_INACTIVE_REVIEW' as const };
    const archived = { ...recipient, active: false, lifecycleStatus: 'ARCHIVED' as const,
      archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
    const fetcher = vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === `/api/recipients/${recipient.id}/resolve-review`) return Promise.resolve(jsonResponse(archived));
      if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(legacy));
      return Promise.resolve(jsonResponse([]));
    });
    renderRecipients(detail, fetcher);

    await userEvent.click(await screen.findByRole('button', { name: 'Mark as archived' }));
    const dialog = screen.getByRole('dialog', { name: 'Resolve this recipient' });
    expect(dialog).toHaveTextContent('The recipient is archived. All notes are kept and it can be restored later.');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Mark as archived' }));

    expect(await screen.findByText('Archived')).toBeInTheDocument();
    expect(screen.getByText('Recipient archived. Their history has been preserved.')).toBeVisible();
    const resolveCall = fetcher.mock.calls.find(([path]) =>
      new URL(path, 'http://localhost').pathname === `/api/recipients/${recipient.id}/resolve-review`);
    expect(resolveCall?.[1].method).toBe('PATCH');
    expect(resolveCall?.[1].body).toBe(JSON.stringify({ resolution: 'ARCHIVED' }));
  });

  it('marks a legacy-review recipient as active through the API', async () => {
    const legacy = { ...recipient, active: false, lifecycleStatus: 'LEGACY_INACTIVE_REVIEW' as const };
    const restored = { ...recipient, restoredAt: '2026-10-16T10:00:00Z', restoredBy: 'b1111111-1111-4111-8111-111111111111' };
    const fetcher = vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === `/api/recipients/${recipient.id}/resolve-review`) return Promise.resolve(jsonResponse(restored));
      if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(legacy));
      if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
      return Promise.resolve(jsonResponse([]));
    });
    renderRecipients(detail, fetcher);

    await userEvent.click(await screen.findByRole('button', { name: 'Mark as active' }));
    const dialog = screen.getByRole('dialog', { name: 'Resolve this recipient' });
    expect(dialog).toHaveTextContent('The recipient becomes active again and you can record new notes.');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Mark as active' }));

    expect(await screen.findByText('Active')).toBeInTheDocument();
    expect(screen.getByText('Recipient restored to active status.')).toBeVisible();
    const resolveCall = fetcher.mock.calls.find(([path]) =>
      new URL(path, 'http://localhost').pathname === `/api/recipients/${recipient.id}/resolve-review`);
    expect(resolveCall?.[1].method).toBe('PATCH');
    expect(resolveCall?.[1].body).toBe(JSON.stringify({ resolution: 'ACTIVE' }));
  });

  it('keeps the resolve dialog open with a mapped error after a failed request', async () => {
    const legacy = { ...recipient, active: false, lifecycleStatus: 'LEGACY_INACTIVE_REVIEW' as const };
    const fetcher = vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === `/api/recipients/${recipient.id}/resolve-review`) {
        return Promise.resolve(jsonResponse({ message: 'private diagnostics' }, 500));
      }
      if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(legacy));
      return Promise.resolve(jsonResponse([]));
    });
    renderRecipients(detail, fetcher);

    await userEvent.click(await screen.findByRole('button', { name: 'Mark as archived' }));
    const dialog = screen.getByRole('dialog', { name: 'Resolve this recipient' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Mark as archived' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('We couldn’t complete this request.');
    expect(screen.queryByText('private diagnostics')).not.toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('renders review resolution actions and dialog in Arabic with RTL', async () => {
    const legacy = { ...recipient, active: false, lifecycleStatus: 'LEGACY_INACTIVE_REVIEW' as const };
    renderRecipients(detail, vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(legacy));
      return Promise.resolve(jsonResponse([]));
    }), 'ar');

    expect(document.documentElement).toHaveAttribute('dir', 'rtl');
    await userEvent.click(await screen.findByRole('button', { name: 'اعتباره مؤرشفًا' }));
    const dialog = screen.getByRole('dialog', { name: 'حسم حالة هذا المستفيد' });
    expect(dialog).toHaveTextContent('سيتم أرشفة المستفيد. ستبقى جميع الملاحظات محفوظة ويمكن استعادته لاحقًا.');
    expect(within(dialog).getByRole('button', { name: 'اعتباره مؤرشفًا' })).toBeInTheDocument();
  });

  it('expires rejected sessions and removes private UI', async () => {
    renderRecipients(root, vi.fn().mockResolvedValue(jsonResponse({ code: 'UNAUTHORIZED' }, 401)));
    expect(await screen.findByRole('heading', { name: 'Caregiver sign in' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: recipient.name })).not.toBeInTheDocument();
    expect(sessionStorage.getItem('caregiver-auth-session-v1')).toBeNull();
  });

  it('aborts abandoned queries and ignores late private results', async () => {
    let resolve!: (response: Response) => void;
    const fetcher = vi.fn().mockImplementation(() => new Promise<Response>((done) => { resolve = done; }));
    renderRecipients(detail, fetcher);
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    expect(fetcher.mock.calls[0][1].signal.aborted).toBe(true);
    resolve(jsonResponse(recipient));
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Caregiver sign in' })).toBeInTheDocument());
    expect(screen.queryByText(recipient.name)).not.toBeInTheDocument();
  });

  it('prevents duplicate create requests and aborts a pending save on cancellation', async () => {
    const fetcher = vi.fn().mockImplementation((path: string, options: RequestInit = {}) =>
      path === '/api/recipients' && options.method === 'POST'
        ? new Promise(() => {})
        : Promise.resolve(jsonResponse([])));
    renderRecipients(`${root}/new`, fetcher);
    await userEvent.type(screen.getByLabelText('Name'), recipient.name);
    await userEvent.dblClick(screen.getByRole('button', { name: 'Add care recipient' }));
    expect(fetcher.mock.calls.filter(([, options]) => options.method === 'POST')).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Saving…' })).toBeDisabled();
    await userEvent.click(screen.getByRole('link', { name: 'Cancel' }));
    const post = fetcher.mock.calls.find(([, options]) => options.method === 'POST');
    expect(post?.[1].signal.aborted).toBe(true);
  });

  it('renders Arabic labels and RTL, and preserves entered values when changing language', async () => {
    renderRecipients(`${root}/new`, vi.fn(), 'ar');
    expect(document.documentElement.dir).toBe('rtl');
    await userEvent.click(screen.getByRole('button', { name: 'إضافة مستفيد من الرعاية' }));
    expect(screen.getByRole('alert')).toHaveTextContent('أدخل اسم المستفيد.');
    await userEvent.type(screen.getByLabelText('الاسم'), 'سمير');
    await userEvent.click(screen.getByRole('button', { name: 'Switch to English' }));
    expect(screen.getByLabelText('Name')).toHaveValue('سمير');
    expect(document.documentElement.dir).toBe('ltr');
    expect(screen.getByLabelText('Name')).toHaveAttribute('dir', 'auto');
  });

  it('loads medications for the selected recipient only', async () => {
    const otherRecipient = 'b2222222-2222-4222-8222-222222222222';
    const medication = medicationFixture(recipient.id, 'Vitamin D');
    const otherMedication = medicationFixture(otherRecipient, 'Private medicine');
    const fetcher = vi.fn().mockImplementation((path: string) => path === '/api/recipients'
      ? Promise.resolve(jsonResponse([recipient]))
      : path === `${root}/${recipient.id}`.replace('/app/', '/api/')
        ? Promise.resolve(jsonResponse(recipient))
        : Promise.resolve(jsonResponse([medication])));
    renderRecipients(detail, fetcher);

    expect(await screen.findByText('Vitamin D')).toBeInTheDocument();
    expect(screen.queryByText(otherMedication.displayName)).not.toBeInTheDocument();
    expect(fetcher.mock.calls.some(([path]) => path === `/api/recipients/${recipient.id}/medications`)).toBe(true);
    expect(fetcher.mock.calls.some(([path]) => path === `/api/recipients/${otherRecipient}/medications`)).toBe(false);
  });

  it('creates a medication with exact mixed Arabic and English text', async () => {
    const value = medicationFixture(recipient.id, 'Vitamin D');
    const input = { displayName: 'دواء صباحي / Morning 💊', doseText: ' الجرعة ٥ ملغ — 5 mg ', scheduleText: 'كل يوم at 08:30\nبعد الإفطار' };
    let medicationReads = 0;
    const fetcher = vi.fn().mockImplementation((path: string, options: RequestInit = {}) => {
      if (path.endsWith('/intake-events')) return Promise.resolve(jsonResponse([]));
      if (path === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(recipient));
      if (path === `/api/recipients/${recipient.id}/medications` && options.method === 'GET') {
        medicationReads += 1;
        return Promise.resolve(jsonResponse(medicationReads === 1 ? [] : [{ ...value, ...input }]));
      }
      if (options.method === 'POST') return Promise.resolve(jsonResponse({ ...value, ...input }, 201));
      throw new Error(`Unexpected request: ${options.method} ${path}`);
    });
    renderRecipients(detail, fetcher);
    await screen.findByText('No medication records yet');
    await userEvent.click(screen.getByRole('button', { name: 'Add medication' }));
    await userEvent.type(screen.getByLabelText('Medication name'), input.displayName);
    await userEvent.type(screen.getByLabelText('Dose'), input.doseText);
    await userEvent.type(screen.getByLabelText('Schedule'), input.scheduleText);
    await userEvent.click(screen.getByRole('button', { name: 'Save medication' }));

    expect(await screen.findByText(input.displayName)).toBeInTheDocument();
    const displayedSchedule = screen.getByText((_, element) => element?.tagName === 'BDI' && element.textContent === input.scheduleText);
    expect(displayedSchedule).toBeInTheDocument();
    const post = fetcher.mock.calls.find(([, options]) => options.method === 'POST');
    expect(post?.[0]).toBe(`/api/recipients/${recipient.id}/medications`);
    expect(JSON.parse(post?.[1].body)).toEqual(input);
  });

  it('updates medication using PUT and preserves the exact edited text', async () => {
    const initial = medicationFixture(recipient.id, 'Old name');
    const updated = { ...initial, displayName: 'دواء جديد / New 💊', doseText: ' ٢ ملغ (2 mg) ', scheduleText: 'ليلًا — at night' };
    let medicationReads = 0;
    const fetcher = vi.fn().mockImplementation((path: string, options: RequestInit = {}) => {
      if (path.endsWith('/intake-events')) return Promise.resolve(jsonResponse([]));
      if (path === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(recipient));
      if (path === `/api/recipients/${recipient.id}/medications` && options.method === 'GET') {
        medicationReads += 1;
        return Promise.resolve(jsonResponse(medicationReads === 1 ? [initial] : [updated]));
      }
      if (options.method === 'PUT') return Promise.resolve(jsonResponse(updated));
      throw new Error(`Unexpected request: ${options.method} ${path}`);
    });
    renderRecipients(detail, fetcher);
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    await userEvent.clear(screen.getByLabelText('Medication name'));
    await userEvent.type(screen.getByLabelText('Medication name'), updated.displayName);
    await userEvent.clear(screen.getByLabelText('Dose'));
    await userEvent.type(screen.getByLabelText('Dose'), updated.doseText);
    await userEvent.clear(screen.getByLabelText('Schedule'));
    await userEvent.type(screen.getByLabelText('Schedule'), updated.scheduleText);
    await userEvent.click(screen.getByRole('button', { name: 'Save medication' }));

    expect(await screen.findByText(updated.displayName)).toBeInTheDocument();
    const put = fetcher.mock.calls.find(([, options]) => options.method === 'PUT');
    expect(put?.[0]).toBe(`/api/recipients/${recipient.id}/medications/${initial.id}`);
    expect(JSON.parse(put?.[1].body)).toEqual({ displayName: updated.displayName, doseText: updated.doseText, scheduleText: updated.scheduleText });
  });

  it('archives medication through PATCH and keeps its historical text visible', async () => {
    const active = medicationFixture(recipient.id, 'Long-term record');
    const archived = { ...active, active: false };
    let medicationReads = 0;
    const fetcher = vi.fn().mockImplementation((path: string, options: RequestInit = {}) => {
      if (path.endsWith('/intake-events')) return Promise.resolve(jsonResponse([]));
      if (path === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(recipient));
      if (path === `/api/recipients/${recipient.id}/medications` && options.method === 'GET') {
        medicationReads += 1;
        return Promise.resolve(jsonResponse(medicationReads === 1 ? [active] : [archived]));
      }
      if (options.method === 'PATCH') return Promise.resolve(jsonResponse(archived));
      throw new Error(`Unexpected request: ${options.method} ${path}`);
    });
    renderRecipients(detail, fetcher);
    expect(await screen.findByText('Long-term record')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));

    expect(await screen.findByText('Archived', { selector: 'span' })).toBeInTheDocument();
    expect(screen.getByText('Long-term record')).toBeInTheDocument();
    expect(screen.getByText(archived.doseText)).toBeInTheDocument();
    const patch = fetcher.mock.calls.find(([, options]) => options.method === 'PATCH');
    expect(patch?.[0]).toBe(`/api/recipients/${recipient.id}/medications/${active.id}/archive`);
    expect(patch?.[1].body).toBeUndefined();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
  });

  it('keeps loading, empty, error and retry states distinct', async () => {
    const pendingResolvers: Array<(response: Response) => void> = [];
    const pending = vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(recipient));
      if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
      return new Promise<Response>((done) => { pendingResolvers.push(done); });
    });
    const loadingView = renderRecipients(detail, pending);
    await waitFor(() => expect(pending).toHaveBeenCalledTimes(4));
    expect(screen.getByText('Loading medication records…')).toBeInTheDocument();
    pendingResolvers.forEach((done) => done(jsonResponse([])));
    expect(await screen.findByRole('heading', { name: 'Medications' })).toBeInTheDocument();
    loadingView.unmount();

    const emptyFetcher = vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(recipient));
      if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
      return Promise.resolve(jsonResponse([]));
    });
    const emptyView = renderRecipients(detail, emptyFetcher);
    expect(await screen.findByText('No medication records yet')).toBeInTheDocument();
    expect(screen.getByText(/does not indicate whether medication is used/)).toBeInTheDocument();
    emptyView.unmount();

    let medicationReads = 0;
    const errorFetcher = vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(recipient));
      if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
      if (url.pathname.endsWith('/medications')) {
        medicationReads += 1;
        return medicationReads === 1
          ? Promise.resolve(jsonResponse({ message: 'private details' }, 500))
          : Promise.resolve(jsonResponse([medicationFixture(recipient.id, 'Recovered')]));
      }
      return Promise.resolve(jsonResponse([]));
    });
    renderRecipients(detail, errorFetcher);
    expect(await screen.findByRole('alert')).toHaveTextContent('We couldn’t complete this medication request.');
    expect(screen.queryByText('private details')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('Recovered')).toBeInTheDocument();
  });

  it('shows archived recipient medication history read-only', async () => {
    const archivedRecipient = { ...recipient, active: false, lifecycleStatus: 'ARCHIVED' as const,
      archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
    const medication = medicationFixture(recipient.id, 'Archived recipient record');
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(archivedRecipient)).mockResolvedValueOnce(jsonResponse([medication]));
    renderRecipients(detail, fetcher);
    expect(await screen.findByText('Archived recipient record')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add medication' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Archive' })).not.toBeInTheDocument();
  });

  it('keeps legacy inactive recipients read-only for medication management', async () => {
    const legacy = { ...recipient, active: false, lifecycleStatus: 'LEGACY_INACTIVE_REVIEW' as const };
    const medication = medicationFixture(recipient.id, 'Legacy medication history');
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(legacy)).mockResolvedValueOnce(jsonResponse([medication]));
    renderRecipients(detail, fetcher);
    expect(await screen.findByText('Legacy medication history')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add medication' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Archive' })).not.toBeInTheDocument();
  });

  it('does not offer edit or archive controls for an archived medication', async () => {
    const archived = { ...medicationFixture(recipient.id, 'Old medication'), active: false };
    renderRecipients(detail, vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(recipient));
      if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
      return Promise.resolve(jsonResponse([archived]));
    }));
    expect(await screen.findByText('Old medication')).toBeInTheDocument();
    expect(screen.getByText('Archived', { selector: 'span' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Archive' })).not.toBeInTheDocument();
  });

  it('prevents duplicate medication submissions and preserves input after failure', async () => {
    let failCreate!: (response: Response) => void;
    const fetcher = vi.fn().mockImplementation((path: string, options: RequestInit = {}) => {
      if (path === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(recipient));
      if (path === `/api/recipients/${recipient.id}/medications` && options.method === 'GET') return Promise.resolve(jsonResponse([]));
      if (options.method === 'POST') return new Promise<Response>((resolve) => { failCreate = resolve; });
      return Promise.reject(new Error(`Unexpected request: ${options.method} ${path}`));
    });
    renderRecipients(detail, fetcher);
    await screen.findByText('No medication records yet');
    await userEvent.click(screen.getByRole('button', { name: 'Add medication' }));
    const entered = { name: 'دواء', dose: '5 mg', schedule: 'with breakfast' };
    await userEvent.type(screen.getByLabelText('Medication name'), entered.name);
    await userEvent.type(screen.getByLabelText('Dose'), entered.dose);
    await userEvent.type(screen.getByLabelText('Schedule'), entered.schedule);
    await userEvent.dblClick(screen.getByRole('button', { name: 'Save medication' }));
    expect(fetcher.mock.calls.filter(([, options]) => options.method === 'POST')).toHaveLength(1);
    failCreate(jsonResponse({ code: 'SERVER_ERROR' }, 500));
    expect(await screen.findByRole('alert')).toHaveTextContent('We couldn’t complete this medication request.');
    expect(screen.getByLabelText('Medication name')).toHaveValue(entered.name);
    expect(screen.getByLabelText('Dose')).toHaveValue(entered.dose);
    expect(screen.getByLabelText('Schedule')).toHaveValue(entered.schedule);
    expect(fetcher.mock.calls.filter(([, options]) => options.method === 'POST')).toHaveLength(1);
  });

  it('preserves edited text after an update request fails', async () => {
    const initial = medicationFixture(recipient.id, 'Current medicine');
    const fetcher = vi.fn().mockImplementation((path: string, options: RequestInit = {}) => {
      if (path.endsWith('/intake-events')) return Promise.resolve(jsonResponse([]));
      if (path === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(recipient));
      if (path === `/api/recipients/${recipient.id}/medications` && options.method === 'GET') return Promise.resolve(jsonResponse([initial]));
      if (options.method === 'PUT') return Promise.reject(new TypeError('Failed to fetch'));
      throw new Error(`Unexpected request: ${options.method} ${path}`);
    });
    renderRecipients(detail, fetcher);
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    await userEvent.clear(screen.getByLabelText('Medication name'));
    await userEvent.type(screen.getByLabelText('Medication name'), 'مسودة غير محفوظة');
    await userEvent.click(screen.getByRole('button', { name: 'Save medication' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('We couldn’t connect.');
    expect(screen.getByLabelText('Medication name')).toHaveValue('مسودة غير محفوظة');
    expect(screen.getByLabelText('Dose')).toHaveValue(initial.doseText);
    expect(screen.getByLabelText('Schedule')).toHaveValue(initial.scheduleText);
  });

  it('keeps an active medication visible and retryable after an archive failure', async () => {
    const active = medicationFixture(recipient.id, 'Retry archive');
    const archived = { ...active, active: false };
    let medicationReads = 0;
    const fetcher = vi.fn().mockImplementation((path: string, options: RequestInit = {}) => {
      if (!options.method || options.method === 'GET') {
        if (path.endsWith('/medications')) {
          medicationReads += 1;
          return Promise.resolve(jsonResponse(medicationReads === 1 ? [active] : [archived]));
        }
        return Promise.resolve(jsonResponse(recipient));
      }
      if (options.method === 'PATCH') {
        const attempts = fetcher.mock.calls.filter(([, call]) => call.method === 'PATCH').length;
        return attempts === 1 ? Promise.reject(new TypeError('Failed to fetch')) : Promise.resolve(jsonResponse(archived));
      }
      throw new Error(`Unexpected request: ${options.method}`);
    });
    renderRecipients(detail, fetcher);
    expect(await screen.findByText('Retry archive')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));
    expect(await screen.findByText('We couldn’t connect. Check your connection and try again.')).toBeInTheDocument();
    expect(screen.getByText('Retry archive')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));
    expect(await screen.findByText('Archived', { selector: 'span' })).toBeInTheDocument();
    expect(screen.getByText('Retry archive')).toBeInTheDocument();
  });

  it('keeps a medication archived after the API reports an archived-record conflict', async () => {
    const active = medicationFixture(recipient.id, 'Externally archived');
    const archived = { ...active, active: false };
    let medicationReads = 0;
    const fetcher = vi.fn().mockImplementation((path: string, options: RequestInit = {}) => {
      if (path.endsWith('/intake-events')) return Promise.resolve(jsonResponse([]));
      if (path === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(recipient));
      if (path === `/api/recipients/${recipient.id}/medications` && options.method === 'GET') {
        medicationReads += 1;
        return Promise.resolve(jsonResponse(medicationReads === 1 ? [active] : [archived]));
      }
      if (options.method === 'PUT') return Promise.resolve(jsonResponse({ code: 'MEDICATION_CONFLICT' }, 409));
      throw new Error(`Unexpected request: ${options.method} ${path}`);
    });
    renderRecipients(detail, fetcher);
    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));
    const name = screen.getByLabelText('Medication name');
    await userEvent.clear(name);
    await userEvent.type(name, 'Unsaved text');
    await userEvent.click(screen.getByRole('button', { name: 'Save medication' }));
    expect(await screen.findByText('Archived', { selector: 'span' })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('This medication record can no longer be changed.');
    expect(screen.getByLabelText('Medication name')).toHaveValue('Unsaved text');
    expect(screen.getByLabelText('Medication name')).toHaveAttribute('readonly');
    expect(screen.getByRole('button', { name: 'Save medication' })).toBeDisabled();
  });

  it('validates required medication fields and maximum lengths before sending', async () => {
    const fetcher = vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(recipient));
      if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
      return Promise.resolve(jsonResponse([]));
    });
    renderRecipients(detail, fetcher);
    await screen.findByText('No medication records yet');
    await userEvent.click(screen.getByRole('button', { name: 'Add medication' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save medication' }));
    expect(screen.getByText('Enter a medication name.')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Medication name'), { target: { value: 'x'.repeat(161) } });
    fireEvent.change(screen.getByLabelText('Dose'), { target: { value: 'x'.repeat(501) } });
    fireEvent.change(screen.getByLabelText('Schedule'), { target: { value: 'schedule' } });
    await userEvent.click(screen.getByRole('button', { name: 'Save medication' }));
    expect(screen.getByText('Use a medication name of up to 160 characters.')).toBeInTheDocument();
    expect(screen.getByText('Use no more than 500 characters.')).toBeInTheDocument();
    expect(fetcher.mock.calls.filter(([, options]) => options.method === 'POST')).toHaveLength(0);
  });

  it('does not display malformed medication responses as an empty list', async () => {
    const malformed = { ...medicationFixture(recipient.id, 'Malformed'), active: 'false' };
    const fetcher = vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(recipient));
      if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
      return Promise.resolve(jsonResponse([malformed]));
    });
    renderRecipients(detail, fetcher);
    expect(await screen.findByRole('alert')).toHaveTextContent('We couldn’t complete this medication request.');
    expect(screen.queryByText('No medication records yet')).not.toBeInTheDocument();
  });

  it('renders Arabic medication labels and mixed-direction values accessibly', async () => {
    const medication = medicationFixture(recipient.id, 'فيتامين D / Vitamin');
    renderRecipients(detail, vi.fn().mockImplementation((path: string) => {
      const url = new URL(path, 'http://localhost');
      if (url.pathname === `/api/recipients/${recipient.id}`) return Promise.resolve(jsonResponse(recipient));
      if (url.pathname === '/api/notes') return Promise.resolve(jsonResponse(historyPage([], 0, 100)));
      return Promise.resolve(jsonResponse([medication]));
    }), 'ar');
    expect(await screen.findByRole('heading', { name: 'الأدوية' })).toBeInTheDocument();
    const displayed = screen.getByText(medication.displayName);
    expect(displayed.closest('bdi')).toHaveAttribute('dir', 'auto');
    expect(screen.getByText('وصف الجرعة')).toBeInTheDocument();
    expect(document.documentElement.dir).toBe('rtl');
  });

  it('aborts old recipient medication requests and ignores late results after recipient changes', async () => {
    const secondRecipient = 'b2222222-2222-4222-8222-222222222222';
    let resolveFirst!: (response: Response) => void;
    const firstPending = new Promise<Response>((done) => { resolveFirst = done; });
    const firstMedication = medicationFixture(recipient.id, 'First recipient only');
    const secondMedication = medicationFixture(secondRecipient, 'Second recipient only');
    const fetcher = vi.fn().mockImplementation((path: string) => path === `/api/recipients/${recipient.id}/medications`
      ? firstPending : Promise.resolve(jsonResponse([secondMedication])));
    vi.stubGlobal('fetch', fetcher);
    localStorage.setItem('caregiver-language', 'en');
    sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
    const panel = (id: string) => <I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider>
      <MemoryRouter><MedicationListPanel recipientId={id} canEdit /></MemoryRouter>
    </AuthProvider></ApiProvider></I18nProvider>;
    const view = render(panel(recipient.id));
    await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1));
    view.rerender(panel(secondRecipient));
    expect(fetcher.mock.calls[0][1].signal.aborted).toBe(true);
    expect(await screen.findByText('Second recipient only')).toBeInTheDocument();
    resolveFirst(jsonResponse([firstMedication]));
    await waitFor(() => expect(screen.queryByText('First recipient only')).not.toBeInTheDocument());
    expect(fetcher.mock.calls[1][0]).toBe(`/api/recipients/${secondRecipient}/medications`);
  });

  it('shows schedule separately from intake history', async () => {
    const medication = medicationFixture(recipient.id, 'Schedule medicine');
    const event = intakeEventFixture(medication, 'TAKEN', 'UNVERIFIED', '2026-10-22T08:30:00Z');
    const { fetcher } = intakeServe(medication, [event]);
    renderRecipients(detail, fetcher);
    const scheduleHeading = await screen.findByRole('heading', { name: 'Schedule' });
    const scheduleSection = scheduleHeading.closest('section')!;
    expect(scheduleSection).toHaveTextContent(medication.scheduleText);
    expect(scheduleSection).not.toHaveTextContent('Taken');
    const intakeHeading = screen.getByRole('heading', { name: 'Intake history' });
    const intakeSection = intakeHeading.closest('section')!;
    await waitFor(() => expect(intakeSection.querySelector('time')).not.toBeNull());
    expect(intakeSection).toHaveTextContent('Taken');
    expect(intakeSection).toHaveTextContent('Unverified');
    expect(intakeSection.querySelector('time')).toHaveAttribute('dateTime', '2026-10-22T08:30:00Z');
    expect(scheduleSection).not.toBe(intakeSection);
    expect(screen.getByText(medication.doseText)).toBeInTheDocument();
  });

  it('shows not recorded when no intake event exists', async () => {
    const medication = medicationFixture(recipient.id, 'Empty history medicine');
    const { fetcher } = intakeServe(medication, []);
    renderRecipients(detail, fetcher);
    expect(await screen.findByText('Not recorded')).toBeInTheDocument();
    expect(screen.getByText(medication.scheduleText)).toBeInTheDocument();
    const intakeSection = screen.getByRole('heading', { name: 'Intake history' }).closest('section')!;
    // No event row, timestamp, or inferred Missed state is fabricated for empty history.
    expect(intakeSection.querySelectorAll('li')).toHaveLength(0);
    expect(intakeSection.querySelector('time')).toBeNull();
  });

  it('records taken missed and unsure as explicit event values', async () => {
    const medication = medicationFixture(recipient.id, 'Explicit values medicine');
    const { fetcher } = intakeServe(medication, []);
    renderRecipients(detail, fetcher);
    await screen.findByText('Not recorded');
    for (const value of ['TAKEN', 'MISSED', 'UNSURE']) {
      await userEvent.selectOptions(screen.getByLabelText('Intake state'), value);
      await userEvent.selectOptions(screen.getByLabelText('Verification state'), 'NOT_ASSERTED');
      await userEvent.click(screen.getByRole('button', { name: 'Save intake event' }));
      await screen.findByText('Intake event recorded.');
    }
    const posts = fetcher.mock.calls.filter(([, options]) => options.method === 'POST'
      && options.body && String(options.body).includes('intakeState'));
    expect(posts).toHaveLength(3);
    expect(posts.map(([, options]) => JSON.parse(options.body as string).intakeState)).toEqual(['TAKEN', 'MISSED', 'UNSURE']);
    for (const [path, options] of posts) {
      expect(path).toBe(`/api/recipients/${recipient.id}/medications/${medication.id}/intake-events`);
      const body = JSON.parse(options.body as string);
      expect(Object.keys(body).sort()).toEqual(['intakeState', 'occurredAt', 'verificationState']);
      expect(body.verificationState).toBe('NOT_ASSERTED');
      expect(Number.isFinite(Date.parse(body.occurredAt))).toBe(true);
      expect(body).not.toHaveProperty('displayName');
      expect(body).not.toHaveProperty('doseText');
      expect(body).not.toHaveProperty('scheduleText');
    }
  });

  it('keeps verification separate from intake state', async () => {
    const medication = medicationFixture(recipient.id, 'Separate verification medicine');
    const event = intakeEventFixture(medication, 'TAKEN', 'UNVERIFIED', '2026-10-22T08:30:00Z');
    const { fetcher } = intakeServe(medication, [event]);
    renderRecipients(detail, fetcher);
    await waitFor(() => expect(document.querySelector('.medication-intake-list li')).not.toBeNull());
    const row = document.querySelector('.medication-intake-list li') as HTMLElement;
    expect(within(row).getByText('Taken')).toBeInTheDocument();
    expect(within(row).getByText('Unverified')).toBeInTheDocument();
    expect(row).toHaveTextContent('Intake state');
    expect(row).toHaveTextContent('Verification state');
    // TAKEN does not imply VERIFIED: the only verification text is Unverified.
    expect(row.textContent).not.toContain('Verified');
    expect(row.textContent?.replace('Unverified', '')).not.toContain('VERIFIED');
  });

  it('does not modify medication schedule when intake event is recorded', async () => {
    const medication = { ...medicationFixture(recipient.id, 'Unchanged schedule medicine'), doseText: '2 mg', scheduleText: 'every morning' };
    const { fetcher } = intakeServe(medication, []);
    renderRecipients(detail, fetcher);
    await screen.findByText('Not recorded');
    await userEvent.selectOptions(screen.getByLabelText('Intake state'), 'TAKEN');
    await userEvent.selectOptions(screen.getByLabelText('Verification state'), 'VERIFIED');
    await userEvent.click(screen.getByRole('button', { name: 'Save intake event' }));
    await screen.findByText('Intake event recorded.');
    expect(fetcher.mock.calls.some(([, options]) => options.method === 'POST'
      && String(options.body).includes('intakeState'))).toBe(true);
    expect(fetcher.mock.calls.some(([, options]) => options.method === 'PUT')).toBe(false);
    expect(fetcher.mock.calls.some(([, options]) => options.method === 'PATCH')).toBe(false);
    expect(screen.getByText('2 mg')).toBeInTheDocument();
    expect(screen.getByText('every morning')).toBeInTheDocument();
    expect(screen.getByText('Unchanged schedule medicine')).toBeInTheDocument();
  });

  it('renders event and verification labels in Arabic', async () => {
    const medication = medicationFixture(recipient.id, 'Arabic intake medicine');
    const { fetcher } = intakeServe(medication, []);
    renderRecipients(detail, fetcher, 'ar');
    expect(await screen.findByText('غير مسجل')).toBeInTheDocument();
    expect(document.documentElement.dir).toBe('rtl');
    expect(screen.getByText('سجل تناول الدواء')).toBeInTheDocument();
    expect(screen.getByText('حالة التناول')).toBeInTheDocument();
    expect(screen.getByText('حالة التحقق')).toBeInTheDocument();
    expect(screen.getByLabelText('حالة التناول')).toBeInTheDocument();
    expect(screen.getByLabelText('حالة التحقق')).toBeInTheDocument();
    expect(screen.queryByText('Not recorded')).not.toBeInTheDocument();
    expect(screen.queryByText('Intake history')).not.toBeInTheDocument();
    expect(screen.queryByText('Taken')).not.toBeInTheDocument();
  });

  it('shows loading intake history before events arrive', async () => {
    const medication = medicationFixture(recipient.id, 'Loading intake medicine');
    let resolveIntake!: (response: Response) => void;
    const fetcher = vi.fn().mockImplementation((path: string, options: RequestInit = {}) => {
      if (path.endsWith('/intake-events') && options.method !== 'POST') return new Promise<Response>((done) => { resolveIntake = done; });
      if (path === `/api/recipients/${recipient.id}/medications`) return Promise.resolve(jsonResponse([medication]));
      return Promise.resolve(jsonResponse(recipient));
    });
    renderRecipients(detail, fetcher);
    expect(await screen.findByText('Loading intake history…')).toBeInTheDocument();
    await act(async () => resolveIntake(jsonResponse([])));
    expect(await screen.findByText('Not recorded')).toBeInTheDocument();
  });

  it('distinguishes intake history failure from empty history', async () => {
    const medication = medicationFixture(recipient.id, 'Failing intake medicine');
    const { fetcher } = intakeServe(medication, [], { intakeGetFails: true });
    renderRecipients(detail, fetcher);
    expect(await screen.findByText('Intake history could not be loaded. Please try again.')).toBeInTheDocument();
    expect(screen.queryByText('Not recorded')).not.toBeInTheDocument();
    expect(screen.getByText(medication.scheduleText)).toBeInTheDocument();
  });

  it('does not show false success when recording an intake event fails', async () => {
    const medication = medicationFixture(recipient.id, 'Failed recording medicine');
    const { fetcher } = intakeServe(medication, [], { intakePostFails: true });
    renderRecipients(detail, fetcher);
    await screen.findByText('Not recorded');
    await userEvent.selectOptions(screen.getByLabelText('Intake state'), 'TAKEN');
    await userEvent.selectOptions(screen.getByLabelText('Verification state'), 'UNVERIFIED');
    await userEvent.click(screen.getByRole('button', { name: 'Save intake event' }));
    expect(await screen.findByText('This intake event could not be recorded. Please try again.')).toBeInTheDocument();
    expect(screen.queryByText('Intake event recorded.')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Intake state')).toHaveValue('TAKEN');
    expect(screen.getByLabelText('Verification state')).toHaveValue('UNVERIFIED');
    expect(screen.getByText('Not recorded')).toBeInTheDocument();
  });

  it('prevents duplicate intake submissions while a post is pending', async () => {
    const medication = medicationFixture(recipient.id, 'Duplicate intake medicine');
    let resolvePost!: (response: Response) => void;
    const created = intakeEventFixture(medication, 'TAKEN', 'VERIFIED', new Date().toISOString());
    const fetcher = vi.fn().mockImplementation((path: string, options: RequestInit = {}) => {
      if (path.endsWith('/intake-events') && options.method === 'POST') return new Promise<Response>((done) => { resolvePost = done; });
      if (path.endsWith('/intake-events')) return Promise.resolve(jsonResponse([]));
      if (path === `/api/recipients/${recipient.id}/medications`) return Promise.resolve(jsonResponse([medication]));
      return Promise.resolve(jsonResponse(recipient));
    });
    renderRecipients(detail, fetcher);
    await screen.findByText('Not recorded');
    await userEvent.selectOptions(screen.getByLabelText('Intake state'), 'TAKEN');
    await userEvent.selectOptions(screen.getByLabelText('Verification state'), 'VERIFIED');
    const submit = screen.getByRole('button', { name: 'Save intake event' });
    fireEvent.click(submit); fireEvent.click(submit);
    expect(fetcher.mock.calls.filter(([, options]) => options.method === 'POST')).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Saving intake event…' })).toBeDisabled();
    await act(async () => resolvePost(jsonResponse(created, 201)));
    expect(await screen.findByText('Intake event recorded.')).toBeInTheDocument();
    expect(fetcher.mock.calls.filter(([, options]) => options.method === 'POST')).toHaveLength(1);
  });

  it('rejects intake submission without explicit state and verification selections', async () => {
    const medication = medicationFixture(recipient.id, 'Invalid intake medicine');
    const { fetcher } = intakeServe(medication, []);
    renderRecipients(detail, fetcher);
    await screen.findByText('Not recorded');
    await userEvent.click(screen.getByRole('button', { name: 'Save intake event' }));
    expect(await screen.findByText('Choose an intake state and a verification state.')).toBeInTheDocument();
    expect(fetcher.mock.calls.some(([, options]) => options.method === 'POST')).toBe(false);
  });
});

function medicationFixture(recipientId: string, displayName: string) {
  return {
    id: 'c3333333-3333-4333-8333-333333333333', recipientId, displayName,
    doseText: '1 tablet', scheduleText: 'once daily', active: true,
    createdAt: '2026-10-01T10:00:00Z', updatedAt: '2026-10-01T10:00:00Z',
  };
}

function intakeEventFixture(
  medication: ReturnType<typeof medicationFixture>,
  intakeState: 'TAKEN' | 'MISSED' | 'UNSURE',
  verificationState: 'VERIFIED' | 'UNVERIFIED' | 'NOT_ASSERTED',
  occurredAt: string,
  id = 'e5555555-5555-4555-8555-555555555555',
) {
  return {
    id, medicationId: medication.id, recipientId: medication.recipientId,
    intakeState, occurredAt, verificationState, createdAt: occurredAt,
  };
}

function intakeServe(
  medication: ReturnType<typeof medicationFixture>,
  initialEvents: ReturnType<typeof intakeEventFixture>[],
  options: { intakeGetFails?: boolean; intakePostFails?: boolean } = {},
) {
  let events = [...initialEvents];
  let nextId = 1;
  const fetcher = vi.fn().mockImplementation((path: string, init: RequestInit = {}) => {
    if (path.endsWith('/intake-events')) {
      if (init.method === 'POST') {
        if (options.intakePostFails) return Promise.resolve(jsonResponse({ code: 'VALIDATION_ERROR' }, 400));
        const body = JSON.parse(init.body as string) as {
          intakeState: 'TAKEN' | 'MISSED' | 'UNSURE'; occurredAt: string;
          verificationState: 'VERIFIED' | 'UNVERIFIED' | 'NOT_ASSERTED';
        };
        const created = intakeEventFixture(
          medication, body.intakeState, body.verificationState, body.occurredAt,
          `f6666666-6666-4666-8666-66666666600${nextId++}`);
        events = [...events, created];
        return Promise.resolve(jsonResponse(created, 201));
      }
      if (options.intakeGetFails) return Promise.resolve(jsonResponse({ code: 'SERVICE_ERROR' }, 500));
      return Promise.resolve(jsonResponse(events));
    }
    if (path === `/api/recipients/${recipient.id}/medications`) return Promise.resolve(jsonResponse([medication]));
    return Promise.resolve(jsonResponse(recipient));
  });
  return { fetcher, getEvents: () => events };
}
