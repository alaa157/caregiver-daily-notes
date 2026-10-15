import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { ApiClientProvider, ApiError, useApiClient, type ApiClient, type ApiRequest } from '../../lib/api';
import { clearStoredSession, parseSession, restoreSession, storeSession, type SessionReason, type SessionState } from './session';

export type AuthStatus = 'anonymous' | 'authenticated' | 'checking';
interface AuthValue {
  status: AuthStatus;
  reason: SessionReason;
  login: (email: string, password: string, signal?: AbortSignal) => Promise<void>;
  logout: () => void;
}
const AuthContext = createContext<AuthValue | null>(null);
const cancelled = () => new DOMException('Sign-in cancelled.', 'AbortError');

export function AuthProvider({ children }: { children: ReactNode }) {
  const publicApi = useApiClient();
  const [state, setState] = useState<SessionState>(() => restoreSession(publicApi.baseUrl));
  const sessionRef = useRef(state.session);
  const generation = useRef(0);
  const pendingLogin = useRef<AbortController | null>(null);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    if (!sessionRef.current) clearStoredSession();
    return () => {
      mounted.current = false;
      generation.current += 1;
      pendingLogin.current?.abort();
    };
  }, []);

  const clearSession = useCallback((reason: SessionReason) => {
    generation.current += 1;
    pendingLogin.current?.abort();
    sessionRef.current = null;
    clearStoredSession();
    if (mounted.current) setState({ session: null, reason });
  }, []);

  useEffect(() => {
    if (!state.session) return;
    const expiresAt = state.session.expiresAt;
    let timer: ReturnType<typeof setTimeout>;
    const check = () => {
      clearTimeout(timer);
      const remaining = expiresAt - Date.now();
      if (remaining <= 0) clearSession('expired');
      else timer = setTimeout(check, Math.min(remaining, 2_147_483_647));
    };
    check();
    window.addEventListener('focus', check);
    window.addEventListener('pageshow', check);
    document.addEventListener('visibilitychange', check);
    return () => {
      clearTimeout(timer);
      window.removeEventListener('focus', check);
      window.removeEventListener('pageshow', check);
      document.removeEventListener('visibilitychange', check);
    };
  }, [state.session, clearSession]);

  const login = useCallback(async (email: string, password: string, signal?: AbortSignal) => {
    pendingLogin.current?.abort();
    const controller = new AbortController();
    pendingLogin.current = controller;
    const attempt = ++generation.current;
    const abort = () => controller.abort();
    signal?.addEventListener('abort', abort, { once: true });
    if (signal?.aborted) controller.abort();
    try {
      const response = await publicApi.request<unknown, { email: string; password: string }>({
        path: '/api/auth/login', method: 'POST', body: { email: email.trim().toLowerCase(), password }, signal: controller.signal,
      });
      if (controller.signal.aborted || generation.current !== attempt || !mounted.current) throw cancelled();
      if (typeof response !== 'object' || response === null || !('token' in response)) throw new Error('Invalid authentication response.');
      const session = parseSession(response.token);
      sessionRef.current = session;
      storeSession(session, publicApi.baseUrl);
      setState({ session, reason: null });
    } finally {
      signal?.removeEventListener('abort', abort);
      if (pendingLogin.current === controller) pendingLogin.current = null;
    }
  }, [publicApi]);

  const authenticatedApi = useMemo<ApiClient>(() => ({
    baseUrl: publicApi.baseUrl,
    async request<TResponse, TBody = unknown>(request: ApiRequest<TBody>): Promise<TResponse> {
      const session = sessionRef.current;
      const requestGeneration = generation.current;
      if (!session || session.expiresAt <= Date.now()) {
        if (session) clearSession('expired');
        throw new ApiError(401, null);
      }
      try {
        const response = await publicApi.request<TResponse, TBody>({ ...request, accessToken: session.token });
        // Ignore private responses from a session that ended while the request ran.
        if (generation.current !== requestGeneration) throw new ApiError(401, null);
        if (session.expiresAt <= Date.now()) {
          clearSession('expired');
          throw new ApiError(401, null);
        }
        return response;
      } catch (error) {
        if (error instanceof ApiError && error.status === 401 && generation.current === requestGeneration) clearSession('expired');
        throw error;
      }
    },
  }), [publicApi, clearSession]);

  const value: AuthValue = {
    status: state.session ? 'authenticated' : 'anonymous',
    reason: state.reason,
    login,
    logout: () => clearSession('signedOut'),
  };
  return <AuthContext.Provider value={value}><ApiClientProvider client={authenticatedApi}>{children}</ApiClientProvider></AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider.');
  return value;
}

export function useAuthStatus(): AuthStatus { return useAuth().status; }
