import { render, screen } from '@testing-library/react';
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
});
