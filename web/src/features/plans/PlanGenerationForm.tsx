import { useState } from 'react';
import { isNoteDate } from '../../api/notes';
import { summaryPeriods, type SummaryRequest } from '../../api/summary';
import { Button } from '../../components/Button';
import { useI18n } from '../../i18n/I18nProvider';

export function PlanGenerationForm({ recipientId, disabled, credentialsRequired, onGenerate }: {
  recipientId: string; disabled: boolean; credentialsRequired: boolean; onGenerate: (request: SummaryRequest) => void;
}) {
  const { t, language } = useI18n();
  const [days, setDays] = useState<SummaryRequest['periodDays']>(7);
  const [end, setEnd] = useState(() => { const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`; });
  const [invalid, setInvalid] = useState(false);
  return <form className="note-surface plan-generation" aria-labelledby="plan-generation-title" noValidate onSubmit={(event) => {
    event.preventDefault(); if (disabled) return;
    setInvalid(!isNoteDate(end)); if (isNoteDate(end)) onGenerate({ recipientId, periodDays: days, periodEndInclusive: end });
  }}>
    <h2 id="plan-generation-title">{t('plansGenerateTitle')}</h2><p>{t('plansGenerateHint')}</p>
    <div className="note-grid">
      <div className="note-field"><label htmlFor="plan-generation-end">{t('summaryEndDate')}</label><input id="plan-generation-end" type="date" value={end} disabled={disabled} aria-invalid={invalid}
        aria-describedby={invalid ? 'plan-generation-error' : undefined} onChange={(event) => { setEnd(event.target.value); setInvalid(false); }} /></div>
      <div className="note-field"><label htmlFor="plan-generation-period">{t('summaryPeriod')}</label><select id="plan-generation-period" value={days} disabled={disabled} onChange={(event) => setDays(Number(event.target.value) as SummaryRequest['periodDays'])}>
        {summaryPeriods.map((value) => <option value={value} key={value}>{new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(value)} {t('summaryDays')}</option>)}
      </select></div>
    </div>
    {invalid && <p id="plan-generation-error" role="alert" className="note-error">{t('summaryRequiredDate')}</p>}
    {credentialsRequired && <p role="alert" className="note-error">{t('plansByokCredentialsRequired')}</p>}
    <div className="note-actions"><Button type="submit" disabled={disabled}>{t('plansGenerate')}</Button></div>
  </form>;
}
