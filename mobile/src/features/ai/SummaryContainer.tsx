import React, {useCallback, useEffect, useState} from 'react';
import ApiStateView, {ApiViewState} from '../../components/ApiStateView';
import {ApiError} from '../../lib/errors';
import {useAuth} from '../auth/AuthContext';
import {Summary} from '../../types/api';
import {requestSummary} from './api';
import SummaryCard from './SummaryCard';

function toApiError(e: unknown): ApiError {
  return e instanceof ApiError ? e : new ApiError('UNKNOWN', 'Unexpected error');
}

// Part A container: summary + non-dismissible safety banner through the
// injected client. The banner stays visible in degraded states (SummaryCard)
// alongside the state message.
export default function SummaryContainer({
  recipientId,
}: {
  recipientId: string;
}): React.JSX.Element {
  const {authedFetch} = useAuth();
  const [summary, setSummary] = useState<Summary | null>(null);
  const [state, setState] = useState<ApiViewState>('loading');
  const [error, setError] = useState<ApiError | null>(null);

  const load = useCallback(async () => {
    setState('loading');
    setError(null);
    try {
      const res = await authedFetch(client => requestSummary(client, recipientId, 7));
      setSummary(res);
      setState('content');
    } catch (e) {
      setError(toApiError(e));
      setState('error');
    }
  }, [authedFetch, recipientId]);

  useEffect(() => {
    void load();
  }, [load]);

  if (!summary) {
    if (state === 'error') {
      return <ApiStateView state="error" error={error} onRetry={() => void load()} />;
    }
    return <ApiStateView state={state} onRetry={() => void load()} />;
  }
  return <SummaryCard summary={summary} state={state} error={error} onRetry={() => void load()} />;
}
