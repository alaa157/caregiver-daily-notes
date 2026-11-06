import { useState } from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthContext';
import { I18nProvider } from '../i18n/I18nProvider';
import { EmptyState, ErrorState, FieldError, LoadingState } from '../components/Feedback';
import { App } from './App';
import { ApiProvider } from '../lib/api';
import { observationFields } from '../api/notes';
import { jsonResponse, testToken } from '../tests/authFixtures';
import { recipientFixture } from '../tests/recipientFixtures';
import { noteDetailFixture, noteFixture, noteListFixture } from '../tests/noteFixtures';
import { ByokProvider, useByok } from '../features/ai/ByokContext';
import { AiSettingsDialog } from '../features/ai/AiSettingsDialog';
import { AppShell } from '../components/AppShell';
import { useApiClient } from '../lib/api';
import { useAuth } from '../features/auth/AuthProvider';
import { ThemeProvider } from './ThemeProvider';

function renderRoute(path: string) {
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider><MemoryRouter initialEntries={[path]}><App /></MemoryRouter></AuthProvider></ApiProvider></I18nProvider>);
}
function ByokStateProbe() {
  const { credentials } = useByok();
  return <>
    <output data-testid="byok-state">{credentials ? 'configured' : 'empty'}</output>
    <output data-testid="byok-active">{credentials ? `${credentials.providerId} · ${credentials.model}` : 'none'}</output>
    <output data-testid="byok-key-preserved">{credentials?.apiKey === 'active-secret' ? 'yes' : 'no'}</output>
  </>;
}

function renderByokDialogUnderProvider(fetcher: unknown) {
  vi.stubGlobal('fetch', fetcher);
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><ByokProvider>
    <AiSettingsDialog open /><ByokStateProbe />
  </ByokProvider></ApiProvider></I18nProvider>);
}

function AuthenticatedByokProbe() {
  const { credentials, setCredentials } = useByok();
  const api = useApiClient();
  return <>
    <output data-testid="shell-byok-state">{credentials ? 'configured' : 'empty'}</output>
    <button type="button" onClick={() => setCredentials({ providerId: 'OPENAI', model: 'gpt-test', apiKey: 'active-secret' })}>Configure test credentials</button>
    <button type="button" onClick={() => { void api.request({ path: '/api/authenticated-probe' }).catch(() => {}); }}>Request private data</button>
  </>;
}

function ByokLifecycleHarness() {
  const { status } = useAuth();
  return status === 'authenticated'
    ? <AuthenticatedByokProbe />
    : <output data-testid="shell-byok-state">empty</output>;
}

function renderByokLifecycle(fetcher: unknown) {
  vi.stubGlobal('fetch', fetcher);
  localStorage.clear();
  sessionStorage.clear();
  sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider>
    <MemoryRouter initialEntries={['/app']}><ThemeProvider><AppShell><ByokLifecycleHarness /></AppShell></ThemeProvider></MemoryRouter>
  </AuthProvider></ApiProvider></I18nProvider>);
}

function AppShellMountHarness() {
  const [mounted, setMounted] = useState(true);
  return <>
    <button type="button" onClick={() => setMounted((value) => !value)}>
      {mounted ? 'Unmount authenticated shell' : 'Mount authenticated shell'}
    </button>
    <ThemeProvider>{mounted && <MemoryRouter initialEntries={['/app']}><AppShell><AuthenticatedByokProbe /></AppShell></MemoryRouter>}</ThemeProvider>
  </>;
}

function renderByokShellUnmount(fetcher: unknown) {
  vi.stubGlobal('fetch', fetcher);
  localStorage.clear();
  sessionStorage.clear();
  sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider><AppShellMountHarness /></AuthProvider></ApiProvider></I18nProvider>);
}


function CurrentPath() {
  return <output data-testid="current-path">{useLocation().pathname}</output>;
}

function HistoryControls() {
  const navigate = useNavigate();
  return <>
    <button type="button" onClick={() => navigate(-1)}>Test back</button>
    <button type="button" onClick={() => navigate(1)}>Test forward</button>
  </>;
}

function renderAuthenticatedRoute(path: string, fetcher: unknown, withHistoryControls = false) {
  vi.stubGlobal('fetch', fetcher);
  localStorage.clear();
  localStorage.setItem('caregiver-language', 'en');
  sessionStorage.clear();
  sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider>
    <MemoryRouter initialEntries={[path]}>
      {withHistoryControls && <HistoryControls />}
      <App />
      <CurrentPath />
    </MemoryRouter>
  </AuthProvider></ApiProvider></I18nProvider>);
}

