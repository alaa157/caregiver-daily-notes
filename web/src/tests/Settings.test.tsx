import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { App } from '../app/App';
import { AuthProvider } from '../features/auth/AuthProvider';
import { I18nProvider } from '../i18n/I18nProvider';
import { ApiProvider } from '../lib/api';
import { jsonResponse, testToken } from './authFixtures';

const labels = {
  en: {
    groups: ['Interface', 'Privacy and security', 'Storage and data', 'System and about'],
    unavailable: ['Privacy', 'Storage', 'Backup and restore', 'Supported links', 'Updater', 'Change log', 'About', 'Version check'],
  },
  ar: {
    groups: ['الواجهة', 'الخصوصية والأمان', 'التخزين والبيانات', 'النظام ومعلومات التطبيق'],
    unavailable: ['الخصوصية', 'التخزين', 'النسخ الاحتياطي والاستعادة', 'الروابط المدعومة', 'محدّث التطبيق', 'سجل التغييرات', 'حول التطبيق', 'التحقق من الإصدار'],
  },
} as const;

function renderSettings(path = '/app/settings', language = 'en', authenticated = true, fetcher = vi.fn()) {
  vi.stubGlobal('fetch', fetcher);
  localStorage.clear();
  localStorage.setItem('caregiver-language', language);
  sessionStorage.clear();
  if (authenticated) sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
  return render(
    <I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider>
      <MemoryRouter initialEntries={[path]}><App /><Link to="/app/settings">Settings test link</Link></MemoryRouter>
    </AuthProvider></ApiProvider></I18nProvider>,
  );
}

