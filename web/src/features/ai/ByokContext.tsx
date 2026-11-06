import { createContext, useContext, useState, type ReactNode } from 'react';

export type ByokProviderId = 'OPENAI' | 'GEMINI' | 'XAI' | 'OPEN_ROUTER';

export interface ByokCredentials {
  providerId: ByokProviderId;
  model: string;
  apiKey: string;
}

interface ByokValue {
  credentials: ByokCredentials | null;
  setCredentials: (credentials: ByokCredentials) => void;
  clearCredentials: () => void;
}

declare global {
  interface Window {
    __E2E_BYOK_CREDENTIALS__?: ByokCredentials;
  }
}

const ByokContext = createContext<ByokValue | null>(null);

function initialCredentials(): ByokCredentials | null {
  if (import.meta.env.MODE !== 'e2e' || typeof window === 'undefined') return null;
  const credentials = window.__E2E_BYOK_CREDENTIALS__ ?? null;
  delete window.__E2E_BYOK_CREDENTIALS__;
  return credentials;
}

export function ByokProvider({ children }: { children: ReactNode }) {
  const [credentials, setCredentials] = useState<ByokCredentials | null>(initialCredentials);
  const value: ByokValue = {
    credentials,
    setCredentials,
    clearCredentials: () => setCredentials(null),
  };
  return <ByokContext.Provider value={value}>{children}</ByokContext.Provider>;
}

export function useByok(): ByokValue {
  const context = useContext(ByokContext);
  if (!context) throw new Error('useByok must be called within ByokProvider.');
  return context;
}
