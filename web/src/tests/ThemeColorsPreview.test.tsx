import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ThemeProvider, useTheme, type ThemeMode } from '../app/ThemeProvider';
import { ThemeColorsPreview } from '../features/settings/ThemeColorsPreview';
import { I18nProvider } from '../i18n/I18nProvider';

const themeStorageKey = 'caregiver-theme-mode';

function ExternalModeHarness() {
  const { setMode } = useTheme();
  return (
    <>
      <button type="button" onClick={() => setMode('light')}>Set applied Light</button>
      <button type="button" onClick={() => setMode('dark')}>Set applied Dark</button>
      <ThemeColorsPreview />
    </>
  );
}

function renderPreview({
  initialMode = 'system',
  language = 'en',
  externalModeControls = false,
}: {
  initialMode?: ThemeMode;
  language?: 'en' | 'ar';
  externalModeControls?: boolean;
} = {}) {
  localStorage.clear();
  localStorage.setItem(themeStorageKey, initialMode);
  localStorage.setItem('caregiver-language', language);

  return render(
    <I18nProvider>
      <ThemeProvider>
        {externalModeControls ? <ExternalModeHarness /> : <ThemeColorsPreview />}
      </ThemeProvider>
    </I18nProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  localStorage.clear();
  delete document.documentElement.dataset.theme;
});

describe('ThemeColorsPreview', () => {
  it('shows system light and dark preview choices', () => {
    renderPreview();

    expect(screen.getByRole('radio', { name: 'System' })).toBeVisible();
    expect(screen.getByRole('radio', { name: 'Light' })).toBeVisible();
    expect(screen.getByRole('radio', { name: 'Dark' })).toBeVisible();
    expect(screen.getAllByRole('radio')).toHaveLength(3);
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
  });

  it('does not persist preview choice before apply', async () => {
    const user = userEvent.setup();
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    renderPreview();
    setItem.mockClear();

    await user.click(screen.getByRole('radio', { name: 'Dark' }));

    expect(screen.getByRole('radio', { name: 'Dark' })).toBeChecked();
    expect(document.documentElement).toHaveAttribute('data-theme', 'system');
    expect(localStorage.getItem(themeStorageKey)).toBe('system');
    expect(setItem.mock.calls.filter(([key]) => key === themeStorageKey)).toEqual([]);
    expect(screen.getByTitle('Theme preview').getAttribute('srcdoc')).toContain('data-theme="dark"');
  });

  it('applies selected theme mode', async () => {
    const user = userEvent.setup();
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    renderPreview();
    setItem.mockClear();

    await user.click(screen.getByRole('radio', { name: 'Light' }));
    expect(document.documentElement).toHaveAttribute('data-theme', 'system');
    expect(setItem.mock.calls.filter(([key]) => key === themeStorageKey)).toEqual([]);

    await user.click(screen.getByRole('button', { name: 'Apply' }));

    await waitFor(() => {
      expect(document.documentElement).toHaveAttribute('data-theme', 'light');
    });
    expect(localStorage.getItem(themeStorageKey)).toBe('light');
    expect(setItem.mock.calls.filter(([key]) => key === themeStorageKey)).toEqual([[themeStorageKey, 'light']]);
  });

  it('cancel restores applied theme selection', async () => {
    const user = userEvent.setup();
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    renderPreview();
    setItem.mockClear();

    await user.click(screen.getByRole('radio', { name: 'Dark' }));
    setItem.mockClear();
    await user.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(screen.getByRole('radio', { name: 'System' })).toBeChecked();
    expect(document.documentElement).toHaveAttribute('data-theme', 'system');
    expect(localStorage.getItem(themeStorageKey)).toBe('system');
    expect(setItem.mock.calls.filter(([key]) => key === themeStorageKey)).toEqual([]);
    expect(screen.getByTitle('Theme preview').getAttribute('srcdoc')).toContain('data-theme="system"');
  });

  it('renders preview using shared semantic tokens', () => {
    renderPreview();
    const previewDocument = screen.getByTitle('Theme preview').getAttribute('srcdoc') ?? '';

    expect(previewDocument).toContain('background: var(--color-background)');
    expect(previewDocument).toContain('color: var(--color-on-background)');
    expect(previewDocument).toContain('background: var(--color-surface)');
    expect(previewDocument).toContain('color: var(--color-on-surface)');
    expect(previewDocument).toContain('background: var(--color-primary)');
    expect(previewDocument).toContain('color: var(--color-on-primary)');
    expect(previewDocument).toContain('border: 1px solid var(--caregiver-border)');
    expect(previewDocument).toContain('outline: 3px solid var(--color-primary)');
    expect(previewDocument).toContain('min-width: 0');
    expect(previewDocument).toContain('width: 100%');
  });

  it('tracks external theme changes without overwriting an active preview', async () => {
    const user = userEvent.setup();
    renderPreview({ externalModeControls: true });

    await user.click(screen.getByRole('button', { name: 'Set applied Light' }));
    await waitFor(() => expect(screen.getByRole('radio', { name: 'Light' })).toBeChecked());

    await user.click(screen.getByRole('radio', { name: 'System' }));
    await user.click(screen.getByRole('button', { name: 'Set applied Dark' }));
    await waitFor(() => expect(document.documentElement).toHaveAttribute('data-theme', 'dark'));

    expect(screen.getByRole('radio', { name: 'System' })).toBeChecked();
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.getByRole('radio', { name: 'Dark' })).toBeChecked();
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark');
  });

  it('renders the preview in Arabic RTL', () => {
    renderPreview({ language: 'ar' });
    const previewDocument = screen.getByTitle('معاينة المظهر').getAttribute('srcdoc') ?? '';

    expect(screen.getByRole('radio', { name: 'النظام' })).toBeVisible();
    expect(screen.getByRole('radio', { name: 'فاتح' })).toBeVisible();
    expect(screen.getByRole('radio', { name: 'داكن' })).toBeVisible();
    expect(previewDocument).toContain('<html lang="ar" dir="rtl"');
  });
});
