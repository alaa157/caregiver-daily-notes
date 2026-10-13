import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AuthProvider } from './AuthContext';
import { I18nProvider } from '../i18n/I18nProvider';
import { EmptyState, ErrorState, FieldError, LoadingState } from '../components/Feedback';
import { App } from './App';

function renderRoute(path: string) {
  return render(<I18nProvider><AuthProvider><MemoryRouter initialEntries={[path]}><App /></MemoryRouter></AuthProvider></I18nProvider>);
}

describe('application foundation', () => {
  it('renders the public landing page and route navigation', () => {
    renderRoute('/');
    expect(screen.getByRole('heading', { level: 1, name: 'Better records. More thoughtful care.' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Continue to sign in' })).toHaveAttribute('href', '/login');
  });
  it('protects the dashboard before Task 2 implements real authentication', () => {
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
  it('renders the sign-in route with its current availability and a return link', () => {
    renderRoute('/login');

    expect(screen.getByRole('heading', { level: 1, name: 'Caregiver sign in' })).toBeInTheDocument();
    expect(screen.getByRole('status')).toBeInTheDocument();
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
});
