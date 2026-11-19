import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AuthProvider } from '../auth/AuthProvider';
import { I18nProvider } from '../../i18n/I18nProvider';
import { ApiProvider } from '../../lib/api';
import { LandingRoute } from '../../routes/LandingRoute';
import { testToken } from '../../tests/authFixtures';

function setup() {
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider><MemoryRouter><LandingRoute /></MemoryRouter></AuthProvider></ApiProvider></I18nProvider>);
}

/** The ported header toggle is outside the nav landmark; there is also a footer copy. */
function headerToggle(container: HTMLElement): HTMLButtonElement {
  const toggle = container.querySelector<HTMLButtonElement>('.site-header .language-toggle');
  if (!toggle) throw new Error('Header language toggle not found');
  return toggle;
}

describe('public landing page', () => {
  it('renders the English landing sections, Android download and visible safety notice', () => {
    const { container } = setup();
    expect(container.querySelectorAll('main')).toHaveLength(1);
    expect(container.querySelector('main')).toHaveAttribute('id', 'main-content');
    expect(screen.getByRole('heading', { level: 1, name: /little details are the story of/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'AI can assist. It does not take the wheel.' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /project demo — please read before using/i })).toBeInTheDocument();
    expect(screen.getByText(/capstone project using synthetic data only/i)).toBeInTheDocument();
    // The port drops the old structured-profile-fields sentence; its safety
    // notice now explains that BYOK note text is sent to the chosen provider.
    expect(screen.getByText(/the note text is sent to that provider as written/i)).toBeInTheDocument();
    const apkLinks = screen.getAllByRole('link', { name: /download android apk/i });
    expect(apkLinks).toHaveLength(1);
    const url = new URL(apkLinks[0].getAttribute('href')!);
    expect(url.origin).toBe('https://github.com');
    // The permanent `apk-latest` tag is replaced by the build, so no version
    // segment can go stale the way a `v0.1.1`-style URL would.
    expect(url.pathname).toBe('/alaa157/caregiver-daily-notes/releases/download/apk-latest/app-minified.apk');
    expect(url.pathname).not.toMatch(/v\d|android-standalone/i);
  });
  it('announces every new-tab link to assistive technology in English', () => {
    const { container } = setup();

    const external = Array.from(container.querySelectorAll<HTMLAnchorElement>('a[target="_blank"]'));
    expect(external).toHaveLength(3);
    expect(external.every((link) => link.getAttribute('rel') === 'noopener noreferrer')).toBe(true);
    // The notice is present in the accessible name but visually hidden.
    expect(external.every((link) => link.querySelector('.visually-hidden')?.textContent === ' (opens in a new tab)')).toBe(true);
    expect(screen.getAllByRole('link', { name: /opens in a new tab/i })).toHaveLength(3);
  });

  it('translates the new-tab announcement when the page switches to Arabic', () => {
    const { container } = setup();
    fireEvent.click(headerToggle(container));

    const external = Array.from(container.querySelectorAll<HTMLAnchorElement>('a[target="_blank"]'));
    expect(external).toHaveLength(3);
    expect(external.every((link) => link.querySelector('.visually-hidden')?.textContent === ' (يفتح في علامة تبويب جديدة)')).toBe(true);
    expect(external.every((link) => link.getAttribute('rel') === 'noopener noreferrer')).toBe(true);
  });

  it('marks the APK download link as opening in a new tab', () => {
    setup();
    const apkLink = screen.getByRole('link', { name: /download android apk/i });

    expect(apkLink).toHaveAttribute('target', '_blank');
    expect(apkLink).toHaveAttribute('rel', 'noopener noreferrer');
    expect(apkLink.querySelector('.visually-hidden')).toHaveTextContent('(opens in a new tab)');
  });

  it('switches language and document direction using the existing i18n provider', () => {
    const { container } = setup();
    expect(screen.getByRole('navigation', { name: 'Main navigation' })).toBeInTheDocument();
    const toggle = headerToggle(container);
    expect(toggle).toHaveAttribute('aria-label', 'Switch to Arabic');
    expect(toggle).toHaveTextContent('العربية');
    fireEvent.click(toggle);
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(/تفاصيل صغيرة ترسم صورة أكبر/i);
    expect(headerToggle(container)).toHaveAttribute('aria-label', 'التبديل إلى الإنجليزية');
    expect(headerToggle(container)).toHaveTextContent('English');
    expect(document.documentElement.lang).toBe('ar');
    expect(document.documentElement.dir).toBe('rtl');
    expect(screen.getByText(/كما كُتب، وقد يتضمن معلومات تكشف الهوية/)).toBeInTheDocument();
  });
  it('marks the brand name with the page language in the header and footer', () => {
    const { container } = setup();
    const brandSpans = () => Array.from(container.querySelectorAll<HTMLElement>(
      '.brand-name strong, .footer-brand span[lang]',
    ));

    expect(brandSpans()).toHaveLength(2);
    expect(brandSpans().every((span) => span.getAttribute('lang') === 'en')).toBe(true);
    expect(brandSpans().every((span) => span.textContent === 'Rafeek')).toBe(true);

    fireEvent.click(headerToggle(container));

    expect(brandSpans()).toHaveLength(2);
    expect(brandSpans().every((span) => span.getAttribute('lang') === 'ar')).toBe(true);
    expect(brandSpans().every((span) => span.textContent === 'رفيق')).toBe(true);
  });

  it('marks the language toggle label with the language it is written in', () => {
    const { container } = setup();
    const toggles = () => Array.from(container.querySelectorAll<HTMLButtonElement>('.language-toggle'));
    const labelLangs = () => toggles().map((button) => button.querySelector('span')?.getAttribute('lang'));

    // English page: the label reads "العربية" (written in Arabic).
    expect(toggles()).toHaveLength(2);
    expect(labelLangs()).toEqual(['ar', 'ar']);

    fireEvent.click(toggles()[0]);

    // Arabic page: the label reads "English" (written in English).
    expect(labelLangs()).toEqual(['en', 'en']);
  });

  it('marks the sample language cards with the language they are written in', () => {
    const { container } = setup();

    expect(container.querySelector('.language-card[lang="en"]')).toHaveTextContent("Today's note");
    expect(container.querySelector('.language-card[lang="ar"]')).toHaveTextContent('ملاحظة اليوم');
    expect(container.querySelector('.language-card[lang="ar"]')).toHaveAttribute('dir', 'rtl');
  });

  it('renders without API/auth providers and keeps a stored session readable', () => {
    sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: 'https://api.example.test' }));

    render(<I18nProvider><MemoryRouter><LandingRoute /></MemoryRouter></I18nProvider>);

    expect(screen.getByRole('heading', { level: 1, name: /little details are the story of/i })).toBeVisible();
    expect(screen.getAllByRole('link', { name: 'Sign in' })[0]).toHaveAttribute('href', '/login');
    // Reading the session for the CTA must not consume it before config loads.
    expect(sessionStorage.getItem('caregiver-auth-session-v1')).not.toBeNull();
  });

  it('names the mobile menu button with the label only, in both languages', () => {
    const { container } = setup();
    const menuButton = container.querySelector<HTMLButtonElement>('.menu-toggle');
    if (!menuButton) throw new Error('Landing menu button not found');

    // The menu trigger is display:none at desktop widths, where accessible-name
    // computation is skipped; assert the label-only contract on aria-label.
    expect(menuButton).toHaveAttribute('aria-label', 'Open navigation');
    expect(menuButton).toHaveAttribute('aria-controls', 'main-nav');
    expect(menuButton).toHaveAttribute('aria-expanded', 'false');
    expect(menuButton.textContent).not.toMatch(/[☰×]/);
    expect(menuButton.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');

    fireEvent.click(menuButton);
    expect(menuButton).toHaveAttribute('aria-expanded', 'true');
    expect(menuButton).toHaveAttribute('aria-label', 'Close navigation');

    fireEvent.click(headerToggle(container));
    expect(menuButton).toHaveAttribute('aria-label', 'إغلاق قائمة التنقل');
    expect(menuButton.textContent).not.toMatch(/[☰×]/);
  });

  it('closes the mobile menu on Escape and on navigation link clicks', async () => {
    const { container } = setup();
    const menuButton = container.querySelector<HTMLButtonElement>('.menu-toggle');
    if (!menuButton) throw new Error('Landing menu button not found');
    const navigation = screen.getByRole('navigation', { name: 'Main navigation' });

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(menuButton).toHaveAttribute('aria-expanded', 'false');

    fireEvent.click(menuButton);
    expect(menuButton).toHaveAttribute('aria-expanded', 'true');
    expect(navigation).toHaveClass('is-open');
    menuButton.focus();
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(menuButton).toHaveAttribute('aria-expanded', 'false');
    expect(navigation).not.toHaveClass('is-open');
    // closeMenu defers focus to the next task so in-page anchors do not steal it.
    await waitFor(() => expect(menuButton).toHaveFocus());

    fireEvent.click(menuButton);
    fireEvent.click(screen.getByRole('link', { name: 'How it works' }));
    expect(menuButton).toHaveAttribute('aria-expanded', 'false');
    expect(navigation).not.toHaveClass('is-open');
  });
});
