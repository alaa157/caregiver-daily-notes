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

function renderRecipients(path: string, fetcher: ReturnType<typeof vi.fn>, language = 'en', authenticated = true) {
  vi.stubGlobal('fetch', fetcher);
  localStorage.setItem('caregiver-language', language);
  if (authenticated) sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider><MemoryRouter initialEntries={[path]}><App /></MemoryRouter></AuthProvider></ApiProvider></I18nProvider>);
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

    const tabs = screen.getByRole('navigation', { name: 'Recipient sections' });
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

  it('protects direct recipient routes before making any API request', () => {
    const fetcher = vi.fn();
    renderRecipients(`${detail}/edit`, fetcher, 'en', false);
    expect(screen.getByRole('heading', { name: 'Caregiver sign in' })).toBeInTheDocument();
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('renders loading, owned recipients and active status without ownership internals', async () => {
    let resolve!: (response: Response) => void;
    const fetcher = vi.fn().mockImplementation(() => new Promise<Response>((done) => { resolve = done; }));
    renderRecipients(root, fetcher);
    expect(screen.getByText('Loading your care recipients…')).toBeInTheDocument();
    resolve(jsonResponse([recipient]));
    expect(await screen.findByRole('link', { name: recipient.name })).toHaveAttribute('href', detail);
    expect(within(screen.getByRole('group', { name: 'Filter recipients by lifecycle status' })).getByRole('button', { name: 'Active' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByText('secret-owner')).not.toBeInTheDocument();
    expect(new Headers(fetcher.mock.calls[0][1].headers).get('Authorization')).toMatch(/^Bearer /);
    expect(screen.getByRole('navigation').querySelector('[aria-current="page"]')).toHaveTextContent('People');
  });

  it('offers creation from the empty state', async () => {
    renderRecipients(root, vi.fn().mockResolvedValue(jsonResponse([])));
    expect(await screen.findByText('No active care recipients', { exact: true })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('link', { name: 'Add care recipient' }));
    expect(screen.getByRole('form', { name: 'Add care recipient' })).toBeInTheDocument();
  });
  it('searches recipients by name or relationship', async () => {
    const fetcher = vi.fn().mockResolvedValue(jsonResponse([recipient, { ...recipient, id: 'b2222222-2222-4222-8222-222222222222', name: 'Mona', relationship: 'Aunt', active: true }]));
    renderRecipients(root, fetcher);
    expect(await screen.findByRole('link', { name: recipient.name })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Mona' })).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Search name or relationship'), 'aunt');
    expect(screen.getByRole('link', { name: 'Mona' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: recipient.name })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Clear' }));
    expect(screen.getByRole('link', { name: recipient.name })).toBeInTheDocument();
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it('filters recipients by case-insensitive name and relationship', async () => {
    const nameMatch = { ...recipient, name: 'caregiver Samir', relationship: 'Son' };
    const relationshipMatch = { ...recipient, id: 'b2222222-2222-4222-8222-222222222222', name: 'Mona', relationship: 'Primary caregiver' };
    const unrelated = { ...recipient, id: 'c3333333-3333-4333-8333-333333333333', name: 'Laila', relationship: 'Aunt' };
    renderRecipients(root, vi.fn().mockResolvedValue(jsonResponse([nameMatch, relationshipMatch, unrelated])));

    await screen.findByRole('link', { name: nameMatch.name });
    await userEvent.type(screen.getByLabelText('Search name or relationship'), 'CaReGiVeR');
    expect(screen.getByRole('link', { name: nameMatch.name })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: relationshipMatch.name })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: unrelated.name })).not.toBeInTheDocument();
  });

  it('shows no-match state separately from empty recipient list', async () => {
    const populated = vi.fn().mockResolvedValue(jsonResponse([recipient]));
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
    const fetcher = vi.fn().mockImplementation((path: string) => path === '/api/recipients?status=ARCHIVED'
      ? Promise.resolve(jsonResponse([archived]))
      : Promise.resolve(jsonResponse([active])));
    renderRecipients(root, fetcher);

    await userEvent.click(screen.getByRole('button', { name: 'Archived' }));
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
    expect(fetcher).toHaveBeenCalledWith('/api/recipients?status=ARCHIVED', expect.anything());
  });

  it('retries a list error and does not display raw server messages', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse({ message: 'private diagnostics' }, 500)).mockResolvedValueOnce(jsonResponse([recipient]));
    renderRecipients(root, fetcher);
    expect(await screen.findByRole('alert')).toHaveTextContent('We couldn’t complete this request.');
    expect(screen.queryByText('private diagnostics')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByRole('link', { name: recipient.name })).toBeInTheDocument();
  });

  it('validates create before submitting, trims fields and opens saved details', async () => {
    // POST currently returns null timestamps; GET returns the committed dates.
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse({ ...recipient, createdAt: null, updatedAt: null }, 201)).mockResolvedValueOnce(jsonResponse(recipient));
    renderRecipients(`${root}/new`, fetcher);
    await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));
    expect(screen.getByRole('textbox', { name: 'Name' })).toHaveFocus();
    expect(screen.getByRole('alert')).toHaveTextContent('Enter the recipient’s name.');
    expect(fetcher).not.toHaveBeenCalled();
    await userEvent.type(screen.getByRole('textbox', { name: 'Name' }), ' Samir Hassan ');
    await userEvent.type(screen.getByRole('textbox', { name: 'Relationship (optional)' }), ' Father ');
    await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));
    expect(await screen.findByRole('heading', { name: recipient.name })).toBeInTheDocument();
    expect(screen.getByText('Care recipient added.')).toHaveFocus();
    expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({ name: recipient.name, relationship: recipient.relationship });
    expect(fetcher.mock.calls[0][1].method).toBe('POST');
  });

  it('rejects overlength values and links validation to the affected field', async () => {
    const fetcher = vi.fn();
    renderRecipients(`${root}/new`, fetcher);
    fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'x'.repeat(121) } });
    fireEvent.change(screen.getByRole('textbox', { name: 'Relationship (optional)' }), { target: { value: 'x'.repeat(81) } });
    await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));
    expect(screen.getAllByRole('alert')).toHaveLength(2);
    expect(screen.getByRole('textbox', { name: 'Name' })).toHaveAttribute('aria-describedby', expect.stringContaining('recipient-name-error'));
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('preserves form values after backend validation errors and permits correction', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse({ code: 'VALIDATION_ERROR', message: 'private diagnostics' }, 422)).mockResolvedValueOnce(jsonResponse(recipient, 201)).mockResolvedValueOnce(jsonResponse(recipient));
    renderRecipients(`${root}/new`, fetcher);
    await userEvent.type(screen.getByLabelText('Name'), recipient.name);
    await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));
    expect(await screen.findByRole('alert')).toHaveFocus();
    expect(screen.getByLabelText('Name')).toHaveValue(recipient.name);
    expect(screen.queryByText('private diagnostics')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add care recipient' }));
    expect(await screen.findByText('Care recipient added.')).toBeInTheDocument();
  });

  it('edits current data with PUT, including removing the optional relationship', async () => {
    const updated = { ...recipient, name: 'Samir Updated', relationship: null };
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(recipient))
      .mockResolvedValueOnce(jsonResponse(updated)).mockResolvedValueOnce(jsonResponse(updated));
    renderRecipients(`${detail}/edit`, fetcher);
    expect(await screen.findByLabelText('Name')).toHaveValue(recipient.name);
    await userEvent.clear(screen.getByLabelText('Name'));
    await userEvent.type(screen.getByLabelText('Name'), updated.name);
    await userEvent.clear(screen.getByLabelText('Relationship (optional)'));
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Recipient details updated.')).toBeInTheDocument();
    expect(JSON.parse(fetcher.mock.calls[1][1].body)).toEqual({ name: updated.name, relationship: null });
    expect(fetcher.mock.calls[1][1].method).toBe('PUT');
    expect(await screen.findByRole('heading', { name: updated.name })).toBeInTheDocument();
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
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(recipient)).mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse(archived)).mockResolvedValueOnce(jsonResponse(restored));
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
    expect(fetcher).toHaveBeenCalledTimes(3);
  });

  it('rejects malformed route IDs without calling the backend', async () => {
    const fetcher = vi.fn();
    renderRecipients(`${root}/not-a-uuid`, fetcher);
    expect(await screen.findByRole('alert')).toHaveTextContent('This recipient is unavailable.');
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('requires confirmation before archive and restores focus when cancelled', async () => {
    const fetcher = vi.fn().mockImplementation((path: string) => Promise.resolve(
      jsonResponse(path.endsWith('/medications') ? [] : recipient)));
    renderRecipients(detail, fetcher);
    const trigger = await screen.findByRole('button', { name: 'Archive recipient' });
    await userEvent.click(trigger);
    const dialog = screen.getByRole('dialog', { name: 'Archive this recipient?' });
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toHaveFocus();
    expect(dialog).toHaveTextContent('Existing notes, corrections, plans, and history will be preserved.');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
    expect(fetcher).toHaveBeenCalledTimes(2);
  });

  it('archives the recipient through the API and makes the detail read-only', async () => {
    const archived = { ...recipient, active: false, lifecycleStatus: 'ARCHIVED' as const, archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(recipient)).mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse(archived));
    renderRecipients(detail, fetcher);
    await userEvent.click(await screen.findByRole('button', { name: 'Archive recipient' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Archive recipient' }));
    expect(await screen.findByText('Archived')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Edit recipient' })).not.toBeInTheDocument();
    expect(screen.getByText('Recipient archived. Their history has been preserved.')).toHaveFocus();
    expect(fetcher.mock.calls[2][0]).toBe(`${detail.replace('/app/', '/api/')}/archive`);
    expect(fetcher.mock.calls[2][1].method).toBe('PATCH');
    expect(fetcher.mock.calls[2][1].body).toBeUndefined();
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
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(recipient)).mockResolvedValueOnce(jsonResponse([]))
      .mockRejectedValueOnce(new TypeError('Failed to fetch')).mockResolvedValueOnce(jsonResponse(archived));
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
    const fetcher = vi.fn().mockImplementation(() => new Promise(() => {}));
    renderRecipients(`${root}/new`, fetcher);
    await userEvent.type(screen.getByLabelText('Name'), recipient.name);
    await userEvent.dblClick(screen.getByRole('button', { name: 'Add care recipient' }));
    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: 'Saving…' })).toBeDisabled();
    await userEvent.click(screen.getByRole('link', { name: 'Cancel' }));
    expect(fetcher.mock.calls[0][1].signal.aborted).toBe(true);
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
    let resolve!: (response: Response) => void;
    const pending = vi.fn().mockImplementation((path: string) => path === `/api/recipients/${recipient.id}`
      ? Promise.resolve(jsonResponse(recipient))
      : new Promise<Response>((done) => { resolve = done; }));
    const loadingView = renderRecipients(detail, pending);
    await waitFor(() => expect(pending).toHaveBeenCalledTimes(2));
    expect(screen.getByText('Loading medication records…')).toBeInTheDocument();
    resolve(jsonResponse([]));
    expect(await screen.findByRole('heading', { name: 'Medications' })).toBeInTheDocument();
    loadingView.unmount();

    const emptyFetcher = vi.fn().mockResolvedValueOnce(jsonResponse(recipient)).mockResolvedValueOnce(jsonResponse([]));
    const emptyView = renderRecipients(detail, emptyFetcher);
    expect(await screen.findByText('No medication records yet')).toBeInTheDocument();
    expect(screen.getByText(/does not indicate whether medication is used/)).toBeInTheDocument();
    emptyView.unmount();

    const errorFetcher = vi.fn().mockResolvedValueOnce(jsonResponse(recipient)).mockResolvedValueOnce(jsonResponse({ message: 'private details' }, 500))
      .mockResolvedValueOnce(jsonResponse([medicationFixture(recipient.id, 'Recovered')]));
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
    renderRecipients(detail, vi.fn().mockResolvedValueOnce(jsonResponse(recipient)).mockResolvedValueOnce(jsonResponse([archived])));
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
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(recipient)).mockResolvedValueOnce(jsonResponse([]));
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
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(recipient)).mockResolvedValueOnce(jsonResponse([malformed]));
    renderRecipients(detail, fetcher);
    expect(await screen.findByRole('alert')).toHaveTextContent('We couldn’t complete this medication request.');
    expect(screen.queryByText('No medication records yet')).not.toBeInTheDocument();
  });

  it('renders Arabic medication labels and mixed-direction values accessibly', async () => {
    const medication = medicationFixture(recipient.id, 'فيتامين D / Vitamin');
    renderRecipients(detail, vi.fn().mockResolvedValueOnce(jsonResponse(recipient)).mockResolvedValueOnce(jsonResponse([medication])), 'ar');
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
