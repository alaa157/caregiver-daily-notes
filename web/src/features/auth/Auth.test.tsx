import { StrictMode, useState } from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../../app/App';
import { I18nProvider } from '../../i18n/I18nProvider';
import { ApiProvider, useApiClient } from '../../lib/api';
import { jsonResponse, testToken } from '../../tests/authFixtures';
import { AuthProvider } from './AuthProvider';
import { SESSION_STORAGE_KEY } from './session';

function Probe() {
  const api = useApiClient();
  const location = useLocation();
  const [result, setResult] = useState('');
  return <>
    <output data-testid="location">{location.pathname + location.search + location.hash}</output>
    <button onClick={() => { void api.request<string>({ path: '/api/test/whoami' }).then(setResult, () => setResult('request denied')); }}>Load private data</button>
    <output data-testid="result">{result}</output>
  </>;
}

function renderApp(path = '/login', baseUrl = '', state?: unknown) {
  return render(<StrictMode><I18nProvider><ApiProvider config={{ apiBaseUrl: baseUrl }}><AuthProvider>
    <MemoryRouter initialEntries={[{ pathname: path.split('?')[0].split('#')[0], search: path.includes('?') ? '?' + path.split('?')[1].split('#')[0] : '', hash: path.includes('#') ? '#' + path.split('#')[1] : '', state }]}>
      <App /><Probe />
    </MemoryRouter>
  </AuthProvider></ApiProvider></I18nProvider></StrictMode>);
}

async function fillAndSubmit(email = 'caregiver@example.com', password = 'valid password') {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('Email address'), email);
  await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Sign in' }));
  return user;
}

function saveToken(token = testToken(), baseUrl = '') {
  sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ token, baseUrl }));
  return token;
}

