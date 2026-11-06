import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { createPlansApi, isValidByokCredentials, planErrorMessage, type CarePlan, type CreateStructuredPlanInput, type PlanAction, type PlanEditRequest } from '../../api/plans';
import { createNotesApi, noteErrorMessage, type NoteListItem } from '../../api/notes';
import { createRecipientsApi, isRecipientId, type Recipient } from '../../api/recipients';
import { ApiError, useApiClient } from '../../lib/api';
import { Button, ButtonLink } from '../../components/Button';
import { EmptyState, ErrorState, LoadingState } from '../../components/Feedback';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';
import { defaultHistoryFilters } from '../history/historyFilters';
import { PlanCard, PlanStatus } from './PlanCard';
import { PlanDraftForm } from './PlanDraftForm';
import { PlanEditor } from './PlanEditor';
import { PlanGenerationForm } from './PlanGenerationForm';
import { retainSafety, type SummaryRequest, type SummarySafety as Safety } from '../../api/summary';
import { SummarySafety } from '../safety/SummarySafety';
import { useByok } from '../ai/ByokContext';
import '../summary/summary.css';
import '../notes/notes.css';
import './plans.css';

interface Snapshot { recipient: Recipient; active: CarePlan | null; history: CarePlan[] }
type Resource<T> = { status: 'loading' } | { status: 'error'; error: unknown } | { status: 'ready'; data: T };
class SnapshotChanged extends Error {}
const readTimeoutMs = 20_000;

function Confirmation({ action, disabled, onCancel, onConfirm }: {
  action: PlanAction; disabled: boolean; onCancel: () => void; onConfirm: () => void;
}) {
  const { t } = useI18n();
  const cancel = useRef<HTMLButtonElement>(null);
  const hintId = useId();
  useEffect(() => { cancel.current?.focus(); }, []);
  return <div className="plan-confirmation" role="group" aria-label={t(action === 'accept' ? 'plansConfirmAccept' : 'plansConfirmDismiss')} aria-describedby={hintId}>
    <p id={hintId}>{t(action === 'accept' ? 'plansAcceptHint' : 'plansDismissHint')}</p>
    <div className="note-actions">
      <button className="button button--secondary" type="button" ref={cancel} disabled={disabled} onClick={onCancel}>{t('plansCancel')}</button>
      <Button disabled={disabled} onClick={onConfirm}>{t(action === 'accept' ? 'plansConfirmAccept' : 'plansConfirmDismiss')}</Button>
    </div>
  </div>;
}

