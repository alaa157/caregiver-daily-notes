import { StrictMode } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../../app/App';
import { I18nProvider } from '../../i18n/I18nProvider';
import { ApiProvider } from '../../lib/api';
import { jsonResponse, testToken } from '../../tests/authFixtures';
import { AuthProvider } from './AuthProvider';
import { SESSION_STORAGE_KEY } from './session';

function renderSignup(path = '/signup') {
  return render(<StrictMode><I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}><AuthProvider>
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>
  </AuthProvider></ApiProvider></I18nProvider></StrictMode>);
}

describe('caregiver signup', () => {
  let fetcher: ReturnType<typeof vi.fn>;
  beforeEach(() => {
    fetcher = vi.fn();
    vi.stubGlobal('fetch', fetcher);
    sessionStorage.clear();
  });

  it('registers, stores the session, and lands in the workspace', async () => {
    const token = testToken();
    fetcher.mockResolvedValue(jsonResponse({ token }, 201));
    const user = userEvent.setup();
    renderSignup();
    await user.type(await screen.findByLabelText('Email address'), '  New.Caregiver@Example.com  ');
    await user.type(screen.getByLabelText('Password'), 'a strong passphrase');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    await screen.findByRole('heading', { name: 'Caregiver workspace' });

    const [url, options] = fetcher.mock.calls[0];
    expect(url).toBe('/api/auth/register');
    expect(options.method).toBe('POST');
    expect(JSON.parse(options.body)).toEqual({
      email: 'new.caregiver@example.com',
      password: 'a strong passphrase',
    });
    expect(options.headers.has('Authorization')).toBe(false);
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toContain(token);
  });

  it('shows a translated message for a 422 and clears the password', async () => {
    fetcher.mockResolvedValue(jsonResponse({ code: 'VALIDATION_ERROR', message: 'internal diagnostics' }, 422));
    const user = userEvent.setup();
    renderSignup();
    await user.type(await screen.findByLabelText('Email address'), 'someone@example.com');
    await user.type(screen.getByLabelText('Password'), 'a strong passphrase');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Check your email and password, then try again.');
    expect(alert).not.toHaveTextContent('internal diagnostics');
    expect(screen.getByLabelText('Password')).toHaveValue('');
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull();
  });

  it('validates locally, focuses the first bad field, and never calls the API', async () => {
    const user = userEvent.setup();
    renderSignup();
    await user.click(await screen.findByRole('button', { name: 'Create account' }));

    expect(screen.getByLabelText('Email address')).toHaveFocus();
    expect(screen.getByLabelText('Email address')).toHaveAccessibleDescription('Enter your email address.');
    expect(screen.getByLabelText('Password')).toHaveAccessibleDescription('Enter your password.');
    expect(fetcher).not.toHaveBeenCalled();

    await user.type(screen.getByLabelText('Email address'), 'valid@example.com');
    await user.type(screen.getByLabelText('Password'), 'é'.repeat(37));
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    expect(screen.getByText('Your password must be at most 72 UTF-8 bytes.')).toBeVisible();
    expect(fetcher).not.toHaveBeenCalled();
  });
});
