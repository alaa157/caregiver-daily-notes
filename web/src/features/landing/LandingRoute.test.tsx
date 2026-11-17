import { fireEvent, render, screen, within } from '@testing-library/react';
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

describe('public landing page', () => {
  it('renders the English landing sections, Android download and visible safety notice', () => {
    const { container } = setup();
    expect(container.querySelectorAll('main')).toHaveLength(1);
    expect(container.querySelector('main')).toHaveAttribute('id', 'main-content');
    expect(screen.getByRole('heading', { level: 1, name: /clearer picture/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Safety comes first' })).toBeInTheDocument();
    expect(screen.getByText(/capstone project/i)).toBeInTheDocument();
    expect(screen.getByText(/structured recipient profile fields.*excluded from AI requests.*free-text notes are sent as written/i)).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /download android apk/i })[0]).toHaveAttribute('href', 'https://github.com/alaa157/caregiver-daily-notes/releases/download/v0.1.1/app-minified.apk');
  });
  it('announces every new-tab link to assistive technology in English', () => {
    const { container } = setup();

    const external = Array.from(container.querySelectorAll<HTMLAnchorElement>('a[target="_blank"]'));
    expect(external).toHaveLength(6);
    expect(external.every((link) => link.getAttribute('rel') === 'noopener noreferrer')).toBe(true);
    // The notice is present in the accessible name but visually hidden.
    expect(external.every((link) => link.querySelector('.visually-hidden')?.textContent === ' (opens in a new tab)')).toBe(true);
    expect(screen.getAllByRole('link', { name: /opens in a new tab/i })).toHaveLength(6);
  });

  it('translates the new-tab announcement when the page switches to Arabic', () => {
    const { container } = setup();
    fireEvent.click(within(screen.getByRole('navigation')).getByRole('button', { name: /التبديل إلى العربية/ }));

    const external = Array.from(container.querySelectorAll<HTMLAnchorElement>('a[target="_blank"]'));
    expect(external).toHaveLength(6);
    expect(external.every((link) => link.querySelector('.visually-hidden')?.textContent === ' (يفتح في علامة تبويب جديدة)')).toBe(true);
    expect(external.every((link) => link.getAttribute('rel') === 'noopener noreferrer')).toBe(true);
  });

  it('marks both APK download links with a visible external-link icon', () => {
    const { container } = setup();

    const apkLinks = screen.getAllByRole('link', { name: /download android apk/i });
    expect(apkLinks).toHaveLength(2);
    expect(apkLinks.every((link) => link.querySelector('svg.external') !== null)).toBe(true);
    const heroIcon = apkLinks[0].querySelector('svg.external');
    expect(heroIcon).toHaveAttribute('aria-hidden', 'true');
    expect(container.querySelectorAll('svg.external')).toHaveLength(2);
  });

  it('switches language and document direction using the existing i18n provider', () => {
    setup();
    const navigation = screen.getByRole('navigation', { name: 'Main navigation' });
    const languageToggle = within(navigation).getByRole('button', { name: /التبديل إلى العربية/ });
    expect(languageToggle).toHaveAttribute('aria-label', 'AR — التبديل إلى العربية');
    expect(languageToggle).toHaveTextContent('AR');
    fireEvent.click(languageToggle);
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('سجّل تفاصيل يومك');
    expect(within(navigation).getByRole('button', { name: 'EN — Switch to English' })).toHaveTextContent('EN');
    expect(document.documentElement.lang).toBe('ar');
    expect(document.documentElement.dir).toBe('rtl');
    expect(screen.getByText(/بيانات الملف الشخصي المنظمة.*الملاحظات المكتوبة بحرية فتُرسل كما كُتبت/)).toBeInTheDocument();
  });
  it('marks the brand name as English in the header, hero and footer on both pages', () => {
    const { container } = setup();
    const brandSpans = () => Array.from(container.querySelectorAll<HTMLElement>(
      '.landing-brand span, .landing-hero .eyebrow span, .landing-footer span[lang="en"]',
    ));

    expect(brandSpans()).toHaveLength(3);
    expect(brandSpans().every((span) => span.getAttribute('lang') === 'en')).toBe(true);
    expect(brandSpans().every((span) => span.textContent === 'Caregiver Daily Notes')).toBe(true);

    fireEvent.click(within(screen.getByRole('navigation')).getByRole('button', { name: /التبديل إلى العربية/ }));

    expect(brandSpans()).toHaveLength(3);
    expect(brandSpans().every((span) => span.getAttribute('lang') === 'en')).toBe(true);
    expect(brandSpans().every((span) => span.textContent === 'Caregiver Daily Notes')).toBe(true);
  });

  it('marks the language toggle label with the language it is written in', () => {
    const { container } = setup();
    const toggles = () => Array.from(container.querySelectorAll<HTMLButtonElement>('.language-toggle'));

    // English page: the label reads "AR — التبديل إلى العربية" (Arabic).
    expect(toggles()).toHaveLength(2);
    expect(toggles().every((button) => button.getAttribute('lang') === 'ar')).toBe(true);

    fireEvent.click(toggles()[0]);

    // Arabic page: the label reads "EN — Switch to English" (English).
    expect(toggles().every((button) => button.getAttribute('lang') === 'en')).toBe(true);
  });

  it('marks the tech-stack product names as English on both pages', () => {
    const { container } = setup();
    const badges = () => Array.from(container.querySelectorAll<HTMLElement>('.landing-badges span'));

    expect(badges()).toHaveLength(8);
    expect(badges().every((badge) => badge.getAttribute('lang') === 'en')).toBe(true);

    fireEvent.click(within(screen.getByRole('navigation')).getByRole('button', { name: /التبديل إلى العربية/ }));

    expect(badges().every((badge) => badge.getAttribute('lang') === 'en')).toBe(true);
  });

  it('renders without API/auth providers and points a stored-session visitor at the app', () => {
    sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: 'https://api.example.test' }));

    render(<I18nProvider><MemoryRouter><LandingRoute /></MemoryRouter></I18nProvider>);

    expect(screen.getByRole('heading', { level: 1, name: /clearer picture/i })).toBeVisible();
    expect(screen.getAllByRole('link', { name: 'Open app' })[0]).toHaveAttribute('href', '/app');
    expect(screen.queryByRole('link', { name: 'Create an account' })).not.toBeInTheDocument();
    // Reading the session for the CTA must not consume it before config loads.
    expect(sessionStorage.getItem('caregiver-auth-session-v1')).not.toBeNull();
  });

  it('closes the mobile menu on Escape and on navigation link clicks', () => {
    const { container } = setup();
    const menuButton = container.querySelector<HTMLButtonElement>('.landing-menu');
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
    expect(menuButton).toHaveFocus();
    expect(navigation).not.toHaveClass('is-open');

    fireEvent.click(menuButton);
    fireEvent.click(screen.getByRole('link', { name: 'How it works' }));
    expect(menuButton).toHaveAttribute('aria-expanded', 'false');
    expect(navigation).not.toHaveClass('is-open');
  });
});
