import { useEffect, useState, type FormEvent } from 'react';
import type { Recipient } from '../../api/recipients';
import { Button } from '../../components/Button';
import { FieldError } from '../../components/Feedback';
import { useI18n } from '../../i18n/I18nProvider';
import { validateHistoryFilters, type HistoryErrors, type HistoryFilters } from './historyFilters';

export function HistoryFilterForm({ filters, defaults, recipients, recipientsLoading, onApply, onReset }: {
  filters: HistoryFilters; defaults: HistoryFilters; recipients: Recipient[]; recipientsLoading: boolean;
  onApply: (filters: HistoryFilters) => void; onReset: () => void;
}) {
  const { t } = useI18n();
  const [draft, setDraft] = useState(filters);
  const [mode, setMode] = useState(filters.from === filters.to ? 'day' : 'range');
  const [errors, setErrors] = useState<HistoryErrors>(() => validateHistoryFilters(filters));
  useEffect(() => {
    setDraft(filters);
    setMode(filters.from === filters.to ? 'day' : 'range');
    setErrors(validateHistoryFilters(filters));
  }, [filters.recipientId, filters.from, filters.to]);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next = { ...draft, to: mode === 'day' ? draft.from : draft.to };
    const validation = validateHistoryFilters(next);
    setErrors(validation);
    const field = (['recipientId', 'from', 'to'] as const).find((key) => validation[key]);
    if (field) {
      (event.currentTarget.elements.namedItem(field) as HTMLElement | null)?.focus();
      return;
    }
    onApply(next);
  }
  const dateField = (field: 'from' | 'to', label: string) => <div className="note-field">
    <label htmlFor={`history-${field}`}>{label}</label>
    <input id={`history-${field}`} name={field} type="date" dir="ltr" required value={draft[field]}
      aria-invalid={!!errors[field]} aria-describedby={errors[field] ? `history-${field}-error` : undefined}
      onChange={(event) => { setDraft({ ...draft, [field]: event.target.value }); setErrors({}); }} />
    {errors[field] && <FieldError id={`history-${field}-error`} message={t(errors[field]!)} />}
  </div>;
  return <form className="note-surface history-filters" aria-labelledby="history-filters-title" noValidate onSubmit={submit}>
    <h2 id="history-filters-title">{t('historyFilters')}</h2>
    <p className="note-hint">{t('historyDefaultRange')}</p>
    <div className="note-field history-recipient-filter">
      <label htmlFor="history-recipient">{t('notesRecipient')}</label>
      <select id="history-recipient" name="recipientId" dir="auto" value={draft.recipientId} disabled={recipientsLoading}
        aria-invalid={!!errors.recipientId} aria-describedby={errors.recipientId ? 'history-recipient-error' : undefined}
        onChange={(event) => { setDraft({ ...draft, recipientId: event.target.value }); setErrors({}); }}>
        <option value="">{t('historyAllRecipients')}</option>
        {draft.recipientId && !recipients.some((recipient) => recipient.id === draft.recipientId)
          && <option value={draft.recipientId}>{draft.recipientId}</option>}
        {recipients.map((recipient) => <option key={recipient.id} value={recipient.id} dir="auto">
          {recipient.name}{recipient.active ? '' : ` (${t('historyInactive')})`}
        </option>)}
      </select>
      {errors.recipientId && <FieldError id="history-recipient-error" message={t(errors.recipientId)} />}
    </div>
    <fieldset className="history-date-mode">
      <legend>{t('historyDateMode')}</legend>
      {(['range', 'day'] as const).map((value) => <label key={value}>
        <input type="radio" name="dateMode" value={value} checked={mode === value} onChange={() => { setMode(value); setErrors({}); }} />
        {t(value === 'range' ? 'historyDateRange' : 'historySingleDate')}
      </label>)}
    </fieldset>
    <div className="note-grid">
      {dateField('from', t(mode === 'day' ? 'historyDate' : 'historyFrom'))}
      {mode === 'range' && dateField('to', t('historyTo'))}
    </div>
    <div className="note-actions">
      <Button type="submit">{t('historyApply')}</Button>
      <Button variant="secondary" onClick={() => { setDraft(defaults); setMode('range'); setErrors({}); onReset(); }}>{t('historyReset')}</Button>
    </div>
  </form>;
}
