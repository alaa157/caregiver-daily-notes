import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, testToken } from '../tests/authFixtures';
import { recipientFixture } from '../tests/recipientFixtures';
import { loadRuntimeConfig, type RuntimeConfig } from '../lib/config';
import { Bootstrap } from './Bootstrap';

vi.mock('../lib/config', () => ({
  loadRuntimeConfig: vi.fn(),
  parseRuntimeConfig: vi.fn(),
}));

const load = vi.mocked(loadRuntimeConfig);

function visit(path: string) {
  window.history.replaceState({}, '', path);
}

function stubApi() {
  vi.stubGlobal('fetch', vi.fn().mockImplementation((path: string) => {
    if (path === '/api/recipients') return Promise.resolve(jsonResponse([recipientFixture]));
    if (path.startsWith('/api/notes?')) return Promise.resolve(jsonResponse({
      content: [], number: 0, size: 20, totalElements: 0, totalPages: 0,
    }));
    return Promise.resolve(jsonResponse([]));
  }));
}

function deferredConfig() {
  let resolve!: (config: RuntimeConfig) => void;
  const promise = new Promise<RuntimeConfig>((done) => { resolve = done; });
  load.mockReturnValue(promise);
  return { promise, resolve: (config: RuntimeConfig = { apiBaseUrl: '' }) => resolve(config) };
}

beforeEach(() => {
  load.mockReset();
  visit('/');
});

afterEach(() => {
  visit('/');
});

describe('bootstrap config gate', () => {
  it('renders the public landing page without fetching runtime config', () => {
    load.mockReturnValue(new Promise(() => {}));
    visit('/');

    render(<Bootstrap />);

    expect(screen.getByRole('heading', { level: 1, name: /clearer picture/i })).toBeVisible();
    expect(screen.getAllByRole('link', { name: 'Sign in' })[0]).toHaveAttribute('href', '/login');
    expect(load).not.toHaveBeenCalled();
    expect(document.querySelector('.feedback--loading')).toBeNull();
  });

  it('keeps the landing page when the config fetch is not even attempted', async () => {
    load.mockRejectedValue(new Error('config.json unavailable'));
    visit('/');

    render(<Bootstrap />);

    expect(screen.getByRole('heading', { level: 1, name: /clearer picture/i })).toBeVisible();
    expect(screen.queryByText('Application configuration unavailable')).not.toBeInTheDocument();
    expect(load).not.toHaveBeenCalled();
  });

  it('shows loading only on an API route until config resolves', async () => {
    const config = deferredConfig();
    visit('/login');

    render(<Bootstrap />);

    expect(load).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('status')).toBeVisible();
    expect(screen.queryByRole('form')).not.toBeInTheDocument();

    config.resolve();
    expect(await screen.findByRole('heading', { level: 1, name: 'Caregiver sign in' })).toBeVisible();
    expect(screen.getByRole('form')).toBeVisible();
  });

  it('shows the retryable config error on API routes, not on the landing page', async () => {
    const user = userEvent.setup();
    load.mockRejectedValueOnce(new Error('offline'));
    visit('/login');

    render(<Bootstrap />);

    expect(await screen.findByText('Application configuration unavailable')).toBeVisible();
    expect(screen.queryByRole('form')).not.toBeInTheDocument();

    load.mockResolvedValueOnce({ apiBaseUrl: '' });
    await user.click(screen.getByRole('button', { name: /retry/i }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Caregiver sign in' })).toBeVisible();
    expect(load).toHaveBeenCalledTimes(2);
  });

  it('does not flash unauthenticated content on a protected route before config is known', async () => {
    stubApi();
    sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: testToken(), baseUrl: '' }));
    const config = deferredConfig();
    visit('/app');

    render(<Bootstrap />);

    expect(screen.getByRole('status')).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Caregiver sign in' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Caregiver workspace' })).not.toBeInTheDocument();

    config.resolve();
    expect(await screen.findByRole('heading', { name: 'Caregiver workspace' })).toBeVisible();
  });

  it('redirects an unauthenticated protected route to sign in after config resolves', async () => {
    load.mockResolvedValue({ apiBaseUrl: '' });
    visit('/app');

    render(<Bootstrap />);

    expect(await screen.findByRole('heading', { level: 1, name: 'Caregiver sign in' })).toBeVisible();
    expect(screen.queryByRole('heading', { name: 'Caregiver workspace' })).not.toBeInTheDocument();
  });
});
