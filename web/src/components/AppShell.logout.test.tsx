import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../app/App';
import { I18nProvider } from '../i18n/I18nProvider';
import { ApiProvider } from '../lib/api';
import { useAuth } from '../features/auth/AuthProvider';
import { jsonResponse } from '../tests/authFixtures';

vi.mock('../features/auth/AuthProvider', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../features/auth/AuthProvider')>();
  return { ...actual, useAuth: vi.fn(), useAuthStatus: vi.fn(() => 'authenticated') };
});

const logout = vi.fn();
const user = userEvent.setup();

function renderApp(path: string) {
  return render(<I18nProvider><ApiProvider config={{ apiBaseUrl: '' }}>
    <MemoryRouter initialEntries={[path]}><App /></MemoryRouter>
  </ApiProvider></I18nProvider>);
}

describe('logout call sites', () => {
  beforeEach(() => {
    logout.mockClear();
    vi.stubGlobal('scrollTo', vi.fn());
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse([])));
    localStorage.setItem('caregiver-language', 'en');
    vi.mocked(useAuth).mockReturnValue({
      status: 'authenticated', reason: null, login: vi.fn(), register: vi.fn(), logout,
    } as ReturnType<typeof useAuth>);
  });

  it('passes no click event to logout from the desktop sign-out button', async () => {
    // The shell header (with the desktop sign-out) no longer renders on auth or
    // workspace routes; a public shell route still exposes it.
    renderApp('/page-that-does-not-exist');

    await user.click(await screen.findByRole('button', { name: 'Sign out' }));

    expect(logout).toHaveBeenCalledTimes(1);
    expect(logout).toHaveBeenCalledWith();
  });

  it('passes no click event to logout from the account menu sign-out button', async () => {
    renderApp('/app');
    await user.click(await screen.findByText('Account'));

    await user.click(await screen.findByRole('button', { name: 'Sign out' }));

    expect(logout).toHaveBeenCalledTimes(1);
    expect(logout).toHaveBeenCalledWith();
  });
});
