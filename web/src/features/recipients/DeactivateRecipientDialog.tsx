import { useEffect, useRef, useState } from 'react';
import { recipientErrorMessage, type Recipient } from '../../api/recipients';
import { Button } from '../../components/Button';
import { useI18n } from '../../i18n/I18nProvider';
import type { MessageKey } from '../../i18n/translations';

export function DeactivateRecipientDialog({ recipient, onClose, onConfirm }: {
  recipient: Recipient;
  onClose: () => void;
  onConfirm: (signal: AbortSignal) => Promise<void>;
}) {
  const { t } = useI18n();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);
  const errorRef = useRef<HTMLDivElement>(null);
  const request = useRef<AbortController | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<MessageKey | null>(null);

  useEffect(() => {
    const dialog = dialogRef.current!;
    const trigger = document.activeElement;
    dialog.showModal();
    cancelRef.current?.focus();
    return () => {
      request.current?.abort();
      dialog.close();
      if (trigger instanceof HTMLElement && trigger.isConnected) trigger.focus();
    };
  }, []);
  useEffect(() => { if (error) errorRef.current?.focus(); }, [error]);

  async function confirm() {
    if (request.current) return;
    const controller = new AbortController();
    request.current = controller;
    setError(null);
    setSaving(true);
    try {
      await onConfirm(controller.signal);
      if (!controller.signal.aborted) onClose();
    } catch (failure) {
      if (!controller.signal.aborted) setError(recipientErrorMessage(failure));
    } finally {
      if (!controller.signal.aborted) { request.current = null; setSaving(false); }
    }
  }

  return <dialog ref={dialogRef} className="recipient-dialog" aria-modal="true" aria-labelledby="deactivate-title" aria-describedby="deactivate-description"
    onKeyDown={(event) => {
      if (event.key !== 'Tab') return;
      const buttons = Array.from(event.currentTarget.querySelectorAll<HTMLButtonElement>('button:not(:disabled)'));
      const first = buttons[0];
      const last = buttons[buttons.length - 1];
      if (!first) { event.preventDefault(); return; }
      const outsideButtons = !buttons.some((button) => button === document.activeElement);
      if (event.shiftKey && (document.activeElement === first || outsideButtons)) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && (document.activeElement === last || outsideButtons)) { event.preventDefault(); first.focus(); }
    }}
    onCancel={(event) => { event.preventDefault(); if (!request.current) onClose(); }}>
    <h2 id="deactivate-title">{t('recipientDeactivateTitle')}</h2>
    <p className="recipient-dialog-name"><bdi>{recipient.name}</bdi></p>
    <p id="deactivate-description">{t('recipientDeactivateDescription')}</p>
    {error && <div ref={errorRef} role="alert" tabIndex={-1} className="recipient-error"><p>{t(error)}</p></div>}
    <div className="recipient-actions">
      <button ref={cancelRef} type="button" className="button button--secondary" disabled={saving} onClick={onClose}>{t('recipientCancel')}</button>
      <Button className="recipient-danger" disabled={saving} onClick={() => { void confirm(); }}>{saving ? t('recipientDeactivating') : t('recipientConfirmDeactivate')}</Button>
    </div>
    <p role="status" className="recipient-progress">{saving ? t('recipientDeactivating') : ''}</p>
  </dialog>;
}
