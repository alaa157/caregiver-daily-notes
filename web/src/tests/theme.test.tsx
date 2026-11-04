import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { useEffect } from 'react';
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

function ThemeProbe({ onPassiveTheme }: { onPassiveTheme?: (theme: string | undefined) => void }) {
  const { mode, resolvedTheme, setMode } = useTheme();

  useEffect(() => {
    onPassiveTheme?.(document.documentElement.dataset.theme);
  }, [onPassiveTheme]);

  return (
    <>
      <output data-testid="theme-state">{mode}:{resolvedTheme}</output>
      <button type="button" onClick={() => setMode('system')}>Set system</button>
      <button type="button" onClick={() => setMode('light')}>Set light</button>
      <button type="button" onClick={() => setMode('dark')}>Set dark</button>
    </>
  );
}

function renderThemeProvider(onPassiveTheme?: (theme: string | undefined) => void) {
  return render(<ThemeProvider><ThemeProbe onPassiveTheme={onPassiveTheme} /></ThemeProvider>);
}

beforeEach(() => {
  window.localStorage.clear();
  delete document.documentElement.dataset.theme;
  configureMatchMedia();
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  window.localStorage.clear();
  delete document.documentElement.dataset.theme;
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

  it('keeps dark mode when operating system changes', () => {
    window.localStorage.setItem('caregiver-theme-mode', 'dark');
    renderThemeProvider();

    setSystemPreference(true);
    expect(screen.getByTestId('theme-state')).toHaveTextContent('dark:dark');
    setSystemPreference(false);
    expect(screen.getByTestId('theme-state')).toHaveTextContent('dark:dark');
    expect(document.documentElement.dataset.theme).toBe('dark');
  });

  it('removes system listeners when switching to light or dark', () => {
    renderThemeProvider();
    expect(mediaListeners.size).toBe(1);

    fireEvent.click(screen.getByRole('button', { name: 'Set light' }));
    expect(mediaListeners.size).toBe(0);
    expect(mediaQuery.removeEventListener).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: 'Set system' }));
    expect(mediaListeners.size).toBe(1);
    fireEvent.click(screen.getByRole('button', { name: 'Set dark' }));
    expect(mediaListeners.size).toBe(0);
    expect(mediaQuery.removeEventListener).toHaveBeenCalledTimes(2);
  });

  it('removes the system listener when the provider unmounts', () => {
    const view = renderThemeProvider();
    expect(mediaListeners.size).toBe(1);

    view.unmount();

    expect(mediaListeners.size).toBe(0);
    expect(mediaQuery.removeEventListener).toHaveBeenCalledTimes(1);
  });

  it('falls back to system mode for an invalid stored value', () => {
    window.localStorage.setItem('caregiver-theme-mode', 'sepia');
    renderThemeProvider();

    expect(screen.getByTestId('theme-state')).toHaveTextContent('system:light');
    expect(document.documentElement.dataset.theme).toBe('system');
  });

  it('does not crash when localStorage reads or writes fail', () => {
    vi.spyOn(window.localStorage, 'getItem').mockImplementation(() => {
      throw new Error('storage read blocked');
    });
    vi.spyOn(window.localStorage, 'setItem').mockImplementation(() => {
      throw new Error('storage write blocked');
    });

    renderThemeProvider();
    expect(screen.getByTestId('theme-state')).toHaveTextContent('system:light');

    fireEvent.click(screen.getByRole('button', { name: 'Set dark' }));
    expect(screen.getByTestId('theme-state')).toHaveTextContent('dark:dark');
    expect(document.documentElement.dataset.theme).toBe('dark');
  });

  it('treats missing matchMedia as a light system preference', () => {
    vi.stubGlobal('matchMedia', undefined);

    renderThemeProvider();

    expect(screen.getByTestId('theme-state')).toHaveTextContent('system:light');
    expect(document.documentElement.dataset.theme).toBe('system');
  });

  it('applies the selected root theme before child passive effects', () => {
    const themesObservedByPassiveEffects: Array<string | undefined> = [];
    window.localStorage.setItem('caregiver-theme-mode', 'dark');

    renderThemeProvider((theme) => themesObservedByPassiveEffects.push(theme));
    expect(themesObservedByPassiveEffects).toEqual(['dark']);
    expect(document.documentElement.dataset.theme).toBe('dark');

    cleanup();
    themesObservedByPassiveEffects.length = 0;
    delete document.documentElement.dataset.theme;
    window.localStorage.setItem('caregiver-theme-mode', 'light');
    setSystemPreference(true);

    renderThemeProvider((theme) => themesObservedByPassiveEffects.push(theme));
    expect(themesObservedByPassiveEffects).toEqual(['light']);
    expect(document.documentElement.dataset.theme).toBe('light');
  });
});
