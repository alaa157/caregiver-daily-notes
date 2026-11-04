import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemeProvider, useTheme } from '../app/ThemeProvider';

type MediaChangeListener = (event: MediaQueryListEvent) => void;

let systemMatches = false;
let mediaListeners: Set<MediaChangeListener>;
let mediaQuery: MediaQueryList;

function configureMatchMedia() {
  systemMatches = false;
  mediaListeners = new Set();
  mediaQuery = {
    get matches() {
      return systemMatches;
    },
    media: '(prefers-color-scheme: dark)',
    onchange: null,
    addEventListener: vi.fn((_type: string, listener: MediaChangeListener) => {
      mediaListeners.add(listener);
    }),
    removeEventListener: vi.fn((_type: string, listener: MediaChangeListener) => {
      mediaListeners.delete(listener);
    }),
    dispatchEvent: vi.fn(() => true),
  } as unknown as MediaQueryList;
  vi.stubGlobal('matchMedia', vi.fn(() => mediaQuery));
}

function setSystemPreference(isDark: boolean) {
  systemMatches = isDark;
  const event = { matches: isDark, media: '(prefers-color-scheme: dark)' } as MediaQueryListEvent;
  act(() => {
    for (const listener of mediaListeners) listener(event);
  });
}

function ThemeProbe() {
  const { mode, resolvedTheme, setMode } = useTheme();
  return (
    <>
      <output data-testid="theme-state">{mode}:{resolvedTheme}</output>
      <button type="button" onClick={() => setMode('dark')}>Set dark</button>
    </>
  );
}

function renderThemeProvider() {
  return render(<ThemeProvider><ThemeProbe /></ThemeProvider>);
}

beforeEach(() => {
  window.localStorage.clear();
  delete document.documentElement.dataset.theme;
  configureMatchMedia();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('ThemeProvider', () => {
  it('defaults to system mode', () => {
    renderThemeProvider();

    expect(screen.getByTestId('theme-state')).toHaveTextContent('system:light');
    expect(document.documentElement.dataset.theme).toBe('system');
  });

  it('follows operating system changes in system mode', () => {
    renderThemeProvider();
    expect(screen.getByTestId('theme-state')).toHaveTextContent('system:light');

    setSystemPreference(true);
    expect(screen.getByTestId('theme-state')).toHaveTextContent('system:dark');

    setSystemPreference(false);
    expect(screen.getByTestId('theme-state')).toHaveTextContent('system:light');
  });

  it('keeps light mode when operating system changes', () => {
    window.localStorage.setItem('caregiver-theme-mode', 'light');
    renderThemeProvider();
    expect(screen.getByTestId('theme-state')).toHaveTextContent('light:light');

    setSystemPreference(true);
    expect(screen.getByTestId('theme-state')).toHaveTextContent('light:light');
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('persists dark mode after remount', () => {
    const firstMount = renderThemeProvider();
    fireEvent.click(screen.getByRole('button', { name: 'Set dark' }));

    expect(window.localStorage.getItem('caregiver-theme-mode')).toBe('dark');
    expect(screen.getByTestId('theme-state')).toHaveTextContent('dark:dark');
    firstMount.unmount();

    renderThemeProvider();
    expect(screen.getByTestId('theme-state')).toHaveTextContent('dark:dark');
    expect(document.documentElement.dataset.theme).toBe('dark');
  });
});
