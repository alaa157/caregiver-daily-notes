import { useEffect, useMemo, useRef, useState } from 'react';
import { isNoteDate } from '../../api/notes';
import { isRecipientId } from '../../api/recipients';
import { createSafetyAcknowledgementsApi, type SafetyAcknowledgement } from '../../api/safetyAcknowledgements';
import type { SummarySafety as Safety } from '../../api/summary';
import { Button } from '../../components/Button';
import { useI18n } from '../../i18n/I18nProvider';
import { useApiClient } from '../../lib/api';

/** Kept outside model-content containers; no dismiss/collapse control. */
export function SummarySafety({ safety, recipientId, periodEndInclusive, periodDays }: {
  safety: Safety;
  recipientId?: string;
  periodEndInclusive?: string;
  periodDays?: 7 | 14 | 30;
}) {
  const { t, language } = useI18n();
  const client = useApiClient();
  const api = useMemo(() => createSafetyAcknowledgementsApi(client), [client]);
  const context = recipientId && isRecipientId(recipientId)
    && periodEndInclusive && isNoteDate(periodEndInclusive)
    && periodDays !== undefined
    ? { recipientId, periodEndInclusive, periodDays } : undefined;
  const contextKey = context ? `${context.recipientId}:${context.periodEndInclusive}:${context.periodDays}` : '';
  const activeRecipientId = context?.recipientId;
  const activePeriodEndInclusive = context?.periodEndInclusive;
  const activePeriodDays = context?.periodDays;
  const [revision, setRevision] = useState(0);
  const [acknowledgements, setAcknowledgements] = useState<{
    contextKey: string; status: 'loading' | 'error' | 'ready'; records: SafetyAcknowledgement[];
  }>({ contextKey: '', status: 'loading', records: [] });
  const [pending, setPending] = useState<ReadonlySet<string>>(() => new Set());
  const [saveErrors, setSaveErrors] = useState<ReadonlySet<string>>(() => new Set());
  const pendingRequests = useRef(new Map<string, AbortController>());
  const mounted = useRef(false);
  const activeContextKey = useRef(contextKey);
  activeContextKey.current = contextKey;

  useEffect(() => {
    if (!activeRecipientId || !activePeriodEndInclusive || activePeriodDays === undefined) return;
    const controller = new AbortController();
    setAcknowledgements((current) => ({
      contextKey,
      status: 'loading',
      records: current.contextKey === contextKey ? current.records : [],
    }));
    void api.list(activeRecipientId, {
      periodEndInclusive: activePeriodEndInclusive,
      periodDays: activePeriodDays,
    }, controller.signal).then((records) => {
      if (controller.signal.aborted) return;
      setAcknowledgements((current) => ({
        contextKey,
        status: 'ready',
        records: mergeAcknowledgements(records, current.contextKey === contextKey ? current.records : []),
      }));
    }).catch(() => {
      if (!controller.signal.aborted) setAcknowledgements((current) => ({
        contextKey,
        status: 'error',
        records: current.contextKey === contextKey ? current.records : [],
      }));
    });
    return () => controller.abort();
  }, [activePeriodDays, activePeriodEndInclusive, activeRecipientId, api, contextKey, revision]);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      pendingRequests.current.forEach((controller) => controller.abort());
      pendingRequests.current.clear();
    };
  }, []);

  async function markReviewed(ruleId: string) {
    if (!context) return;
    const requestKey = `${contextKey}:${ruleId}`;
    if (pendingRequests.current.has(requestKey)) return;
    const controller = new AbortController();
    pendingRequests.current.set(requestKey, controller);
    setPending((current) => new Set(current).add(requestKey));
    setSaveErrors((current) => { const next = new Set(current); next.delete(requestKey); return next; });
    try {
      const acknowledgement = await api.create(context.recipientId, {
        ruleId,
        periodEndInclusive: context.periodEndInclusive,
        periodDays: context.periodDays,
      }, controller.signal);
      if (!controller.signal.aborted && mounted.current && activeContextKey.current === contextKey) {
        setAcknowledgements((current) => ({
          contextKey,
          status: current.contextKey === contextKey ? current.status : 'ready',
          records: mergeAcknowledgements(current.contextKey === contextKey ? current.records : [], [acknowledgement]),
        }));
      }
    } catch {
      if (!controller.signal.aborted && mounted.current && activeContextKey.current === contextKey) {
        setSaveErrors((current) => new Set(current).add(requestKey));
      }
    } finally {
      pendingRequests.current.delete(requestKey);
      if (mounted.current) setPending((current) => { const next = new Set(current); next.delete(requestKey); return next; });
    }
  }

  const acknowledgementState = acknowledgements.contextKey === contextKey
    ? acknowledgements : { contextKey, status: 'loading' as const, records: [] };
  const formatAcknowledgedAt = (value: string) => new Intl.DateTimeFormat(language === 'ar' ? 'ar-EG' : 'en', {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(new Date(value));
  const urgent = safety.needsDoctorBanner || safety.incomplete || safety.signals.some((signal) => signal.severity !== 'INFO' && signal.severity !== 'WATCH');
  return <section className={`summary-safety${urgent ? ' summary-safety--urgent' : ''}`} aria-label={t('summarySafety')}>
    <h3>{t(safety.needsDoctorBanner ? 'summaryClinicalReview' : 'summarySafety')}</h3>
    <p className="summary-safety-source">{t('summarySafetyIndependent')}</p>
    {safety.incomplete && <p className="summary-safety-warning" role="alert">{t('summarySafetyUnknown')}</p>}
    {context && acknowledgementState.status === 'loading' && <p className="summary-safety-source" role="status">{t('summarySafetyAckLoading')}</p>}
    {context && acknowledgementState.status === 'error' && <div className="summary-safety-source" role="alert">
      <p>{t('summarySafetyAckLoadError')}</p>
      <Button variant="secondary" onClick={() => setRevision((value) => value + 1)}>{t('summarySafetyAckRetry')}</Button>
    </div>}
    {safety.signals.length > 0 && <ul className="summary-signals">{safety.signals.map((signal, index) => {
      const ruleId = signal.ruleId;
      const requestKey = `${contextKey}:${ruleId}`;
      const acknowledgement = context && ruleId
        ? acknowledgementState.records.find((record) => record.ruleId === ruleId) : undefined;
      return <li key={index}>
        <div><strong className="summary-severity"><bdi>{signal.severity ?? t('summaryUnknownSeverity')}</bdi></strong>{' '}
          <strong><bdi>{ruleId ?? t('summaryUnknownSignal')}</bdi></strong></div>
        <p className="summary-untrusted" dir="auto">{signal.reason ?? t('summaryNoReason')}</p>
        {context && ruleId && acknowledgement && <p className="summary-safety-source" role="status">
          <strong>{t('summarySafetyAckReviewed')}</strong>{' · '}
          <time dateTime={acknowledgement.acknowledgedAt}>{formatAcknowledgedAt(acknowledgement.acknowledgedAt)}</time>
        </p>}
        {context && ruleId && !acknowledgement && acknowledgementState.status === 'ready' && <div>
          <Button variant="secondary" disabled={pending.has(requestKey)}
            onClick={() => { void markReviewed(ruleId); }}>{t('summarySafetyMarkReviewed')}</Button>
          {saveErrors.has(requestKey) && <p className="summary-safety-source" role="alert">{t('summarySafetyAckError')}</p>}
        </div>}
      </li>;
    })}</ul>}
    {!safety.incomplete && !safety.signals.length && !safety.needsDoctorBanner && <p>{t('summaryNoFlags')}</p>}
  </section>;
}

function mergeAcknowledgements(
  current: SafetyAcknowledgement[],
  updates: SafetyAcknowledgement[],
): SafetyAcknowledgement[] {
  const byRuleId = new Map(current.map((record) => [record.ruleId, record]));
  updates.forEach((record) => byRuleId.set(record.ruleId, record));
  return [...byRuleId.values()];
}
