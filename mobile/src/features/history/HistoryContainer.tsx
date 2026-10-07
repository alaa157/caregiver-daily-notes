import React, {useCallback, useEffect, useState} from 'react';
import {ApiViewState} from '../../components/ApiStateView';
import {ApiError} from '../../lib/errors';
import {useAuth} from '../auth/AuthContext';
import {Note} from '../../types/api';
import {queryHistory} from './api';
import HistoryScreen from './HistoryScreen';

function toApiError(e: unknown): ApiError {
  return e instanceof ApiError ? e : new ApiError('UNKNOWN', 'Unexpected error');
}

// Part A container: history for the seeded demo recipients through the
// injected client. Recipient/date filters stay client-side (HistoryScreen).
export default function HistoryContainer(): React.JSX.Element {
  const {authedFetch} = useAuth();
  const [notes, setNotes] = useState<Note[]>([]);
  const [state, setState] = useState<ApiViewState>('loading');
  const [error, setError] = useState<ApiError | null>(null);

  const load = useCallback(async () => {
    setState('loading');
    setError(null);
    try {
      const [r1, r2] = await Promise.all([
        authedFetch(client => queryHistory(client, 'r1')),
        authedFetch(client => queryHistory(client, 'r2')),
      ]);
      const all = [...r1, ...r2];
      setNotes(all);
      setState(all.length === 0 ? 'empty' : 'content');
    } catch (e) {
      setError(toApiError(e));
      setState('error');
    }
  }, [authedFetch]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <HistoryScreen
      notes={notes}
      recipientIds={['r1', 'r2']}
      state={state}
      error={error}
      onRetry={() => void load()}
    />
  );
}
