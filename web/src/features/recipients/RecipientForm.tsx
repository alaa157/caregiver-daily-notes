import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError } from '../../lib/api';
import { recipientErrorMessage, validateRecipient, type RecipientInput } from '../../api/recipients';
import { Button, ButtonLink } from '../../components/Button';
import { FieldError } from '../../components/Feedback';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';

export function RecipientForm({ initial, onSave, cancelTo, onReload }: {
  initial?: RecipientInput;
  onSave: (input: RecipientInput, signal: AbortSignal) => Promise<void>;
  cancelTo: string;
  onReload?: () => void;
}) {
  const { t } = useI18n();
  const [name, setName] = useState(initial?.name ?? '');
  const [relationship, setRelationship] = useState(initial?.relationship ?? '');
  const [errors, setErrors] = useState<Partial<Record<keyof RecipientInput, MessageKey>>>({});
  const [failure, setFailure] = useState<{ key: MessageKey; conflict: boolean } | null>(null);
  const [saving, setSaving] = useState(false);
  const request = useRef<AbortController | null>(null);
  const nameRef = useRef<HTMLInputElement>(null);
  const relationshipRef = useRef<HTMLInputElement>(null);
  const failureRef = useRef<HTMLDivElement>(null);

  useEffect(() => () => request.current?.abort(), []);
  useEffect(() => { if (failure) failureRef.current?.focus(); }, [failure]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (request.current || failure?.conflict) return;
    const input = { name, relationship };
    const nextErrors = validateRecipient(input);
    setErrors(nextErrors);
    setFailure(null);
    if (nextErrors.name || nextErrors.relationship) {
      (nextErrors.name ? nameRef : relationshipRef).current?.focus();
      return;
    }
    const controller = new AbortController();
    request.current = controller;
    setSaving(true);
    try {
      await onSave(input, controller.signal);
    } catch (error) {
      if (!controller.signal.aborted) setFailure({ key: recipientErrorMessage(error), conflict: error instanceof ApiError && error.status === 409 });
    } finally {
      if (!controller.signal.aborted) {
        request.current = null;
        setSaving(false);
      }
    }
  }

  return <form className="recipient-form" aria-label={initial ? t('recipientEdit') : t('recipientCreate')} noValidate onSubmit={(event) => { void submit(event); }}>
    {failure && <div ref={failureRef} role="alert" tabIndex={-1} className="recipient-error">
      <p>{t(failure.key)}</p>
      {failure.conflict && onReload && <Button variant="secondary" onClick={onReload}>{t('recipientReload')}</Button>}
    </div>}
    <div className="recipient-field">
      <label htmlFor="recipient-name">{t('recipientName')}</label>
      <input id="recipient-name" ref={nameRef} name="name" dir="auto" autoComplete="off" required maxLength={120}
        value={name} readOnly={saving || failure?.conflict} aria-invalid={!!errors.name}
        aria-describedby={'recipient-name-hint' + (errors.name ? ' recipient-name-error' : '')}
        onChange={(event) => { setName(event.target.value); setErrors((value) => ({ ...value, name: undefined })); }} />
      <p className="recipient-hint" id="recipient-name-hint">{t('recipientNameHint')}</p>
      {errors.name && <FieldError id="recipient-name-error" message={t(errors.name)} />}
    </div>
    <div className="recipient-field">
      <label htmlFor="recipient-relationship">{t('recipientRelationship')}</label>
      <input id="recipient-relationship" ref={relationshipRef} name="relationship" dir="auto" autoComplete="off" maxLength={80}
        value={relationship} readOnly={saving || failure?.conflict} aria-invalid={!!errors.relationship}
        aria-describedby={'recipient-relationship-hint' + (errors.relationship ? ' recipient-relationship-error' : '')}
        onChange={(event) => { setRelationship(event.target.value); setErrors((value) => ({ ...value, relationship: undefined })); }} />
      <p className="recipient-hint" id="recipient-relationship-hint">{t('recipientRelationshipHint')}</p>
      {errors.relationship && <FieldError id="recipient-relationship-error" message={t(errors.relationship)} />}
    </div>
    <div className="recipient-actions">
      <Button type="submit" disabled={saving || failure?.conflict}>{saving ? t('recipientSaving') : initial ? t('recipientSave') : t('recipientCreate')}</Button>
      <ButtonLink variant="secondary" to={cancelTo}>{t('recipientCancel')}</ButtonLink>
    </div>
    <p role="status" className="recipient-progress">{saving ? t('recipientSaving') : ''}</p>
  </form>;
}
