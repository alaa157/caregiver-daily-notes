import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AuthProvider } from '../auth/AuthProvider';
import { I18nProvider } from '../../i18n/I18nProvider';
import { ApiProvider } from '../../lib/api';
import { LandingRoute } from '../../routes/LandingRoute';

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
