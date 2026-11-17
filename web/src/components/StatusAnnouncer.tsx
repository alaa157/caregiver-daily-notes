import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';

type AnnounceStatus = (message: string) => void;

const StatusAnnouncerContext = createContext<AnnounceStatus | null>(null);
const noAnnouncement: AnnounceStatus = () => {};

export function StatusAnnouncerProvider({ children }: { children: ReactNode }) {
  const { message, announce } = useStatusAnnouncement();

  return (
    <StatusAnnouncerContext.Provider value={announce}>
      {children}
      <div className="app-visually-hidden" role="status" aria-live="polite" aria-atomic="true">{message}</div>
    </StatusAnnouncerContext.Provider>
  );
}

export function useStatusAnnouncement() {
  const [message, setMessage] = useState('');
  const pendingAnnouncement = useRef<ReturnType<typeof setTimeout> | null>(null);
  const announce = useCallback((nextMessage: string) => {
    if (pendingAnnouncement.current !== null) clearTimeout(pendingAnnouncement.current);
    setMessage('');
    pendingAnnouncement.current = setTimeout(() => {
      setMessage(nextMessage);
      pendingAnnouncement.current = null;
    }, 0);
  }, []);

  useEffect(() => () => {
    if (pendingAnnouncement.current !== null) clearTimeout(pendingAnnouncement.current);
  }, []);

  return { message, announce };
}

export function useStatusAnnouncer(): AnnounceStatus {
  return useContext(StatusAnnouncerContext) ?? noAnnouncement;
}
