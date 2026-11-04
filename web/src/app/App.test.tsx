import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthContext';
import { I18nProvider } from '../i18n/I18nProvider';
import { EmptyState, ErrorState, FieldError, LoadingState } from '../components/Feedback';
import { App } from './App';
import { ApiProvider } from '../lib/api';
import { observationFields } from '../api/notes';
import { jsonResponse, testToken } from '../tests/authFixtures';
import { recipientFixture } from '../tests/recipientFixtures';
import { noteFixture } from '../tests/noteFixtures';

function renderRoute(path: string) {
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider><MemoryRouter initialEntries={[path]}><App /></MemoryRouter></AuthProvider></ApiProvider></I18nProvider>);
}

function CurrentPath() {
  return <output data-testid="current-path">{useLocation().pathname}</output>;
}

function renderAuthenticatedRoute(path: string, fetcher: unknown) {
  vi.stubGlobal('fetch', fetcher);
  localStorage.setItem('caregiver-language', 'en');
  sessionStorage.clear();
  sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider>
    <MemoryRouter initialEntries={[path]}><App /><CurrentPath /></MemoryRouter>
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
  return { originalNote: noteFixture, events: [], effectiveObservations };
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

  it('returns note detail to history', async () => {
    const historyPage = { content: [], number: 0, size: 20, totalElements: 0, totalPages: 0 };
    const correctionHistory = correctionHistoryFixture();
    const fetcher = vi.fn().mockImplementation((path: string) => {
      if (path.startsWith('/api/recipients?') || path === '/api/recipients') return Promise.resolve(jsonResponse([recipientFixture]));
      if (path.startsWith('/api/recipients/')) return Promise.resolve(jsonResponse(recipientFixture));
      if (path.endsWith('/addenda')) return Promise.resolve(jsonResponse([]));
      if (path.endsWith('/corrections')) return Promise.resolve(jsonResponse(correctionHistory));
      if (path.startsWith('/api/notes?')) return Promise.resolve(jsonResponse(historyPage));
      return Promise.resolve(jsonResponse(noteFixture));
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
});
