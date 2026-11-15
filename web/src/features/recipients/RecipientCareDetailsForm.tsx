import { useEffect, useRef, useState, type FormEvent } from 'react';
import {
  recipientErrorMessage,
  validateCareDetails,
  type Recipient,
  type RecipientCareDetailsInput,
} from '../../api/recipients';
import { Button } from '../../components/Button';
import { FieldError } from '../../components/Feedback';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';

type CareDetailsField = keyof RecipientCareDetailsInput;
type CareDetailsValues = Record<CareDetailsField, string>;
type CareDetailsErrors = Partial<Record<CareDetailsField, MessageKey>>;

function todayDate() {
  const today = new Date();
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
}

function valuesFromRecipient(recipient: Recipient): CareDetailsValues {
  return {
    dateOfBirth: recipient.dateOfBirth ?? '',
    allergies: recipient.allergies ?? '',
    conditions: recipient.conditions ?? '',
    emergencyContactName: recipient.emergencyContactName ?? '',
    emergencyContactPhone: recipient.emergencyContactPhone ?? '',
  };
}

function careDetailsErrors(values: CareDetailsValues, today: string): CareDetailsErrors {
  const validation = validateCareDetails(values);
  const errors: CareDetailsErrors = {};
  if (validation.dateOfBirth || (values.dateOfBirth && values.dateOfBirth > today)) {
    errors.dateOfBirth = 'careDetailsInvalidDate';
  }
  if (validation.allergies) errors.allergies = validation.allergies;
  if (validation.conditions) errors.conditions = validation.conditions;
  if (validation.emergencyContactName) errors.emergencyContactName = validation.emergencyContactName;
  if (validation.emergencyContactPhone) errors.emergencyContactPhone = 'careDetailsInvalidPhone';
  return errors;
}

export function RecipientCareDetailsForm({ initial, onSave }: {
  initial: Recipient;
  onSave: (input: RecipientCareDetailsInput, signal: AbortSignal) => Promise<Recipient>;
}) {
  const { t } = useI18n();
  const [values, setValues] = useState(() => valuesFromRecipient(initial));
  const [errors, setErrors] = useState<CareDetailsErrors>({});
  const [failure, setFailure] = useState<MessageKey | null>(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const request = useRef<AbortController | null>(null);
  const failureRef = useRef<HTMLDivElement>(null);
  const maxDate = todayDate();

  useEffect(() => () => request.current?.abort(), []);
  useEffect(() => { if (failure) failureRef.current?.focus(); }, [failure]);

  function updateField(field: CareDetailsField, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
    setFailure(null);
    setSaved(false);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (request.current) return;
    const nextErrors = careDetailsErrors(values, todayDate());
    setErrors(nextErrors);
    setFailure(null);
    setSaved(false);
    if (Object.keys(nextErrors).length > 0) return;

    const controller = new AbortController();
    request.current = controller;
    setSaving(true);
    try {
      const updated = await onSave(values, controller.signal);
      if (!controller.signal.aborted) {
        setValues(valuesFromRecipient(updated));
        setSaved(true);
      }
    } catch (error) {
      if (!controller.signal.aborted) setFailure(recipientErrorMessage(error));
    } finally {
      if (!controller.signal.aborted) {
        request.current = null;
        setSaving(false);
      }
    }
  }

  return <form className="recipient-form recipient-care-details-form" aria-label={t('careDetailsTitle')} noValidate onSubmit={(event) => { void submit(event); }}>
    <div>
      <h3>{t('careDetailsTitle')}</h3>
      <p className="recipient-hint">{t('careDetailsHint')}</p>
    </div>
    {failure && <div ref={failureRef} role="alert" tabIndex={-1} className="recipient-error"><p>{t(failure)}</p></div>}
    <div className="recipient-field">
      <label htmlFor="care-date-of-birth">{t('careDetailsDateOfBirth')}</label>
      <input id="care-date-of-birth" name="dateOfBirth" type="date" dir="ltr" max={maxDate}
        value={values.dateOfBirth} disabled={saving} aria-invalid={!!errors.dateOfBirth}
        aria-describedby={errors.dateOfBirth ? 'care-date-of-birth-error' : undefined}
        onChange={(event) => updateField('dateOfBirth', event.target.value)} />
      {errors.dateOfBirth && <FieldError id="care-date-of-birth-error" message={t(errors.dateOfBirth)} />}
    </div>
    <div className="recipient-field">
      <label htmlFor="care-allergies">{t('careDetailsAllergies')}</label>
      <textarea id="care-allergies" name="allergies" dir="auto" maxLength={500} value={values.allergies}
        disabled={saving} aria-invalid={!!errors.allergies}
        aria-describedby={errors.allergies ? 'care-allergies-error' : undefined}
        onChange={(event) => updateField('allergies', event.target.value)} />
      {errors.allergies && <FieldError id="care-allergies-error" message={t(errors.allergies)} />}
    </div>
    <div className="recipient-field">
      <label htmlFor="care-conditions">{t('careDetailsConditions')}</label>
      <textarea id="care-conditions" name="conditions" dir="auto" maxLength={500} value={values.conditions}
        disabled={saving} aria-invalid={!!errors.conditions}
        aria-describedby={errors.conditions ? 'care-conditions-error' : undefined}
        onChange={(event) => updateField('conditions', event.target.value)} />
      {errors.conditions && <FieldError id="care-conditions-error" message={t(errors.conditions)} />}
    </div>
    <div className="recipient-field">
      <label htmlFor="care-emergency-contact-name">{t('careDetailsEmergencyContactName')}</label>
      <input id="care-emergency-contact-name" name="emergencyContactName" type="text" dir="auto" maxLength={120}
        value={values.emergencyContactName} disabled={saving} aria-invalid={!!errors.emergencyContactName}
        aria-describedby={errors.emergencyContactName ? 'care-emergency-contact-name-error' : undefined}
        onChange={(event) => updateField('emergencyContactName', event.target.value)} />
      {errors.emergencyContactName && <FieldError id="care-emergency-contact-name-error" message={t(errors.emergencyContactName)} />}
    </div>
    <div className="recipient-field">
      <label htmlFor="care-emergency-contact-phone">{t('careDetailsEmergencyContactPhone')}</label>
      <input id="care-emergency-contact-phone" name="emergencyContactPhone" type="tel" dir="ltr" maxLength={30}
        value={values.emergencyContactPhone} disabled={saving} aria-invalid={!!errors.emergencyContactPhone}
        aria-describedby={errors.emergencyContactPhone ? 'care-emergency-contact-phone-error' : undefined}
        onChange={(event) => updateField('emergencyContactPhone', event.target.value)} />
      {errors.emergencyContactPhone && <FieldError id="care-emergency-contact-phone-error" message={t(errors.emergencyContactPhone)} />}
    </div>
    <div className="recipient-actions">
      <Button type="submit" disabled={saving}>{saving ? t('recipientSaving') : t('careDetailsSave')}</Button>
    </div>
    {saved && <p role="status" className="recipient-success">{t('careDetailsSaved')}</p>}
  </form>;
}
