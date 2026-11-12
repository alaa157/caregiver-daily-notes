import { fireEvent, render, screen } from '@testing-library/react';
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
    setup();
    expect(screen.getByRole('heading', { level: 1, name: /clearer picture/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Safety comes first' })).toBeInTheDocument();
    expect(screen.getByText(/capstone project/i)).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /download android apk/i })[0]).toHaveAttribute('href', 'https://github.com/alaa157/caregiver-daily-notes/releases/download/v0.1.1/app-minified.apk');
  });
  it('switches language and document direction using the existing i18n provider', () => {
    setup();
    fireEvent.click(screen.getByRole('button', { name: 'التبديل إلى العربية' }));
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('سجّل تفاصيل يومك');
    expect(document.documentElement.lang).toBe('ar');
    expect(document.documentElement.dir).toBe('rtl');
  });
});