function PlanWorkspace({ recipientId, onSaving }: { recipientId: string; onSaving: (busy: boolean) => void }) {
  const { t, language } = useI18n();
  const { credentials } = useByok();
  const client = useApiClient();
  const api = useMemo(() => ({ plans: createPlansApi(client), recipients: createRecipientsApi(client), notes: createNotesApi(client) }), [client]);
  const [state, setState] = useState<Resource<Snapshot>>({ status: 'loading' });
  const [revision, setRevision] = useState(0);
  const [evidenceState, setEvidenceState] = useState<Resource<NoteListItem[]>>({ status: 'loading' });
  const [evidenceRevision, setEvidenceRevision] = useState(0);
  const [confirmation, setConfirmation] = useState<{ plan: CarePlan; action: PlanAction } | null>(null);
  const [editing, setEditing] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [creatingDraft, setCreatingDraft] = useState(false);
  const [credentialsRequired, setCredentialsRequired] = useState(false);
  const [generationSafety, setGenerationSafety] = useState<{ request: SummaryRequest; safety: Safety } | null>(null);
  const [blocked, setBlocked] = useState(false);
  const [failure, setFailure] = useState<MessageKey | null>(null);
  const [notice, setNotice] = useState<MessageKey | null>(null);
  const pendingNotice = useRef<MessageKey | null>(null);
  const mutation = useRef<AbortController | null>(null);
  const trigger = useRef<HTMLButtonElement | null>(null);
  const status = useRef<HTMLDivElement>(null);
  const results = useRef<HTMLHeadingElement>(null);
  const focusResults = useRef(false);
  const mounted = useRef(true);
  const restoreFocus = useRef(false);

  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(() => { controller.abort(); setState({ status: 'error', error: new Error('Read timed out.') }); }, readTimeoutMs);
    setState({ status: 'loading' });
    void Promise.all([api.recipients.get(recipientId, controller.signal), api.plans.active(recipientId, controller.signal), api.plans.history(recipientId, controller.signal)])
      .then(([recipient, active, history]) => {
        if (controller.signal.aborted) return;
        // These reads are not an atomic backend snapshot. Refuse inconsistent data
        // instead of inferring/reconciling lifecycle state on the client.
        const accepted = history.filter((plan) => plan.status === 'ACCEPTED' || plan.status === 'EDITED_AND_ACCEPTED');
        if (active ? accepted.length !== 1 || !accepted.some((plan) => plan.id === active.id && plan.content === active.content && plan.status === active.status && plan.version === active.version)
          : accepted.length > 0) throw new SnapshotChanged();
        setState({ status: 'ready', data: { recipient, active, history } });
        setBlocked(false); setFailure(null); setNotice(pendingNotice.current); pendingNotice.current = null;
      }).catch((error: unknown) => { if (!controller.signal.aborted) setState({ status: 'error', error }); })
      .finally(() => clearTimeout(timer));
    return () => { controller.abort(); clearTimeout(timer); };
  }, [api, recipientId, revision]);

  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(() => {
      controller.abort();
      setEvidenceState({ status: 'error', error: new Error('Read timed out.') });
    }, readTimeoutMs);
    const { from, to } = defaultHistoryFilters();
    setEvidenceState({ status: 'loading' });
    void api.notes.history({ from, to, recipientId, page: 0, size: 100 }, controller.signal)
      .then((page) => { if (!controller.signal.aborted) setEvidenceState({ status: 'ready', data: page.content }); })
      .catch((error: unknown) => { if (!controller.signal.aborted) setEvidenceState({ status: 'error', error }); })
      .finally(() => clearTimeout(timer));
    return () => { controller.abort(); clearTimeout(timer); };
  }, [api, recipientId, evidenceRevision]);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; mutation.current?.abort(); mutation.current = null; onSaving(false); };
  }, [onSaving]);
  useEffect(() => {
    if (!confirmation && !editing && restoreFocus.current) { trigger.current?.focus(); restoreFocus.current = false; }
  }, [confirmation, editing]);
  useEffect(() => {
    if (saving || failure || notice || (state.status !== 'loading' && focusResults.current)) {
      (status.current ?? results.current)?.focus();
      focusResults.current = false;
    }
  }, [saving, failure, notice, state.status]);
  useEffect(() => {
    if (!saving) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [saving]);

  function reload(after: MessageKey = 'plansUpdated') {
    if (mutation.current) return;
    pendingNotice.current = after; focusResults.current = true;
    setConfirmation(null); setEditing(null); setNotice(null); setRevision((value) => value + 1);
  }
  async function decide(choice = confirmation, edit?: PlanEditRequest) {
    if (!choice || mutation.current || blocked || state.status !== 'ready' || !state.data.recipient.active) return;
    const controller = new AbortController(); mutation.current = controller;
    const timer = setTimeout(() => controller.abort(), readTimeoutMs);
    const { plan, action } = choice;
    setSaving(true); onSaving(true); setNotice(null); setFailure(null);
    try {
      if (action === 'edit-and-accept' && edit) await api.plans.editAndAccept(recipientId, plan, edit, controller.signal);
      else await api.plans.transition(recipientId, plan.id, action, controller.signal);
      if (!mounted.current) return;
      if (controller.signal.aborted) throw new Error('Unconfirmed decision.');
      // Clear the mutation guard before initiating the explicit read refresh.
      mutation.current = null;
      reload(action === 'edit-and-accept' ? 'plansEdited' : action === 'accept' ? 'plansAccepted' : 'plansDismissed');
    } catch (error) {
      if (!mounted.current || mutation.current !== controller) return;
      setBlocked(true); setConfirmation(null);
      const definiteRejection = error instanceof ApiError && [400, 403, 404, 409, 422, 429].includes(error.status);
      setFailure(definiteRejection ? planErrorMessage(error) : 'plansUncertain');
    } finally {
      clearTimeout(timer);
      if (mutation.current === controller) mutation.current = null;
      // Cleanup aborts on navigation; do not focus/update an abandoned screen.
      if (mounted.current) { setSaving(false); onSaving(false); }
    }
  }

  async function generate(request: SummaryRequest) {
    if (mutation.current || blocked || state.status !== 'ready' || !state.data.recipient.active) return;
    if (!isValidByokCredentials(credentials)) {
      setCredentialsRequired(true);
      return;
    }
    setCredentialsRequired(false);
    const controller = new AbortController(); mutation.current = controller;
    const timer = setTimeout(() => controller.abort(), 120_000);
    setSaving(true); setGenerating(true); onSaving(true); setNotice(null); setFailure(null);
    try {
      const response = await api.plans.generateByok(request, credentials, controller.signal);
      if (!mounted.current) return;
      if (controller.signal.aborted) throw new Error('Unconfirmed generation.');
      setGenerationSafety((previous) => ({ request, safety: retainSafety(previous && JSON.stringify(previous.request) === JSON.stringify(request) ? previous.safety : undefined, response.safety, response.invalidPlan) }));
      if (response.invalidPlan) throw new Error('Unconfirmed plan response.');
      mutation.current = null;
      if (response.aiUnavailable) setNotice('plansGenerationUnavailable'); else reload('plansGenerated');
    } catch (error) {
      if (!mounted.current) return;
      setBlocked(true); setFailure(error instanceof ApiError && [400, 403, 404, 409, 422, 429].includes(error.status) ? planErrorMessage(error) : 'plansUncertain');
    } finally {
      clearTimeout(timer); if (mutation.current === controller) mutation.current = null;
      if (mounted.current) { setSaving(false); setGenerating(false); onSaving(false); }
    }
  }

  async function createDraft(input: CreateStructuredPlanInput) {
    if (mutation.current || blocked || state.status !== 'ready' || !state.data.recipient.active) return;
    const controller = new AbortController(); mutation.current = controller;
    const timer = setTimeout(() => controller.abort(), readTimeoutMs);
    setSaving(true); setCreatingDraft(true); onSaving(true); setNotice(null); setFailure(null);
    try {
      await api.plans.createSuggested(recipientId, input, controller.signal);
      if (!mounted.current) return;
      if (controller.signal.aborted) throw new Error('Unconfirmed draft creation.');
      mutation.current = null;
      reload('plansDraftSaved');
    } catch (error) {
      if (!mounted.current || mutation.current !== controller) return;
      setBlocked(true);
      const definiteRejection = error instanceof ApiError && [400, 403, 404, 409, 422, 429].includes(error.status);
      setFailure(definiteRejection
        ? error instanceof ApiError && [400, 422].includes(error.status) ? 'plansDraftRejected' : planErrorMessage(error)
        : 'plansUncertain');
    } finally {
      clearTimeout(timer); if (mutation.current === controller) mutation.current = null;
      if (mounted.current) { setSaving(false); setCreatingDraft(false); onSaving(false); }
    }
  }

  const snapshot = state.status === 'ready' ? state.data : null;
  const historyIds = new Set(snapshot?.history.map((plan) => plan.id));
  const suggestions = snapshot?.history.filter((plan) => plan.status === 'SUGGESTED') ?? [];
  const disabled = saving || blocked || !snapshot?.recipient.active;
  return <section className="plan-workspace" aria-busy={state.status === 'loading' || saving} aria-labelledby="plans-results">
    <div className="plan-results-heading"><h2 id="plans-results" ref={results} tabIndex={-1}>{snapshot ? <bdi>{snapshot.recipient.name}</bdi> : t('plansTitle')}</h2>
      <Button variant="secondary" onClick={() => reload()} disabled={saving || state.status === 'loading'}>{t('plansRefresh')}</Button></div>
    {(saving || failure || notice) && <div className={failure ? 'note-error plan-message' : 'note-success plan-message'} role={failure ? 'alert' : 'status'} ref={status} tabIndex={-1}>
      <p>{t(saving ? (creatingDraft ? 'plansCreatingDraft' : generating ? 'plansGenerating' : 'plansSaving') : failure ?? notice!)}</p>{failure && <p>{t('plansReloadRequired')}</p>}
    </div>}
    {state.status === 'loading' && <LoadingState label={t('plansLoading')} />}
    {state.status === 'error' && <div ref={status} tabIndex={-1}><ErrorState description={t(state.error instanceof SnapshotChanged ? 'plansInconsistent' : planErrorMessage(state.error))} onRetry={() => reload()} /></div>}
    {generationSafety && <div className="plan-generation-safety"><p>{t('plansGenerationSafety')}: <bdi>{generationSafety.request.periodEndInclusive}</bdi> · {generationSafety.request.periodDays} {t('summaryDays')}</p><SummarySafety safety={generationSafety.safety} /></div>}
    {snapshot && <>
      {!snapshot.recipient.active && <p className="note-notice">{t('plansInactive')}</p>}
      <PlanGenerationForm recipientId={recipientId} disabled={disabled || !!confirmation || !!editing}
        credentialsRequired={credentialsRequired && !isValidByokCredentials(credentials)} onGenerate={(request) => { void generate(request); }} />
      <section className="note-surface" aria-label={t('plansCreateDraft')}>
        {evidenceState.status === 'loading' && <LoadingState label={t('plansDraftEvidenceLoading')} />}
        {evidenceState.status === 'error' && <ErrorState description={t(noteErrorMessage(evidenceState.error))} onRetry={() => setEvidenceRevision((value) => value + 1)} />}
        {evidenceState.status === 'ready' && <PlanDraftForm evidenceNotes={evidenceState.data}
          disabled={disabled || !!confirmation || !!editing} onSave={(input) => { void createDraft(input); }} />}
      </section>
      <section className="note-surface" aria-labelledby="plans-active"><h2 id="plans-active">{t('plansActive')}</h2>
        {snapshot.active ? <PlanCard plan={snapshot.active} historyIds={historyIds} /> : <EmptyState title={t('plansNoActive')} description={t('plansNoActiveHint')} />}
      </section>
      <section className="plan-suggestions" aria-labelledby="plans-suggested"><h2 id="plans-suggested">{t('plansSuggested')}</h2>
        <p>{t('plansSuggestionsHint')}</p>
        {!suggestions.length ? <EmptyState title={t('plansNoSuggestions')} description={t('plansNoSuggestionsHint')} />
          : <ul className="plan-list" role="list">{suggestions.map((plan) => <li className="note-surface" key={plan.id}>
            <PlanCard plan={plan} historyIds={historyIds} />
            <div className="note-actions">
              {(['accept', 'dismiss'] as const).map((action) => <Button key={action} variant={action === 'accept' ? 'primary' : 'secondary'} disabled={disabled || !!confirmation || !!editing}
                onClick={(event) => { trigger.current = event.currentTarget; setConfirmation({ plan, action }); }}>
                {t(action === 'accept' ? 'plansAccept' : 'plansDismiss')}</Button>)}
              <Button variant="secondary" disabled={disabled || !plan.editable || !!confirmation || !!editing} aria-describedby={!plan.editable ? 'plans-edit-help' : undefined}
                onClick={(event) => { trigger.current = event.currentTarget; setEditing(plan.id); }}>{t('plansEdit')}</Button>
            </div>
            {confirmation?.plan.id === plan.id && <Confirmation action={confirmation.action} disabled={saving}
              onCancel={() => { restoreFocus.current = true; setConfirmation(null); }} onConfirm={() => { void decide(); }} />}
            {editing === plan.id && <PlanEditor plan={plan} disabled={disabled} onCancel={() => { restoreFocus.current = true; setEditing(null); }}
              onConfirm={(input) => { void decide({ plan, action: 'edit-and-accept' }, input); }} />}
          </li>)}</ul>}
      </section>
      <section className="note-surface" aria-labelledby="plans-history"><h2 id="plans-history">{t('plansHistory')}</h2>
        <p>{t('plansHistoryHint')}</p>
        {!snapshot.history.length ? <EmptyState title={t('plansNoHistory')} description={t('plansNoHistoryHint')} />
          : <ol className="plan-list" role="list">{snapshot.history.map((plan) => <li key={plan.id}>
            <details id={`plan-history-${plan.id}`} className="plan-history-version"><summary>
              <span>{t('plansVersion')} {new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(plan.version)}</span> <PlanStatus status={plan.status} />
              <span>{t('plansCreated')}: <time dateTime={plan.createdAt}>{new Intl.DateTimeFormat(language === 'ar' ? 'ar-EG' : 'en', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(plan.createdAt))}</time></span>
            </summary><PlanCard plan={plan} historyIds={historyIds} /></details>
          </li>)}</ol>}
      </section>
    </>}
  </section>;
}

