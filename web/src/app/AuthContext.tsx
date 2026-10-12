import { createContext, useContext, type ReactNode } from 'react';

export type AuthStatus = 'anonymous' | 'authenticated' | 'checking';

/** No fake login/session is supplied by the foundation. Task #14 will implement real JWT auth. */
const AuthContext = createContext<AuthStatus>('anonymous');

export function AuthProvider({ children }: { children: ReactNode }) {
  return <AuthContext.Provider value="anonymous">{children}</AuthContext.Provider>;
}

export function useAuthStatus(): AuthStatus {
  return useContext(AuthContext);
}
