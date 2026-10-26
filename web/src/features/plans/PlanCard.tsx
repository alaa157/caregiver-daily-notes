import { useId } from 'react';
import type { CarePlan } from '../../api/plans';
import { isRecipientId } from '../../api/recipients';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';
import { Link } from 'react-router-dom';

const statusKeys: Record<string, MessageKey> = {
  SUGGESTED: 'plansStatusSuggested', ACCEPTED: 'plansStatusAccepted', EDITED_AND_ACCEPTED: 'plansStatusEdited',
  DISMISSED: 'plansStatusDismissed', ARCHIVED: 'plansStatusArchived',
};
export function PlanStatus({ status }: { status: string }) {
  const { t } = useI18n();
  const key = Object.prototype.hasOwnProperty.call(statusKeys, status) ? statusKeys[status] : undefined;
  return <span className={`plan-status${status === 'SUGGESTED' ? ' plan-status--suggested' : ''}`}>
    {t(key ?? 'plansStatusUnknown')}{!key && <>: <bdi>{status}</bdi></>}
  </span>;
}

export function revealPlanHistory(id: string) {
  const details = document.getElementById(`plan-history-${id}`);
  if (details instanceof HTMLDetailsElement) {
    details.open = true;
    details.querySelector('summary')?.focus();
    details.scrollIntoView?.({ block: 'start' });
  }
}

export function PlanCard({ plan, historyIds }: { plan: CarePlan; historyIds: ReadonlySet<string> }) {
  const { t, language } = useI18n();
  const headingId = useId();
  const number = new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(plan.version);
  const evidence = plan.evidenceNoteIds?.split(',').filter((item) => item.length > 0) ?? [];
  return <article className="plan-card" aria-labelledby={headingId}>
    <header className="plan-card-heading">
      <h3 id={headingId}>{t('plansVersion')} {number}</h3>
      <PlanStatus status={plan.status} />
    </header>
    <p className="note-hint">{t('plansCreated')}: <time dateTime={plan.createdAt}>{new Intl.DateTimeFormat(language === 'ar' ? 'ar-EG' : 'en', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(plan.createdAt))}</time></p>
    <h4>{t('plansContent')}</h4>
    <div className="plan-content" dir="auto">{plan.content}</div>
    <section className="plan-provenance" aria-label={t('plansProvenance')}>
      <h4>{t('plansProvenance')}</h4>
      <p className="note-hint">{t('plansOriginUnavailable')}</p>
      {plan.sourcePlanId && <p className="note-hint">{t('plansDerived')}</p>}
      {!plan.sourcePlanId && !plan.sourceSummaryId && !evidence.length ? <p>{t('plansNoProvenance')}</p>
        : <dl className="plan-references">
          {plan.sourceSummaryId && <div><dt>{t('plansSourceSummary')}</dt><dd><bdi>{plan.sourceSummaryId}</bdi></dd></div>}
          {plan.sourcePlanId && <div><dt>{t('plansSourcePlan')}</dt><dd>{historyIds.has(plan.sourcePlanId)
            ? <a href={`#plan-history-${plan.sourcePlanId}`} onClick={(event) => { event.preventDefault(); revealPlanHistory(plan.sourcePlanId!); }}><bdi>{plan.sourcePlanId}</bdi></a>
            : <bdi>{plan.sourcePlanId}</bdi>}</dd></div>}
          {evidence.length > 0 && <div><dt>{t('plansEvidence')}</dt><dd><ul>{evidence.map((noteId, index) => <li key={index}>
            {isRecipientId(noteId) ? <Link to={`/app/notes/${noteId}`}><bdi>{noteId}</bdi></Link> : <bdi>{noteId}</bdi>}
          </li>)}</ul></dd></div>}
        </dl>}
    </section>
  </article>;
}