function correctionHistoryFixture() {
  const effectiveObservations = Object.fromEntries(observationFields.map((field) => {
    const observation = noteFixture.observations[field];
    return [field, {
      state: observation.state,
      value: observation.value ?? null,
      rawValue: observation.rawValue ?? null,
      lastOperation: null,
      eventId: null,
      actorId: null,
      updatedAt: noteFixture.createdAt,
    }];
  }));
  return { originalNote: noteListFixture, events: [], effectiveObservations };
}

describe('application foundation', () => {
  it('renders the public landing page and route navigation', () => {
    renderRoute('/');
    expect(screen.getByRole('heading', { level: 1, name: 'Better records. More thoughtful care.' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Continue to sign in' })).toHaveAttribute('href', '/login');
  });
  it('protects the dashboard without an authenticated session', () => {
    renderRoute('/app');
    expect(screen.getByRole('heading', { name: 'Caregiver sign in' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Caregiver workspace' })).not.toBeInTheDocument();
  });
  it('switches language and document direction to Arabic and back', async () => {
    const user = userEvent.setup();
    renderRoute('/');
    await user.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));
    expect(screen.getByRole('heading', { name: 'سجلات أوضح. رعاية أكثر اهتمامًا.' })).toBeInTheDocument();
    expect(document.documentElement.lang).toBe('ar');
    expect(document.documentElement.dir).toBe('rtl');
    await user.click(screen.getByRole('button', { name: 'Switch to English' }));
    expect(document.documentElement.dir).toBe('ltr');
  });
  it('provides accessible loading, empty, error and validation states', () => {
    render(<I18nProvider><LoadingState /><EmptyState /><ErrorState /><FieldError id="field-error" /></I18nProvider>);
    expect(screen.getAllByRole('status')).toHaveLength(2);
    expect(screen.getAllByRole('alert')).toHaveLength(2);
    expect(screen.getByText('No items yet')).toBeInTheDocument();
    expect(screen.getByText('This field is required.')).toHaveAttribute('id', 'field-error');
  });
  it('renders the sign-in form and a return link', () => {
    renderRoute('/login');

    expect(screen.getByRole('heading', { level: 1, name: 'Caregiver sign in' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Email address' })).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toHaveAttribute('autocomplete', 'current-password');
    expect(screen.getByRole('link', { name: 'Return home' })).toHaveAttribute('href', '/');
  });
  it('returns to the landing page from an unknown route', async () => {
    const user = userEvent.setup();
    renderRoute('/page-that-does-not-exist');

    expect(screen.getByRole('heading', { level: 1, name: 'Page not found' })).toBeInTheDocument();
    await user.click(screen.getByRole('link', { name: 'Return home' }));

    expect(screen.getByRole('heading', { level: 1, name: 'Better records. More thoughtful care.' })).toBeInTheDocument();
  });
  it('marks the active navigation link and moves focus to main after navigation', async () => {
    const user = userEvent.setup();
    renderRoute('/');
    const navigation = within(screen.getByRole('navigation'));

    expect(navigation.getByRole('link', { name: 'Home' })).toHaveAttribute('aria-current', 'page');
    expect(navigation.getByRole('link', { name: 'Sign in' })).not.toHaveAttribute('aria-current');
    expect(window.scrollTo).not.toHaveBeenCalled();

    await user.click(navigation.getByRole('link', { name: 'Sign in' }));

    expect(screen.getByRole('heading', { level: 1, name: 'Caregiver sign in' })).toBeInTheDocument();
    expect(navigation.getByRole('link', { name: 'Sign in' })).toHaveAttribute('aria-current', 'page');
    expect(navigation.getByRole('link', { name: 'Home' })).not.toHaveAttribute('aria-current');
    expect(screen.getByRole('main')).toHaveFocus();
    expect(window.scrollTo).toHaveBeenCalledTimes(1);
    expect(window.scrollTo).toHaveBeenCalledWith(expect.objectContaining({ top: 0 }));
    expect(document.title).toBe('Caregiver sign in · Caregiver Daily Notes');

    await user.click(navigation.getByRole('link', { name: 'Home' }));

    expect(screen.getByRole('heading', { level: 1, name: 'Better records. More thoughtful care.' })).toBeInTheDocument();
    expect(navigation.getByRole('link', { name: 'Home' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('main')).toHaveFocus();
    expect(window.scrollTo).toHaveBeenCalledTimes(2);
    expect(document.title).toBe('Home · Caregiver Daily Notes');
  });
  it('provides a focusable main landmark as the skip-link target', () => {
    renderRoute('/');
    const main = screen.getByRole('main');

    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', `#${main.id}`);
    expect(main).toHaveAttribute('tabindex', '-1');
    main.focus();
    expect(main).toHaveFocus();
  });
  it('keeps Arabic active and persisted when navigating between routes', async () => {
    const user = userEvent.setup();
    renderRoute('/');

    await user.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));
    await user.click(within(screen.getByRole('navigation')).getByRole('link', { name: 'تسجيل الدخول' }));

    expect(screen.getByRole('heading', { level: 1, name: 'دخول مقدّم الرعاية' })).toBeInTheDocument();
    expect(document.documentElement.lang).toBe('ar');
    expect(document.documentElement.dir).toBe('rtl');
    expect(localStorage.getItem('caregiver-language')).toBe('ar');
    expect(document.title).toBe('دخول مقدّم الرعاية · ملاحظات الرعاية اليومية');

    await user.click(screen.getByRole('link', { name: 'العودة للرئيسية' }));

    expect(screen.getByRole('heading', { level: 1, name: 'سجلات أوضح. رعاية أكثر اهتمامًا.' })).toBeInTheDocument();
    expect(document.documentElement.dir).toBe('rtl');
    expect(document.title).toBe('الرئيسية · ملاحظات الرعاية اليومية');
  });
  it('restores stored Arabic when the application mounts', () => {
    localStorage.setItem('caregiver-language', 'ar');
    renderRoute('/');

    expect(screen.getByRole('heading', { level: 1, name: 'سجلات أوضح. رعاية أكثر اهتمامًا.' })).toBeInTheDocument();
    expect(document.documentElement.lang).toBe('ar');
    expect(document.documentElement.dir).toBe('rtl');
    expect(screen.getByRole('button', { name: 'Switch to English' })).toHaveAttribute('lang', 'en');
  });
  it('declares the language of the language-switch button', async () => {
    const user = userEvent.setup();
    renderRoute('/');

    expect(screen.getByRole('button', { name: 'التغيير إلى العربية' })).toHaveAttribute('lang', 'ar');
    await user.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));
    expect(screen.getByRole('button', { name: 'Switch to English' })).toHaveAttribute('lang', 'en');
  });

  it('shows four authenticated workspace destinations', async () => {
    const user = userEvent.setup();
    renderAuthenticatedRoute('/app', vi.fn().mockResolvedValue(jsonResponse([])));
    const navigation = screen.getByRole('navigation', { name: 'Main navigation' });
    const primary = within(navigation).getByRole('group', { name: 'Workspace navigation' });

    expect(within(primary).getAllByRole('link').map((link) => [link.textContent, link.getAttribute('href')])).toEqual([
      ['Home', '/app'],
      ['People', '/app/recipients'],
      ['Notes', '/app/notes/new'],
      ['History', '/app/notes'],
    ]);
    expect(navigation.querySelector('a[href="/"]')).toBeNull();
    expect(within(primary).getByRole('link', { name: 'Home' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'AI summary & safety' })).toHaveAttribute('href', '/app/notes/summary');

    await user.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));
    const arabicPrimary = within(navigation).getByRole('group', { name: 'قائمة مساحة العمل' });
    expect(within(arabicPrimary).getAllByRole('link').map((link) => link.textContent)).toEqual([
      'الرئيسية', 'المستفيدون', 'الملاحظات', 'سجل الملاحظات',
    ]);
  });

  it('keeps plans outside primary navigation', () => {
    renderAuthenticatedRoute('/app', vi.fn().mockResolvedValue(jsonResponse([])));
    const navigation = screen.getByRole('navigation', { name: 'Main navigation' });
    const primary = within(navigation).getByRole('group', { name: 'Workspace navigation' });
    const notesGroup = within(navigation).getByRole('group', { name: 'Notes navigation' });
    const plans = within(notesGroup).getByRole('link', { name: 'Care plans' });

    expect(within(primary).getAllByRole('link')).toHaveLength(4);
    expect(within(primary).queryByRole('link', { name: 'Care plans' })).not.toBeInTheDocument();
    expect(plans).toHaveAttribute('href', '/app/notes/plans');
    expect(primary.contains(plans)).toBe(false);
  });

  it.each([
    { path: '/app', activeLabel: 'Home' },
    { path: '/app/recipients', activeLabel: 'People' },
    { path: `/app/recipients/${recipientFixture.id}`, activeLabel: 'People' },
    { path: `/app/recipients/${recipientFixture.id}/edit`, activeLabel: 'People' },
    { path: '/app/notes/new', activeLabel: 'Notes' },
    { path: '/app/notes', activeLabel: 'History' },
    { path: '/app/notes/history', activeLabel: 'History' },
    { path: `/app/notes/${noteFixture.id}`, activeLabel: 'History' },
    { path: `/app/notes/${noteFixture.id}/addenda/new`, activeLabel: 'History' },
    { path: '/app/notes/summary' },
    { path: '/app/notes/plans' },
  ])('marks only the appropriate workspace destination active on $path', ({ path, activeLabel }) => {
    const view = renderAuthenticatedRoute(path, vi.fn().mockResolvedValue(jsonResponse([])));
    const primary = within(screen.getByRole('group', { name: 'Workspace navigation' }));
    const activeLinks = primary.getAllByRole('link').filter((link) => link.getAttribute('aria-current') === 'page');

    expect(activeLinks.map((link) => link.textContent)).toEqual(activeLabel ? [activeLabel] : []);
    view.unmount();
  });

  it('returns recipient detail to recipient list', async () => {
    const fetcher = vi.fn().mockImplementation((path: string) => Promise.resolve(
      jsonResponse(path.startsWith('/api/recipients?') ? [recipientFixture] : recipientFixture),
    ));
    renderAuthenticatedRoute(`/app/recipients/${recipientFixture.id}`, fetcher);
    expect(await screen.findByRole('heading', { name: recipientFixture.name })).toBeInTheDocument();

    const primary = screen.getByRole('group', { name: 'Workspace navigation' });
    expect(within(primary).getByRole('link', { name: 'People' })).toHaveAttribute('aria-current', 'page');
    const back = screen.getByRole('link', { name: 'Back to care recipients' });
    expect(back).toBeVisible();
    expect(back).toHaveAttribute('href', '/app/recipients');
    await userEvent.click(back);
    await waitFor(() => expect(screen.getByTestId('current-path').textContent).toBe('/app/recipients'));
  });

  it('opens recipient detail from a direct route', async () => {
    const fetcher = vi.fn().mockImplementation((path: string) => Promise.resolve(
      jsonResponse(path.startsWith('/api/recipients?') ? [recipientFixture] : recipientFixture),
    ));
    renderAuthenticatedRoute(`/app/recipients/${recipientFixture.id}`, fetcher);

    expect(await screen.findByRole('heading', { name: recipientFixture.name })).toBeVisible();
    expect(screen.getByRole('heading', { name: 'Recipient details' })).toBeVisible();
    expect(document.querySelector('.recipient-list')).toBeNull();
    expect(screen.getByTestId('current-path')).toHaveTextContent(`/app/recipients/${recipientFixture.id}`);
  });

  it('opens note detail from a direct route', async () => {
    const correctionHistory = correctionHistoryFixture();
    const fetcher = vi.fn().mockImplementation((path: string) => {
      if (path.startsWith('/api/recipients?') || path === '/api/recipients') return Promise.resolve(jsonResponse([recipientFixture]));
      if (path.startsWith('/api/recipients/')) return Promise.resolve(jsonResponse(recipientFixture));
      if (path.endsWith('/addenda')) return Promise.resolve(jsonResponse([]));
      if (path.endsWith('/corrections')) return Promise.resolve(jsonResponse(correctionHistory));
      if (path.startsWith('/api/notes?')) return Promise.resolve(jsonResponse({ content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 }));
      return Promise.resolve(jsonResponse(noteDetailFixture));
    });
    renderAuthenticatedRoute(`/app/notes/${noteFixture.id}`, fetcher);

    expect(await screen.findByRole('heading', { name: 'Original note — read only' })).toBeVisible();
    expect(screen.getByText(/غير متأكد من الدواء\./)).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Notes history' })).not.toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Record a daily note' })).not.toBeInTheDocument();
    expect(screen.getByTestId('current-path')).toHaveTextContent(`/app/notes/${noteFixture.id}`);
  });

  it('opens note addendum route directly', async () => {
    const fetcher = vi.fn().mockImplementation((path: string) => {
      if (path.startsWith('/api/recipients/')) return Promise.resolve(jsonResponse(recipientFixture));
      if (path.endsWith('/addenda')) return Promise.resolve(jsonResponse([]));
      if (path.endsWith('/corrections')) return Promise.resolve(jsonResponse(correctionHistoryFixture()));
      return Promise.resolve(jsonResponse(noteDetailFixture));
    });
    renderAuthenticatedRoute(`/app/notes/${noteFixture.id}/addenda/new`, fetcher);

    expect(await screen.findByRole('heading', { name: 'Add a correction' })).toBeVisible();
    expect(screen.getByRole('form', { name: 'Add a correction' })).toBeVisible();
    expect(screen.getByRole('button', { name: 'Save addendum' })).toBeVisible();
    expect(screen.getByRole('link', { name: 'Back to this note' })).toHaveAttribute('href', `/app/notes/${noteFixture.id}`);
    expect(screen.queryByRole('heading', { name: 'Daily note details' })).not.toBeInTheDocument();
    expect(screen.getByTestId('current-path')).toHaveTextContent(`/app/notes/${noteFixture.id}/addenda/new`);
    expect(fetcher).toHaveBeenCalledWith(`/api/notes/${noteFixture.id}`, expect.anything());
  });

  it('shows recipient not found for an unknown recipient child route', () => {
    renderAuthenticatedRoute('/app/recipients/unknown/invalid-child', vi.fn());

    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeVisible();
    expect(screen.getByText(/This recipient is unavailable\./)).toBeVisible();
    expect(screen.queryByText(/We couldn’t find that page\./)).not.toBeInTheDocument();
    expect(screen.getByTestId('current-path')).toHaveTextContent('/app/recipients/unknown/invalid-child');
  });

  it('shows notes not found for an unknown note child route', () => {
    renderAuthenticatedRoute('/app/notes/unknown/invalid-child', vi.fn());

    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeVisible();
    expect(screen.getByText('This note or recipient is not available. Choose a record from your workspace.')).toBeVisible();
    expect(screen.queryByText(/We couldn’t find that page\./)).not.toBeInTheDocument();
    expect(screen.getByTestId('current-path')).toHaveTextContent('/app/notes/unknown/invalid-child');
  });

  it('restores previous caregiver routes with Back and Forward without storing resource IDs', async () => {
    const user = userEvent.setup();
    const fetcher = vi.fn().mockImplementation((path: string) => Promise.resolve(
      jsonResponse(path.startsWith('/api/recipients?') ? [recipientFixture] : recipientFixture),
    ));
    renderAuthenticatedRoute('/app/recipients', fetcher, true);
    expect(await screen.findByRole('heading', { name: 'Care recipients' })).toBeVisible();

    await user.click(screen.getByRole('link', { name: recipientFixture.name }));
    expect(await screen.findByRole('heading', { name: 'Samir Hassan' })).toBeVisible();
    expect(screen.getByTestId('current-path')).toHaveTextContent(`/app/recipients/${recipientFixture.id}`);

    await user.click(screen.getByRole('link', { name: 'Edit recipient' }));
    expect(await screen.findByRole('heading', { name: 'Edit recipient' })).toBeVisible();
    expect(screen.getByTestId('current-path')).toHaveTextContent(`/app/recipients/${recipientFixture.id}/edit`);
    expect(JSON.stringify(localStorage)).not.toContain(recipientFixture.id);

    await user.click(screen.getByRole('button', { name: 'Test back' }));
    expect(await screen.findByRole('heading', { name: 'Recipient details' })).toBeVisible();
    expect(screen.getByTestId('current-path')).toHaveTextContent(`/app/recipients/${recipientFixture.id}`);

    await user.click(screen.getByRole('button', { name: 'Test forward' }));
    expect(await screen.findByRole('heading', { name: 'Edit recipient' })).toBeVisible();
    expect(screen.getByTestId('current-path')).toHaveTextContent(`/app/recipients/${recipientFixture.id}/edit`);
    expect(JSON.stringify(localStorage)).not.toContain(recipientFixture.id);
  });

  it('returns note detail to history', async () => {
    const historyPage = { content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 };
    const correctionHistory = correctionHistoryFixture();
    const fetcher = vi.fn().mockImplementation((path: string) => {
      if (path.startsWith('/api/recipients?') || path === '/api/recipients') return Promise.resolve(jsonResponse([recipientFixture]));
      if (path.startsWith('/api/recipients/')) return Promise.resolve(jsonResponse(recipientFixture));
      if (path.endsWith('/addenda')) return Promise.resolve(jsonResponse([]));
      if (path.endsWith('/corrections')) return Promise.resolve(jsonResponse(correctionHistory));
      if (path.startsWith('/api/notes?')) return Promise.resolve(jsonResponse(historyPage));
      return Promise.resolve(jsonResponse(noteDetailFixture));
    });
    renderAuthenticatedRoute(`/app/notes/${noteFixture.id}`, fetcher);
    expect(await screen.findByRole('heading', { name: 'Original note — read only' })).toBeInTheDocument();

    const notesPage = document.querySelector('.notes-page');
    expect(notesPage).not.toBeNull();
    const history = within(notesPage as HTMLElement).getByRole('link', { name: 'Notes history' });
    expect(history).toHaveAttribute('href', '/app/notes');
    await userEvent.click(history);
    await waitFor(() => expect(screen.getByTestId('current-path').textContent).toBe('/app/notes'));
  });

  it.each([
    '/app/recipients/a1111111-1111-4111-8111-111111111111',
    '/app/notes/d4444444-4444-4444-8444-444444444444',
    '/app/notes/d4444444-4444-4444-8444-444444444444/addenda/new',
  ])('protects direct caregiver route %s when unauthenticated', (path) => {
    const fetcher = vi.fn();
    vi.stubGlobal('fetch', fetcher);
    renderRoute(path);

    expect(screen.getByRole('heading', { name: 'Caregiver sign in' })).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Recipient details' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Original note — read only' })).not.toBeInTheDocument();
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('renders the authenticated application without adding a settings navigation link', () => {
    localStorage.clear();
    localStorage.setItem('caregiver-language', 'en');
    sessionStorage.clear();
    sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
    const { unmount } = render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider>
      <MemoryRouter initialEntries={['/app']}><App /></MemoryRouter>
    </AuthProvider></ApiProvider></I18nProvider>);

    expect(screen.getByRole('navigation', { name: 'Main navigation' })).toBeVisible();
    expect(screen.queryByRole('link', { name: /settings/i })).not.toBeInTheDocument();
    unmount();
  });

  it('opens the AI settings dialog under its provider and offers only the supported providers', () => {
    render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><ByokProvider>
      <AiSettingsDialog open />
      <ByokStateProbe />
    </ByokProvider></ApiProvider></I18nProvider>);
    expect(screen.getByRole('dialog', { name: 'AI provider settings' })).toBeVisible();
    expect(Array.from(screen.getByRole('combobox', { name: 'Provider' }).querySelectorAll('option'), (option) => option.value))
      .toEqual(['OPENAI', 'GEMINI', 'XAI', 'OPEN_ROUTER']);
  });

  it('stores credentials only after a successful connection test', async () => {
    const user = userEvent.setup();
    const fetcher = vi.fn().mockResolvedValue(jsonResponse({ success: true, providerId: 'GEMINI', model: 'gemini-3.8-flash', errorCode: null }));
    renderByokDialogUnderProvider(fetcher);
    await user.selectOptions(screen.getByLabelText('Provider'), 'GEMINI');
    await user.type(screen.getByLabelText('Model'), 'gemini-3.8-flash');
    await user.type(screen.getByLabelText('API key'), 'synthetic-secret-123');
    expect(screen.getByTestId('byok-state')).toHaveTextContent('empty');
    expect(screen.getByTestId('byok-active')).toHaveTextContent('none');
    expect(screen.getByText('No verified credentials are active.')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Test connection' }));

    expect(await screen.findByText(/Connection test succeeded/)).toBeVisible();
    expect(screen.getByTestId('byok-state')).toHaveTextContent('configured');
    expect(screen.getByTestId('byok-active')).toHaveTextContent('GEMINI · gemini-3.8-flash');
    expect(fetcher).toHaveBeenCalledWith('/api/ai/byok/connection-test', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ credentials: { providerId: 'GEMINI', model: 'gemini-3.8-flash', apiKey: 'synthetic-secret-123' } }),
    }));
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it('does not store credentials after a failed connection test or expose provider text', async () => {
    const user = userEvent.setup();
    const response = jsonResponse({ success: false, errorCode: 'invalid_key', providerMessage: 'provider text', detail: 'synthetic-secret-123' }, 400);
    const fetcher = vi.fn().mockResolvedValue(response);
    renderByokDialogUnderProvider(fetcher);
    await user.type(screen.getByLabelText('Model'), 'bad-model');
    await user.type(screen.getByLabelText('API key'), 'synthetic-secret-123');
    await user.click(screen.getByRole('button', { name: 'Test connection' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('The provider rejected this key.');
    expect(screen.getByRole('alert')).toHaveTextContent('These credentials were not saved or activated.');
    expect(screen.getByTestId('byok-state')).toHaveTextContent('empty');
    expect(screen.getByTestId('byok-active')).toHaveTextContent('none');
    expect(screen.getByRole('alert')).not.toHaveTextContent('synthetic-secret-123');
    expect(screen.getByRole('alert')).not.toHaveTextContent('provider text');
    expect(screen.getByRole('dialog')).not.toHaveTextContent('synthetic-secret-123');
  });

  it('preserves verified provider credentials when a replacement test fails', async () => {
    const user = userEvent.setup();
    const fetcher = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ success: true }))
      .mockResolvedValueOnce(jsonResponse({ success: false, errorCode: 'invalid_key' }, 400));
    renderByokDialogUnderProvider(fetcher);
    await user.selectOptions(screen.getByLabelText('Provider'), 'OPENAI');
    await user.type(screen.getByLabelText('Model'), 'gpt-4o-mini');
    await user.type(screen.getByLabelText('API key'), 'active-secret');
    await user.click(screen.getByRole('button', { name: 'Test connection' }));
    expect(await screen.findByText(/Connection test succeeded/)).toBeVisible();
    expect(screen.getByTestId('byok-active')).toHaveTextContent('OPENAI · gpt-4o-mini');

    await user.selectOptions(screen.getByLabelText('Provider'), 'GEMINI');
    await user.clear(screen.getByLabelText('Model'));
    await user.type(screen.getByLabelText('Model'), 'gemini-test');
    await user.clear(screen.getByLabelText('API key'));
    await user.type(screen.getByLabelText('API key'), 'replacement-secret');
    expect(screen.getByTestId('byok-active')).toHaveTextContent('OPENAI · gpt-4o-mini');
    await user.click(screen.getByRole('button', { name: 'Test connection' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('The provider rejected this key.');
    expect(screen.getByRole('alert')).toHaveTextContent('your previously verified provider remains active');
    expect(screen.getByTestId('byok-state')).toHaveTextContent('configured');
    expect(screen.getByTestId('byok-active')).toHaveTextContent('OPENAI · gpt-4o-mini');
    expect(screen.getByTestId('byok-key-preserved')).toHaveTextContent('yes');
    expect(screen.getByRole('dialog')).not.toHaveTextContent('active-secret');
    expect(screen.getByRole('dialog')).not.toHaveTextContent('replacement-secret');
    expect(screen.getByRole('alert')).not.toHaveTextContent('replacement-secret');
  });

  it('replaces active credentials only after a replacement test succeeds', async () => {
    const user = userEvent.setup();
    const fetcher = vi.fn()
      .mockResolvedValueOnce(jsonResponse({ success: true }))
      .mockResolvedValueOnce(jsonResponse({ success: true }));
    renderByokDialogUnderProvider(fetcher);
    await user.selectOptions(screen.getByLabelText('Provider'), 'OPENAI');
    await user.type(screen.getByLabelText('Model'), 'gpt-4o-mini');
    await user.type(screen.getByLabelText('API key'), 'active-secret');
    await user.click(screen.getByRole('button', { name: 'Test connection' }));
    expect(await screen.findByText(/Connection test succeeded/)).toBeVisible();

    await user.selectOptions(screen.getByLabelText('Provider'), 'XAI');
    await user.clear(screen.getByLabelText('Model'));
    await user.type(screen.getByLabelText('Model'), 'grok-test');
    await user.clear(screen.getByLabelText('API key'));
    await user.type(screen.getByLabelText('API key'), 'replacement-secret');
    expect(screen.getByTestId('byok-active')).toHaveTextContent('OPENAI · gpt-4o-mini');
    expect(screen.queryByText(/Connection test succeeded/)).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Test connection' }));

    expect(await screen.findByText(/Connection test succeeded/)).toBeVisible();
    expect(screen.getByTestId('byok-active')).toHaveTextContent('XAI · grok-test');
    expect(screen.getByTestId('byok-key-preserved')).toHaveTextContent('no');
    expect(screen.getByRole('dialog')).not.toHaveTextContent('replacement-secret');
  });

  it('shows an in-progress state without replacing active credentials', async () => {
    const user = userEvent.setup();
    let resolveResponse!: (response: Response) => void;
    const fetcher = vi.fn().mockImplementation(() => new Promise<Response>((resolve) => { resolveResponse = resolve; }));
    renderByokDialogUnderProvider(fetcher);
    await user.type(screen.getByLabelText('Model'), 'gpt-test');
    await user.type(screen.getByLabelText('API key'), 'synthetic-secret-123');
    await user.click(screen.getByRole('button', { name: 'Test connection' }));

    expect(screen.getByRole('button', { name: 'Testing connection…' })).toBeDisabled();
    expect(screen.getByTestId('byok-active')).toHaveTextContent('none');
    resolveResponse(jsonResponse({ success: true }));
    expect(await screen.findByText(/Connection test succeeded/)).toBeVisible();
  });

  it('uses a generic safe message for unsupported error codes', async () => {
    const user = userEvent.setup();
    const fetcher = vi.fn().mockResolvedValue(jsonResponse({
      success: false, errorCode: 'unexpected_error', providerMessage: 'unsafe provider detail',
    }, 400));
    renderByokDialogUnderProvider(fetcher);
    await user.type(screen.getByLabelText('Model'), 'unknown-model');
    await user.type(screen.getByLabelText('API key'), 'synthetic-secret-123');
    await user.click(screen.getByRole('button', { name: 'Test connection' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('The provider is temporarily unavailable.');
    expect(screen.getByRole('alert')).toHaveTextContent('These credentials were not saved or activated.');
    expect(screen.getByRole('alert')).not.toHaveTextContent('unsafe provider detail');
    expect(screen.getByRole('dialog')).not.toHaveTextContent('synthetic-secret-123');
  });

  it('handles network errors without exposing exception details', async () => {
    const user = userEvent.setup();
    const fetcher = vi.fn().mockRejectedValue(new Error('unsafe network details'));
    renderByokDialogUnderProvider(fetcher);
    await user.type(screen.getByLabelText('Model'), 'network-model');
    await user.type(screen.getByLabelText('API key'), 'synthetic-secret-123');
    await user.click(screen.getByRole('button', { name: 'Test connection' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('The provider is temporarily unavailable.');
    expect(screen.getByRole('alert')).not.toHaveTextContent('unsafe network details');
    expect(screen.getByTestId('byok-state')).toHaveTextContent('empty');
  });

  it('removes the key from memory and clears the password field', async () => {
    const user = userEvent.setup();
    const fetcher = vi.fn().mockResolvedValue(jsonResponse({ success: true }));
    renderByokDialogUnderProvider(fetcher);
    await user.type(screen.getByLabelText('Model'), 'gpt-test-model');
    await user.type(screen.getByLabelText('API key'), 'synthetic-secret-123');
    await user.click(screen.getByRole('button', { name: 'Test connection' }));
    expect(await screen.findByTestId('byok-state')).toHaveTextContent('configured');

    await user.click(screen.getByRole('button', { name: 'Remove key' }));

    expect(screen.getByLabelText('API key')).toHaveValue('');
    expect(screen.getByTestId('byok-state')).toHaveTextContent('empty');
    expect(screen.queryByText(/Connection test succeeded/)).not.toBeInTheDocument();
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it('does not write BYOK credentials to browser storage', async () => {
    const user = userEvent.setup();
    const storage = vi.spyOn(Storage.prototype, 'setItem');
    renderByokDialogUnderProvider(vi.fn().mockResolvedValue(jsonResponse({ success: true })));
    await user.type(screen.getByLabelText('Model'), 'gpt-test-model');
    await user.type(screen.getByLabelText('API key'), 'synthetic-secret-123');
    await user.click(screen.getByRole('button', { name: 'Test connection' }));

    expect(await screen.findByTestId('byok-state')).toHaveTextContent('configured');
    expect(storage.mock.calls.flat().join(' ')).not.toContain('synthetic-secret-123');
    storage.mockRestore();
  });

  it('clears BYOK credentials from the authenticated shell when signing out', async () => {
    const user = userEvent.setup();
    renderByokLifecycle(vi.fn());
    await user.click(screen.getByRole('button', { name: 'Configure test credentials' }));
    expect(screen.getByTestId('shell-byok-state')).toHaveTextContent('configured');

    await user.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(screen.getByTestId('shell-byok-state')).toHaveTextContent('empty');
  });

  it('clears BYOK credentials when authentication is rejected', async () => {
    const user = userEvent.setup();
    renderByokLifecycle(vi.fn().mockResolvedValue(jsonResponse({ code: 'UNAUTHORIZED' }, 401)));
    await user.click(screen.getByRole('button', { name: 'Configure test credentials' }));
    await user.click(screen.getByRole('button', { name: 'Request private data' }));

    expect(await screen.findByTestId('shell-byok-state')).toHaveTextContent('empty');
    expect(sessionStorage.getItem('caregiver-auth-session-v1')).toBeNull();
  });

  it('destroys BYOK credentials when the authenticated shell unmounts', async () => {
    const user = userEvent.setup();
    renderByokShellUnmount(vi.fn());
    await user.click(screen.getByRole('button', { name: 'Configure test credentials' }));
    expect(screen.getByTestId('shell-byok-state')).toHaveTextContent('configured');
    await user.click(screen.getByRole('button', { name: 'Unmount authenticated shell' }));
    await user.click(screen.getByRole('button', { name: 'Mount authenticated shell' }));

    expect(screen.getByTestId('shell-byok-state')).toHaveTextContent('empty');
  });

});