describe('caregiver authentication', () => {
  let fetcher: ReturnType<typeof vi.fn>;
  beforeEach(() => { fetcher = vi.fn(); vi.stubGlobal('fetch', fetcher); });

  it('validates required fields, focuses the first error, and makes no request', async () => {
    renderApp();
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(screen.getByLabelText('Email address')).toHaveFocus();
    expect(screen.getByLabelText('Email address')).toHaveAccessibleDescription('Enter your email address.');
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription('Enter your password.');
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('rejects malformed email and passwords exceeding the backend UTF-8 byte limit', async () => {
    renderApp();
    const user = await fillAndSubmit('invalid-address', 'é'.repeat(37));
    expect(screen.getByText('Enter a valid email address (up to 320 characters).')).toBeVisible();
    expect(screen.getByText('Your password must be at most 72 UTF-8 bytes.')).toBeVisible();
    await user.clear(screen.getByLabelText('Email address'));
    expect(screen.getByLabelText('Email address')).toHaveAttribute('aria-invalid', 'false');
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('posts the real contract, normalizes email, preserves the password, and returns to the protected URL', async () => {
    const token = testToken();
    fetcher.mockResolvedValue(jsonResponse({ token }));
    renderApp('/app?view=recent#notes', 'https://backend.example/api');
    await fillAndSubmit('  CAREGIVER@Example.com  ', ' padded password ');
    await screen.findByRole('heading', { name: 'Caregiver workspace' });
    expect(screen.getByTestId('location')).toHaveTextContent('/app?view=recent#notes');
    const [url, options] = fetcher.mock.calls[0];
    expect(url).toBe('https://backend.example/api/auth/login');
    expect(options.method).toBe('POST');
    expect(JSON.parse(options.body)).toEqual({ email: 'caregiver@example.com', password: ' padded password ' });
    expect(options.headers.has('Authorization')).toBe(false);
    expect(options).toMatchObject({ credentials: 'omit', redirect: 'error', cache: 'no-store' });
    expect(JSON.parse(sessionStorage.getItem(SESSION_STORAGE_KEY)!)).toEqual({ token, baseUrl: 'https://backend.example/api' });
    expect(Object.values(localStorage)).not.toContain(token);
  });

  it('allows a single-label email domain accepted by the backend', async () => {
    fetcher.mockResolvedValue(jsonResponse({ token: testToken() }));
    renderApp();
    await fillAndSubmit('caregiver@localhost');
    await screen.findByRole('heading', { name: 'Caregiver workspace' });
  });

  it.each([
    [401, 'The email or password is incorrect. Please try again.'],
    [422, 'Check your email and password, then try again.'],
    [403, 'Sign-in is unavailable for this request. Please try again.'],
    [429, 'Too many sign-in attempts. Please wait a moment and try again.'],
    [500, 'Sign-in is temporarily unavailable. Please try again.'],
  ])('safely handles HTTP %s without showing or logging server details', async (status, message) => {
    const log = vi.spyOn(console, 'log');
    const error = vi.spyOn(console, 'error');
    fetcher.mockResolvedValue(jsonResponse({ message: 'sensitive server diagnostics', token: 'server-secret' }, status));
    renderApp();
    await fillAndSubmit();
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(message);
    expect(alert).toHaveFocus();
    expect(screen.getByLabelText('Password')).toHaveValue('');
    expect(screen.queryByText(/sensitive server diagnostics|server-secret/)).not.toBeInTheDocument();
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
    expect(log).not.toHaveBeenCalled();
    expect(error).not.toHaveBeenCalled();
  });

  it('provides a retryable network error', async () => {
    fetcher.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    renderApp();
    const user = await fillAndSubmit();
    expect(await screen.findByRole('alert')).toHaveTextContent('We couldn’t connect.');
    fetcher.mockResolvedValueOnce(jsonResponse({ token: testToken() }));
    await user.type(screen.getByLabelText('Password'), 'retry password');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    await screen.findByRole('heading', { name: 'Caregiver workspace' });
  });

  it.each([{}, { token: 'malformed' }, { token: testToken(-5) }])('rejects an invalid login response', async (body) => {
    fetcher.mockResolvedValue(jsonResponse(body));
    renderApp();
    await fillAndSubmit();
    expect(await screen.findByRole('alert')).toHaveTextContent('Sign-in is temporarily unavailable.');
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('restores a valid tab session, logs out, and protects the dashboard again', async () => {
    saveToken();
    const first = renderApp('/app');
    expect(screen.getByRole('heading', { name: 'Caregiver workspace' })).toBeVisible();
    expect(fetcher).not.toHaveBeenCalled();
    first.unmount();
    renderApp('/app');
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    expect(await screen.findByRole('heading', { name: 'Caregiver sign in' })).toBeVisible();
    expect(screen.getByText('You have signed out.')).toBeVisible();
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it.each([testToken(-10), 'invalid-token'])('clears expired or malformed stored sessions under StrictMode', async (token) => {
    saveToken(token);
    renderApp('/app');
    expect(screen.getByRole('heading', { name: 'Caregiver sign in' })).toBeVisible();
    expect(screen.getByRole('alert')).toHaveTextContent('Your session has expired or is no longer valid.');
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('does not send a stored token to a changed backend', async () => {
    saveToken(testToken(), 'https://previous.example');
    renderApp('/app', 'https://new.example');
    expect(screen.getByRole('heading', { name: 'Caregiver sign in' })).toBeVisible();
    expect(fetcher).not.toHaveBeenCalled();
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('expires a live session automatically', async () => {
    vi.useFakeTimers();
    saveToken(testToken(5));
    renderApp('/app');
    expect(screen.getByRole('heading', { name: 'Caregiver workspace' })).toBeVisible();
    await act(async () => { vi.advanceTimersByTime(6000); });
    expect(screen.getByRole('heading', { name: 'Caregiver sign in' })).toBeVisible();
    expect(screen.getByRole('alert')).toHaveTextContent('Your session has expired');
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('adds the bearer header centrally and invalidates a rejected session even with a broken error body', async () => {
    const token = saveToken();
    fetcher.mockResolvedValue(new Response('not json', { status: 401, headers: { 'content-type': 'application/json' } }));
    renderApp('/app');
    await userEvent.click(screen.getByRole('button', { name: 'Load private data' }));
    await screen.findByRole('heading', { name: 'Caregiver sign in' });
    expect(fetcher.mock.calls[0][1].headers.get('Authorization')).toBe('Bearer ' + token);
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
    expect(screen.getByRole('alert')).toHaveTextContent('Your session has expired');
  });

  it('keeps the session on a resource-level 403', async () => {
    saveToken();
    fetcher.mockResolvedValue(jsonResponse({ code: 'FORBIDDEN' }, 403));
    renderApp('/app');
    await userEvent.click(screen.getByRole('button', { name: 'Load private data' }));
    await waitFor(() => expect(screen.getByTestId('result')).toHaveTextContent('request denied'));
    expect(screen.getByRole('heading', { name: 'Caregiver workspace' })).toBeVisible();
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).not.toBeNull();
  });

  it('deduplicates submissions and ignores a late login response after navigation away', async () => {
    let resolve!: (response: Response) => void;
    fetcher.mockImplementation(() => new Promise<Response>((done) => { resolve = done; }));
    renderApp();
    const user = await fillAndSubmit();
    expect(screen.getByRole('button', { name: 'Signing in…' })).toBeDisabled();
    fireEvent.submit(screen.getByRole('form'));
    expect(fetcher).toHaveBeenCalledTimes(1);
    await user.click(screen.getByRole('link', { name: 'Return home' }));
    expect(fetcher.mock.calls[0][1].signal.aborted).toBe(true);
    await act(async () => { resolve(jsonResponse({ token: testToken() })); });
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
    expect(screen.getByRole('heading', { name: 'Better records. More thoughtful care.' })).toBeVisible();
  });

  it('does not let a stale 401 clear a newly signed-in session', async () => {
    saveToken();
    let resolveOld!: (response: Response) => void;
    fetcher.mockImplementationOnce(() => new Promise<Response>((done) => { resolveOld = done; }));
    renderApp('/app');
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Load private data' }));
    await user.click(screen.getByRole('button', { name: 'Sign out' }));
    const newToken = testToken(3600, 'new-session-signature');
    fetcher.mockResolvedValueOnce(jsonResponse({ token: newToken }));
    await fillAndSubmit();
    await screen.findByRole('heading', { name: 'Caregiver workspace' });
    await act(async () => { resolveOld(jsonResponse({}, 401)); });
    expect(screen.getByRole('heading', { name: 'Caregiver workspace' })).toBeVisible();
    expect(JSON.parse(sessionStorage.getItem(SESSION_STORAGE_KEY)!).token).toBe(newToken);
  });

  it('discards private results that arrive after logout', async () => {
    saveToken();
    let resolve!: (response: Response) => void;
    fetcher.mockImplementation(() => new Promise<Response>((done) => { resolve = done; }));
    renderApp('/app');
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Load private data' }));
    await user.click(screen.getByRole('button', { name: 'Sign out' }));
    await act(async () => { resolve(jsonResponse('private content')); });
    expect(screen.getByTestId('result')).toHaveTextContent('request denied');
    expect(screen.queryByText('private content')).not.toBeInTheDocument();
  });

  it('continues in memory if session storage is disabled', async () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new DOMException('Blocked'); });
    fetcher.mockResolvedValue(jsonResponse({ token: testToken() }));
    renderApp();
    await fillAndSubmit();
    await screen.findByRole('heading', { name: 'Caregiver workspace' });
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    await screen.findByRole('heading', { name: 'Caregiver sign in' });
  });

  it('supports password visibility, Arabic form errors, and RTL', async () => {
    renderApp();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Show' }));
    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'text');
    expect(screen.getByRole('button', { name: 'Hide' })).toHaveAttribute('aria-pressed', 'true');
    await user.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));
    expect(document.documentElement.dir).toBe('rtl');
    expect(screen.getByLabelText('البريد الإلكتروني')).toHaveAttribute('dir', 'ltr');
    await user.click(within(screen.getByRole('form')).getByRole('button', { name: 'تسجيل الدخول' }));
    expect(screen.getByLabelText('البريد الإلكتروني')).toHaveAccessibleDescription('أدخل بريدك الإلكتروني.');
    expect(screen.getByLabelText('كلمة المرور')).toHaveAccessibleDescription('أدخل كلمة المرور.');
    expect(fetcher).not.toHaveBeenCalled();
  });
});
