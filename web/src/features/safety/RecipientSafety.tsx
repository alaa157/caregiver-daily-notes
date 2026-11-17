import { useCallback, useEffect, useMemo, useState } from 'react';
import { createSafetySignalsApi } from '../../api/safetySignals';
import type { SummarySafety as Safety } from '../../api/summary';
import { useI18n } from '../../i18n/I18nProvider';
import { useApiClient } from '../../lib/api';
import { SummarySafety } from './SummarySafety';
import '../summary/summary.css';

type SignalsState =
  | { status: 'loading' }
  | { status: 'ready'; safety: Safety }
  | { status: 'error' };

/**
 * Red flags on a non-AI surface. The endpoint needs no provider key, so the
 * deterministic rules are visible even when no AI provider is configured.
 */
export function RecipientSafety({ recipientId, periodEndInclusive, periodDays = 7 }: {
  recipientId: string;
  periodEndInclusive: string;
  periodDays?: 7 | 14 | 30;
}) {
  const { t } = useI18n();
  const client = useApiClient();
  const api = useMemo(() => createSafetySignalsApi(client), [client]);
  const [state, setState] = useState<SignalsState>({ status: 'loading' });

  const load = useCallback((signal: AbortSignal) => {
    setState({ status: 'loading' });
    return api.evaluate(recipientId, { periodEndInclusive, periodDays }, signal)
      .then((safety) => {
        if (!signal.aborted) setState(safety.incomplete ? { status: 'error' } : { status: 'ready', safety });
      })
      .catch(() => {
        if (!signal.aborted) setState({ status: 'error' });
      });
  }, [api, recipientId, periodEndInclusive, periodDays]);

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);

  if (state.status === 'loading') {
    return <p className="safety-signals-status" role="status">{t('safetySignalsLoading')}</p>;
  }
  if (state.status === 'error') {
    return <p className="safety-signals-status">{t('safetySignalsUnavailable')}</p>;
  }
  return <SummarySafety safety={state.safety} />;
}
