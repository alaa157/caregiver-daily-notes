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

const ByokContext = createContext<ByokValue | null>(null);

export function ByokProvider({ children }: { children: ReactNode }) {
  const [credentials, setCredentials] = useState<ByokCredentials | null>(null);
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
