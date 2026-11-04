import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';

export type ThemeMode = 'system' | 'light' | 'dark';

type ResolvedTheme = 'light' | 'dark';

interface ThemeContextValue {
  mode: ThemeMode;
  setMode: (mode: ThemeMode) => void;
  resolvedTheme: ResolvedTheme;
}

const STORAGE_KEY = 'caregiver-theme-mode';
const SYSTEM_COLOR_SCHEME_QUERY = '(prefers-color-scheme: dark)';

const ThemeContext = createContext<ThemeContextValue | null>(null);

function readStoredMode(): ThemeMode {
  try {
    const storedMode = window.localStorage.getItem(STORAGE_KEY);
    if (storedMode === 'system' || storedMode === 'light' || storedMode === 'dark') {
      return storedMode;
    }
  } catch {
    // Storage may be unavailable; system mode remains the in-memory default.
  }
  return 'system';
}

function getSystemTheme(): ResolvedTheme {
  if (typeof window.matchMedia !== 'function') return 'light';
  return window.matchMedia(SYSTEM_COLOR_SCHEME_QUERY).matches ? 'dark' : 'light';
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setModeState] = useState<ThemeMode>(readStoredMode);
  const [resolvedTheme, setResolvedTheme] = useState<ResolvedTheme>(() =>
    mode === 'system' ? getSystemTheme() : mode,
  );

  const setMode = (nextMode: ThemeMode) => {
    setModeState(nextMode);
    try {
      window.localStorage.setItem(STORAGE_KEY, nextMode);
    } catch {
      // Keep the selected mode in memory when storage is unavailable.
    }
  };

  useEffect(() => {
    document.documentElement.dataset.theme = mode;

    if (mode !== 'system') {
      setResolvedTheme(mode);
      return;
    }

    if (typeof window.matchMedia !== 'function') {
      setResolvedTheme('light');
      return;
    }

    const mediaQuery = window.matchMedia(SYSTEM_COLOR_SCHEME_QUERY);
    const updateResolvedTheme = (event: MediaQueryListEvent) => {
      setResolvedTheme(event.matches ? 'dark' : 'light');
    };

    setResolvedTheme(mediaQuery.matches ? 'dark' : 'light');
    mediaQuery.addEventListener('change', updateResolvedTheme);
    return () => mediaQuery.removeEventListener('change', updateResolvedTheme);
  }, [mode]);

  return (
    <ThemeContext.Provider value={{ mode, setMode, resolvedTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('useTheme must be called within ThemeProvider.');
  return context;
}
