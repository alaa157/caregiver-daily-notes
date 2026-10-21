import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';
import { App } from '../app/App';
import { AuthProvider } from '../features/auth/AuthProvider';
import { I18nProvider } from '../i18n/I18nProvider';
import { ApiProvider } from '../lib/api';
import { jsonResponse, testToken } from './authFixtures';
import { recipientFixture as recipient } from './recipientFixtures';

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
  it('protects direct recipient routes before making any API request', () => {
    const fetcher = vi.fn();
    renderRecipients(`${detail}/edit`, fetcher, 'en', false);
    expect(screen.getByRole('heading', { name: 'Caregiver sign in' })).toBeInTheDocument();
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('renders loading, owned recipients and inactive status without ownership internals', async () => {
    let resolve!: (response: Response) => void;
    const fetcher = vi.fn().mockImplementation(() => new Promise<Response>((done) => { resolve = done; }));
    renderRecipients(root, fetcher);
    expect(screen.getByText('Loading your care recipients…')).toBeInTheDocument();
    resolve(jsonResponse([recipient, { ...recipient, id: 'b2222222-2222-4222-8222-222222222222', name: 'Mona', active: false, caregiverId: 'secret-owner' }]));
    expect(await screen.findByRole('link', { name: recipient.name })).toHaveAttribute('href', detail);
    expect(screen.getByText('Inactive')).toBeInTheDocument();
    expect(screen.queryByText('secret-owner')).not.toBeInTheDocument();
    expect(new Headers(fetcher.mock.calls[0][1].headers).get('Authorization')).toMatch(/^Bearer /);
    expect(screen.getByRole('navigation').querySelector('[aria-current="page"]')).toHaveTextContent('Care recipients');
  });

  it('offers creation from the empty state', async () => {
    renderRecipients(root, vi.fn().mockResolvedValue(jsonResponse([])));
    expect(await screen.findByText('No care recipients yet')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('link', { name: 'Add care recipient' }));
    expect(screen.getByRole('form', { name: 'Add care recipient' })).toBeInTheDocument();
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
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(recipient)).mockResolvedValueOnce(jsonResponse(updated)).mockResolvedValueOnce(jsonResponse(updated));
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

  it('reloads an inactive conflict without silently losing the entered values', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(recipient)).mockResolvedValueOnce(jsonResponse({ code: 'INACTIVE_RECIPIENT' }, 409)).mockResolvedValueOnce(jsonResponse({ ...recipient, active: false }));
    renderRecipients(`${detail}/edit`, fetcher);
    const name = await screen.findByLabelText('Name');
    await userEvent.clear(name);
    await userEvent.type(name, 'Unsaved name');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('The recipient can no longer be updated');
    expect(name).toHaveValue('Unsaved name');
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Reload recipient details' }));
    expect(await screen.findByText('Inactive')).toBeInTheDocument();
    expect(screen.queryByRole('form')).not.toBeInTheDocument();
    expect(screen.getByText(/Their historical notes and plans are preserved/)).toBeInTheDocument();
  });

  it('keeps inactive detail/edit routes read-only with no delete or reactivate operation', async () => {
    renderRecipients(`${detail}/edit`, vi.fn().mockResolvedValue(jsonResponse({ ...recipient, active: false })));
    expect(await screen.findByText('Inactive')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Deactivate|Delete|Reactivate/ })).not.toBeInTheDocument();
  });

  it.each([403, 404])('uses a safe unavailable message for HTTP %i', async (status) => {
    renderRecipients(detail, vi.fn().mockResolvedValue(jsonResponse({ message: 'belongs to other caregiver' }, status)));
    expect(await screen.findByRole('alert')).toHaveTextContent('This recipient is unavailable.');
    expect(screen.queryByText('belongs to other caregiver')).not.toBeInTheDocument();
  });

  it('rejects malformed route IDs without calling the backend', async () => {
    const fetcher = vi.fn();
    renderRecipients(`${root}/not-a-uuid`, fetcher);
    expect(await screen.findByRole('alert')).toHaveTextContent('This recipient is unavailable.');
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('requires confirmation and restores focus when deactivation is cancelled', async () => {
    const fetcher = vi.fn().mockResolvedValue(jsonResponse(recipient));
    renderRecipients(detail, fetcher);
    const trigger = await screen.findByRole('button', { name: 'Deactivate recipient' });
    await userEvent.click(trigger);
    const dialog = screen.getByRole('dialog', { name: 'Deactivate this recipient?' });
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toHaveFocus();
    expect(dialog).toHaveTextContent('Their historical notes and plans will be preserved.');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it('confirms deactivation once and leaves the record visible as inactive', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(recipient)).mockResolvedValueOnce(jsonResponse({ ...recipient, active: false }));
    renderRecipients(detail, fetcher);
    await userEvent.click(await screen.findByRole('button', { name: 'Deactivate recipient' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirm deactivation' }));
    expect(await screen.findByText('Inactive')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Edit recipient' })).not.toBeInTheDocument();
    expect(screen.getByText('Recipient deactivated. Their historical notes and plans are preserved.')).toHaveFocus();
    expect(fetcher.mock.calls[1][0]).toBe(`${detail.replace('/app/', '/api/')}/deactivate`);
    expect(fetcher.mock.calls[1][1].method).toBe('PATCH');
    expect(fetcher.mock.calls[1][1].body).toBeUndefined();
  });

  it('keeps the confirmation open after a failed deactivation and permits retry', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(recipient)).mockRejectedValueOnce(new TypeError('Failed to fetch')).mockResolvedValueOnce(jsonResponse({ ...recipient, active: false }));
    renderRecipients(detail, fetcher);
    await userEvent.click(await screen.findByRole('button', { name: 'Deactivate recipient' }));
    await userEvent.click(screen.getByRole('button', { name: 'Confirm deactivation' }));
    expect(await screen.findByRole('alert')).toHaveFocus();
    expect(screen.getByRole('dialog')).toHaveTextContent('We couldn’t connect.');
    await userEvent.click(screen.getByRole('button', { name: 'Confirm deactivation' }));
    expect(await screen.findByText('Inactive')).toBeInTheDocument();
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
});