describe('authenticated grouped settings page', () => {
  it('renders settings route only for authenticated caregiver', () => {
    renderSettings();

    expect(screen.getByRole('heading', { level: 1, name: 'Settings' })).toBeVisible();
    for (const heading of labels.en.groups) expect(screen.getByRole('heading', { name: heading, level: 2 })).toBeVisible();
  });

  it('redirects unauthenticated users away from settings', () => {
    renderSettings('/app/settings', 'en', false);

    expect(screen.getByRole('heading', { name: 'Caregiver sign in' })).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Settings' })).not.toBeInTheDocument();
    expect(screen.queryByText('Privacy and security')).not.toBeInTheDocument();
  });

  it('shows all four settings groups', () => {
    renderSettings();

    expect(labels.en.groups.map((heading) => screen.getByRole('heading', { name: heading, level: 2 }).textContent)).toEqual(labels.en.groups);
    for (const heading of labels.en.groups) {
      expect(screen.getByRole('region', { name: heading })).toHaveClass('note-surface');
    }
  });

  it('opens the existing AI settings dialog and closes back to Settings', async () => {
    const user = userEvent.setup();
    renderSettings();

    await user.click(screen.getByRole('button', { name: 'AI Settings' }));
    const dialog = screen.getByRole('dialog', { name: 'AI provider settings' });
    expect(dialog).toBeVisible();
    expect(within(dialog).getByLabelText('Provider')).toBeVisible();
    expect(within(dialog).getByLabelText('API key')).toHaveAttribute('type', 'password');

    await user.click(within(dialog).getByRole('button', { name: 'Close' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Settings' })).toBeVisible();
  });

  it('preserves active BYOK credentials when closing the dialog and navigating away and back', async () => {
    const user = userEvent.setup();
    const fetcher = vi.fn().mockResolvedValue(jsonResponse({ success: true }));
    renderSettings('/app/settings', 'en', true, fetcher);

    await user.click(screen.getByRole('button', { name: 'AI Settings' }));
    const dialog = screen.getByRole('dialog', { name: 'AI provider settings' });
    await user.selectOptions(within(dialog).getByLabelText('Provider'), 'OPENAI');
    await user.type(within(dialog).getByLabelText('Model'), 'gpt-test');
    await user.type(within(dialog).getByLabelText('API key'), 'settings-test-secret');
    await user.click(within(dialog).getByRole('button', { name: 'Test connection' }));
    expect(await within(dialog).findByRole('status')).toHaveTextContent('Connection test succeeded');

    await user.click(within(dialog).getByRole('button', { name: 'Close' }));
    expect(document.body).not.toHaveTextContent('settings-test-secret');
    await user.click(screen.getByRole('link', { name: 'Home' }));
    expect(await screen.findByRole('heading', { name: 'Caregiver workspace' })).toBeVisible();
    await user.click(screen.getByRole('link', { name: 'Settings' }));
    await user.click(screen.getByRole('button', { name: 'AI Settings' }));

    const reopened = screen.getByRole('dialog', { name: 'AI provider settings' });
    const activeCredentials = within(reopened).getByRole('region', { name: 'Currently active credentials' });
    expect(activeCredentials).toHaveTextContent('OpenAI · gpt-test');
    expect(activeCredentials.querySelector('bdi')).toHaveTextContent('gpt-test');
    expect(document.body).not.toHaveTextContent('settings-test-secret');
    expect(fetcher).toHaveBeenCalledWith('/api/ai/byok/connection-test', expect.objectContaining({ method: 'POST' }));
  });

  it('marks unimplemented settings as unavailable and keeps them non-interactive', () => {
    renderSettings();

    for (const label of labels.en.unavailable) {
      const row = screen.getByText(label, { exact: true }).closest('li');
      expect(row).not.toBeNull();
      expect(within(row!).getByText('Not available yet')).toBeVisible();
      expect(within(row!).queryByRole('button')).not.toBeInTheDocument();
      expect(within(row!).queryByRole('link')).not.toBeInTheDocument();
    }
  });

  it('shows the theme preview controls inside the existing Theme and Colors entry', () => {
    renderSettings();

    const row = screen.getByText('Theme and Colors', { exact: true }).closest('li');
    expect(row).toHaveAttribute('data-settings-theme-preview-entry');
    expect(within(row!).queryByText('Not available yet')).not.toBeInTheDocument();
    expect(within(row!).getByRole('group', { name: 'Choose a theme mode' })).toBeVisible();
    expect(within(row!).getByRole('radio', { name: 'System' })).toBeVisible();
    expect(within(row!).getByRole('radio', { name: 'Light' })).toBeVisible();
    expect(within(row!).getByRole('radio', { name: 'Dark' })).toBeVisible();
    expect(within(row!).getAllByRole('radio')).toHaveLength(3);
    expect(within(row!).getByRole('button', { name: 'Apply' })).toBeVisible();
    expect(within(row!).getByRole('button', { name: 'Cancel' })).toBeVisible();
    expect(within(row!).queryByRole('link')).not.toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: /theme/i })).not.toBeInTheDocument();
  });

  it('renders translated settings and navigation in Arabic RTL', async () => {
    const user = userEvent.setup();
    renderSettings('/app/settings', 'en');

    await user.click(screen.getByRole('button', { name: 'التغيير إلى العربية' }));
    await waitFor(() => {
      expect(document.documentElement.lang).toBe('ar');
      expect(document.documentElement.dir).toBe('rtl');
    });
    expect(screen.getByRole('heading', { level: 1, name: 'الإعدادات' })).toBeVisible();
    for (const heading of labels.ar.groups) expect(screen.getByRole('heading', { level: 2, name: heading })).toBeVisible();
    expect(screen.getByRole('link', { name: 'الإعدادات' })).toHaveAttribute('href', '/app/settings');
    expect(screen.getByRole('button', { name: 'إعدادات الذكاء الاصطناعي' })).toBeVisible();
    expect(screen.getByText('المظهر والألوان')).toBeVisible();
    expect(screen.getAllByText('غير متاح حتى الآن')).toHaveLength(8);
    for (const label of labels.ar.unavailable) expect(screen.getByText(label, { exact: true })).toBeVisible();
  });

  it('marks Settings navigation active and preserves workspace destinations', async () => {
    const user = userEvent.setup();
    renderSettings();
    const navigation = screen.getByRole('navigation', { name: 'Main navigation' });
    const settings = within(navigation).getByRole('link', { name: 'Settings' });

    expect(settings).toHaveAttribute('href', '/app/settings');
    expect(settings).toHaveAttribute('aria-current', 'page');
    expect(within(navigation).getByRole('link', { name: 'Home' })).toHaveAttribute('href', '/app');
    expect(within(navigation).getByRole('link', { name: 'People' })).toHaveAttribute('href', '/app/recipients');
    expect(within(navigation).getByRole('link', { name: 'Notes' })).toHaveAttribute('href', '/app/notes/new');
    expect(within(navigation).getByRole('link', { name: 'History' })).toHaveAttribute('href', '/app/notes');
    expect(within(navigation).getByRole('link', { name: 'Care plans' })).toHaveAttribute('href', '/app/notes/plans');
    expect(document.title).toBe('Settings · Caregiver Daily Notes');

    await user.click(within(navigation).getByRole('link', { name: 'Home' }));
    expect(await screen.findByRole('heading', { name: 'Caregiver workspace' })).toBeVisible();
    await user.click(within(navigation).getByRole('link', { name: 'Settings' }));
    expect(await screen.findByRole('heading', { name: 'Settings' })).toBeVisible();
  });
});
