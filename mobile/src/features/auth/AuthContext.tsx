import React, {createContext, useCallback, useContext, useEffect, useState} from 'react';
import {ApiClient} from '../../lib/ApiClient';
import {ApiError} from '../../lib/errors';
import {TokenStore} from '../../lib/TokenStore';
import {login as loginCall, register as registerCall} from './api';

export interface AuthState {
  token: string | null;
  loading: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  signUp: (email: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
  authedFetch: <T>(fn: (client: ApiClient) => Promise<T>) => Promise<T>;
}

const AuthContext = createContext<AuthState | null>(null);

export function useAuth(): AuthState {
  const state = useContext(AuthContext);
  if (!state) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return state;
}

export function AuthProvider({
  client,
  children,
}: {
  client: ApiClient;
  children: React.ReactNode;
}): React.JSX.Element {
  const [token, setToken] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    let live = true;
    TokenStore.get().then(
      stored => {
        if (live) {
          setToken(stored);
          setLoading(false);
        }
      },
      () => {
        if (live) {
          setToken(null);
          setLoading(false);
        }
      },
    );
    return () => {
      live = false;
    };
  }, []);

  const signOut = useCallback(async () => {
    await TokenStore.clear();
    setToken(null);
  }, []);

  const signIn = useCallback(
    async (email: string, password: string) => {
      const res = await loginCall(client, email, password);
      await TokenStore.set(res.token);
      setToken(res.token);
    },
    [client],
  );

  const signUp = useCallback(
    async (email: string, password: string) => {
      const res = await registerCall(client, {email, password});
      await TokenStore.set(res.token);
      setToken(res.token);
    },
    [client],
  );

  const authedFetch = useCallback(
    async <T,>(fn: (client: ApiClient) => Promise<T>): Promise<T> => {
      try {
        return await fn(client);
      } catch (e) {
        if (e instanceof ApiError && e.code === 'UNAUTHORIZED') {
          await signOut();
        }
        throw e;
      }
    },
    [client, signOut],
  );

  return (
    <AuthContext.Provider value={{token, loading, signIn, signUp, signOut, authedFetch}}>
      {children}
    </AuthContext.Provider>
  );
}
