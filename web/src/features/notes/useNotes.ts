import { useEffect, useMemo, useState } from 'react';
import { createNotesApi } from '../../api/notes';
import { createRecipientsApi } from '../../api/recipients';
import { useApiClient } from '../../lib/api';

export function useNotesApis() {
  const client = useApiClient();
  return useMemo(() => ({ notes: createNotesApi(client), recipients: createRecipientsApi(client) }), [client]);
}
type Resource<T> = { status: 'loading' } | { status: 'error'; error: unknown } | { status: 'ready'; data: T };
export function useNoteResource<T>(load: (signal: AbortSignal) => Promise<T>) {
  const [state, setState] = useState<Resource<T>>({ status: 'loading' });
  const [version, setVersion] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setState({ status: 'loading' });
    void load(controller.signal).then(
      (data) => { if (!controller.signal.aborted) setState({ status: 'ready', data }); },
      (error: unknown) => { if (!controller.signal.aborted) setState({ status: 'error', error }); },
    );
    return () => controller.abort();
  }, [load, version]);
  return { state, reload: () => setVersion((value) => value + 1) };
}