export function PlansPage() {
  const { t } = useI18n();
  const client = useApiClient();
  const recipientsApi = useMemo(() => createRecipientsApi(client), [client]);
  const [recipients, setRecipients] = useState<Resource<Recipient[]>>({ status: 'loading' });
  const [revision, setRevision] = useState(0);
  const [search, setSearch] = useSearchParams();
  const recipientId = search.get('recipientId') ?? '';
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(() => { controller.abort(); setRecipients({ status: 'error', error: new Error('Read timed out.') }); }, readTimeoutMs);
    setRecipients({ status: 'loading' });
    void recipientsApi.list(controller.signal, 'ALL').then(
      (data) => { if (!controller.signal.aborted) setRecipients({ status: 'ready', data }); },
      (error: unknown) => { if (!controller.signal.aborted) setRecipients({ status: 'error', error }); },
    ).finally(() => clearTimeout(timer));
    return () => { controller.abort(); clearTimeout(timer); };
  }, [recipientsApi, revision]);
  const validRecipient = recipients.status === 'ready' && isRecipientId(recipientId) && recipients.data.some((recipient) => recipient.id === recipientId);
  return <section className="notes-page plans-page container" aria-labelledby="plans-heading">
    <header className="notes-heading"><div><p className="eyebrow">{t('dashboardTitle')}</p><h1 id="plans-heading">{t('plansTitle')}</h1><p>{t('plansDescription')}</p></div>
      <ButtonLink to="/app/notes" variant="secondary">{t('historyTitle')}</ButtonLink></header>
    <div className="note-notice plan-lock"><p>{t('plansMedicationLock')}</p><p id="plans-edit-help">{t('plansEditUnavailable')}</p></div>
    {recipients.status === 'loading' && <LoadingState />}
    {recipients.status === 'error' && <ErrorState description={t(planErrorMessage(recipients.error))} onRetry={() => setRevision((value) => value + 1)} />}
    {recipients.status === 'ready' && (recipients.data.length ? <>
      <div className="note-field plan-recipient"><label htmlFor="plans-recipient">{t('plansRecipient')}</label>
        <select id="plans-recipient" value={validRecipient ? recipientId : ''} disabled={saving} aria-invalid={!!recipientId && !validRecipient} aria-describedby={recipientId && !validRecipient ? 'plans-recipient-error' : undefined}
          onChange={(event) => setSearch(event.target.value ? { recipientId: event.target.value } : {})}>
          <option value="">{t('plansChoose')}</option>{recipients.data.map((recipient) => <option key={recipient.id} value={recipient.id}>{recipient.name}{recipient.lifecycleStatus === 'ARCHIVED' ? ` — ${t('recipientArchived')}` : recipient.lifecycleStatus === 'LEGACY_INACTIVE_REVIEW' ? ` — ${t('recipientNeedsReview')}` : ''}</option>)}
        </select>
      </div>
      {recipientId && !validRecipient && <p id="plans-recipient-error" className="note-error" role="alert">{t('plansInvalidRecipient')}</p>}
      {validRecipient ? <PlanWorkspace key={recipientId} recipientId={recipientId} onSaving={setSaving} /> : <p role="status">{t('plansSelectHint')}</p>}
    </> : <div className="note-surface"><EmptyState title={t('plansNoRecipients')} description={t('plansNoRecipientsHint')} /><ButtonLink to="/app/recipients/new">{t('recipientCreate')}</ButtonLink></div>)}
  </section>;
}
