import { useEffect, useId, useRef, useState } from 'react';
import type { CarePlan, PlanEditRequest } from '../../api/plans';
import { Button } from '../../components/Button';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';

const actions: Record<string, MessageKey> = {
  SCHEDULE_CHECK_IN: 'plansActionCheckIn', UPDATE_MEAL_REMINDER: 'plansActionMeal', LOG_MOBILITY_GOAL: 'plansActionMobility',
  CAREGIVER_EDUCATION_TOPIC: 'plansActionEducation', ESCALATE_TO_DOCTOR: 'plansActionDoctor',
};
const params: Record<string, MessageKey> = {
  withinDays: 'plansParamDays', meal: 'plansParamMeal', time: 'plansParamTime', activity: 'plansParamActivity', frequency: 'plansParamFrequency', topic: 'plansParamTopic', reason: 'plansParamReason',
};
export const actionLabel = (action: string): MessageKey => actions[action];
export const parameterLabel = (key: string): MessageKey => params[key];

export function PlanEditor({ plan, disabled, onCancel, onConfirm }: {
  plan: CarePlan; disabled: boolean; onCancel: () => void; onConfirm: (input: PlanEditRequest) => void;
}) {
  const { t } = useI18n();
  const original = plan.structuredContent!;
  const [values, setValues] = useState(() => original.changes.map((change) => ({ ...change.params })));
  const [review, setReview] = useState(false);
  const [invalid, setInvalid] = useState(false);
  const form = useRef<HTMLFormElement>(null);
  const preview = useRef<HTMLHeadingElement>(null);
  const id = useId();
  useEffect(() => { if (review) preview.current?.focus(); else form.current?.querySelector('input')?.focus(); }, [review]);
  const changed = original.changes.flatMap((change, index) => Object.keys(change.params).filter((key) => values[index][key] !== change.params[key]).map((key) => ({ index, key, before: change.params[key], after: values[index][key] })));
  return <form ref={form} className="plan-editor" noValidate onSubmit={(event) => {
    event.preventDefault();
    if (disabled) return;
    const invalid = !form.current?.checkValidity() || !changed.length || values.some((fields) => Object.values(fields).some((value) => !value.trim() || value.length > 500));
    setInvalid(invalid);
    if (invalid) { form.current?.querySelector<HTMLInputElement>('input:invalid, input')?.focus(); return; }
    setReview(true);
  }} aria-label={t('plansEditHeading')}>
    <h4>{t('plansEditHeading')}</h4><p id={`${id}-hint`}>{t('plansEditHint')}</p>
    {invalid && <p className="note-error" role="alert">{t('plansEditValidation')}</p>}
    {!review ? <>
      {original.changes.map((change, index) => <fieldset className="plan-edit-fields" disabled={disabled} key={index}>
        <legend>{index + 1}. {t(actionLabel(change.action))}</legend>
        {Object.keys(change.params).map((key) => <div className="note-field" key={key}>
          <label htmlFor={`${id}-${index}-${key}`}>{t(parameterLabel(key))}</label>
          <input id={`${id}-${index}-${key}`} dir="auto" type={key === 'withinDays' ? 'number' : key === 'time' ? 'time' : 'text'} min={key === 'withinDays' ? 1 : undefined} max={key === 'withinDays' ? 365 : undefined}
            value={values[index][key]} required maxLength={500} autoComplete="off" aria-describedby={`${id}-hint`}
            aria-invalid={invalid && (!values[index][key].trim() || values[index][key].length > 500)}
            onChange={(event) => { const value = event.target.value; setValues((previous) => previous.map((fields, at) => at === index ? { ...fields, [key]: value } : fields)); setInvalid(false); }} />
        </div>)}
      </fieldset>)}
      <div className="note-actions"><Button variant="secondary" disabled={disabled} onClick={onCancel}>{t('plansCancel')}</Button><Button type="submit" disabled={disabled}>{t('plansReviewEdits')}</Button></div>
    </> : <div className="plan-confirmation">
      <h4 ref={preview} tabIndex={-1}>{t('plansEditReview')}</h4><p>{t('plansEditConfirmHint')}</p>
      <ul>{changed.map(({ index, key, before, after }) => <li key={`${index}-${key}`}><strong>{t(actionLabel(original.changes[index].action))} — {t(parameterLabel(key))}</strong>
        <dl><dt>{t('plansBefore')}</dt><dd dir="auto" className="note-verbatim">{before}</dd><dt>{t('plansAfter')}</dt><dd dir="auto" className="note-verbatim">{after}</dd></dl>
      </li>)}</ul>
      <div className="note-actions"><Button variant="secondary" disabled={disabled} onClick={() => setReview(false)}>{t('plansBackToEdit')}</Button>
        <Button variant="secondary" disabled={disabled} onClick={onCancel}>{t('plansCancel')}</Button>
        <Button disabled={disabled} onClick={() => onConfirm({ expectedVersion: plan.version, changes: values.map((params, index) => ({ index, params })) })}>{t('plansConfirmEdit')}</Button></div>
    </div>}
  </form>;
}
