import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError } from '../../lib/api';
import { recipientErrorMessage, validateRecipient, type Recipient, type RecipientInput, type RecipientLifecycleStatus } from '../../api/recipients';
import { Button, ButtonLink } from '../../components/Button';
import { FieldError } from '../../components/Feedback';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';

const statusMessage: Record<RecipientLifecycleStatus, MessageKey> = {
  ACTIVE: 'recipientActive',
  ARCHIVED: 'recipientArchived',
  LEGACY_INACTIVE_REVIEW: 'recipientNeedsReview',
};

export function RecipientForm({ initial, onSave, onCheckDuplicate, cancelTo, onReload }: {
  initial?: RecipientInput;
  onSave: (input: RecipientInput, key: string | undefined, signal: AbortSignal) => Promise<void>;
  onCheckDuplicate?: (input: RecipientInput, signal: AbortSignal) => Promise<Recipient | null>;
  cancelTo: string;
  onReload?: () => void;
}) {
  const { t } = useI18n();
  const [name, setName] = useState(initial?.name ?? '');
  const [relationship, setRelationship] = useState(initial?.relationship ?? '');
  const [errors, setErrors] = useState<Partial<Record<keyof RecipientInput, MessageKey>>>({});
  const [failure, setFailure] = useState<{ key: MessageKey; conflict: boolean } | null>(null);
  const [duplicate, setDuplicate] = useState<{ recipient: Recipient; input: RecipientInput; key: string } | null>(null);
  const [saving, setSaving] = useState(false);
  const request = useRef<AbortController | null>(null);
  const attempt = useRef<{ key: string; name: string; relationship: string } | null>(null);
  const duplicateBypass = useRef<string | null>(null);
  const nameRef = useRef<HTMLInputElement>(null);
  const relationshipRef = useRef<HTMLInputElement>(null);
  const failureRef = useRef<HTMLDivElement>(null);

  useEffect(() => () => request.current?.abort(), []);
  useEffect(() => { if (failure) failureRef.current?.focus(); }, [failure]);

  async function save(input: RecipientInput, key: string | undefined, checkDuplicate: boolean) {
    const controller = new AbortController();
    request.current = controller;
    setSaving(true);
    try {
      if (checkDuplicate && onCheckDuplicate && key) {
        let existing: Recipient | null = null;
        try {
          existing = await onCheckDuplicate(input, controller.signal);
        } catch {
          if (controller.signal.aborted) return;
          // Duplicate detection is advisory; a list failure must not block creation.
        }
        if (controller.signal.aborted) return;
        if (existing) {
          setDuplicate({ recipient: existing, input, key });
          return;
        }
      }
      await onSave(input, key, controller.signal);
    } catch (error) {
      if (!controller.signal.aborted) setFailure({ key: recipientErrorMessage(error), conflict: error instanceof ApiError && error.status === 409 });
    } finally {
      if (!controller.signal.aborted) {
        request.current = null;
        setSaving(false);
      }
    }
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (request.current || failure?.conflict) return;
    const input = { name, relationship };
    const nextErrors = validateRecipient(input);
    setErrors(nextErrors);
    setFailure(null);
    setDuplicate(null);
    if (nextErrors.name || nextErrors.relationship) {
      (nextErrors.name ? nameRef : relationshipRef).current?.focus();
      return;
    }
    let key: string | undefined;
    if (!initial) {
      if (!attempt.current || attempt.current.name !== name || attempt.current.relationship !== relationship) {
        const bytes = crypto.getRandomValues(new Uint8Array(16));
        attempt.current = {
          key: Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join(''),
          name,
          relationship,
        };
      }
      key = attempt.current.key;
    }
    await save(input, key, !initial && Boolean(onCheckDuplicate) && duplicateBypass.current !== key);
  }

  async function createAnyway() {
    if (!duplicate || request.current) return;
    const pending = duplicate;
    duplicateBypass.current = pending.key;
    setDuplicate(null);
    await save(pending.input, pending.key, false);
  }

  return <form className="recipient-form" aria-label={initial ? t('recipientEdit') : t('recipientCreate')} noValidate onSubmit={(event) => { void submit(event); }}>
    {failure && <div ref={failureRef} role="alert" tabIndex={-1} className="recipient-error">
      <p>{t(failure.key)}</p>
      {failure.conflict && onReload && <Button variant="secondary" onClick={onReload}>{t('recipientReload')}</Button>}
    </div>}
    {duplicate && <div role="alert" className="recipient-duplicate">
      <h2>{t('recipientDuplicateTitle')}</h2>
      <p>{t('recipientDuplicateDescription')}</p>
      <p className="recipient-duplicate-match">
        <bdi>{duplicate.recipient.name}</bdi> — {t(statusMessage[duplicate.recipient.lifecycleStatus])}
      </p>
      <div className="recipient-actions">
        <ButtonLink variant="secondary" to={`/app/recipients/${encodeURIComponent(duplicate.recipient.id)}`}>
          {t('recipientDuplicateOpen')}
        </ButtonLink>
        <Button type="button" onClick={() => { void createAnyway(); }}>{t('recipientDuplicateCreateAnyway')}</Button>
        <Button type="button" variant="secondary" onClick={() => setDuplicate(null)}>{t('recipientCancel')}</Button>
      </div>
    </div>}
    <div className="recipient-field">
      <label htmlFor="recipient-name">{t('recipientName')}</label>
      <input id="recipient-name" ref={nameRef} name="name" dir="auto" autoComplete="off" required maxLength={120}
        value={name} readOnly={saving || failure?.conflict} aria-invalid={!!errors.name}
        aria-describedby={'recipient-name-hint' + (errors.name ? ' recipient-name-error' : '')}
        onChange={(event) => { setName(event.target.value); setDuplicate(null); setErrors((value) => ({ ...value, name: undefined })); }} />
      <p className="recipient-hint" id="recipient-name-hint">{t('recipientNameHint')}</p>
      {errors.name && <FieldError id="recipient-name-error" message={t(errors.name)} />}
    </div>
    <div className="recipient-field">
      <label htmlFor="recipient-relationship">{t('recipientRelationship')}</label>
      <input id="recipient-relationship" ref={relationshipRef} name="relationship" dir="auto" autoComplete="off" maxLength={80}
        value={relationship} readOnly={saving || failure?.conflict} aria-invalid={!!errors.relationship}
        aria-describedby={'recipient-relationship-hint' + (errors.relationship ? ' recipient-relationship-error' : '')}
        onChange={(event) => { setRelationship(event.target.value); setDuplicate(null); setErrors((value) => ({ ...value, relationship: undefined })); }} />
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
