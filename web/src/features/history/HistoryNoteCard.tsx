import { useCallback, useState } from 'react';
import type { DailyNote } from '../../api/notes';
import { noteErrorMessage } from '../../api/notes';
import type { Recipient } from '../../api/recipients';
import { Button, ButtonLink } from '../../components/Button';
import { ErrorState, LoadingState } from '../../components/Feedback';
import { useI18n } from '../../i18n/I18nProvider';
import { ObservationValues } from '../notes/ObservationValues';
import { CorrectionHistory } from '../notes/CorrectionHistory';
import { NoteTimelineRow } from '../notes/NoteTimelineRow';
import { useNoteResource, useNotesApis } from '../notes/useNotes';

function Corrections({ noteId }: { noteId: string }) {
  const { t, language } = useI18n();
  const api = useNotesApis();
  const load = useCallback(async (signal: AbortSignal) => {
    const [addenda, corrections] = await Promise.all([api.notes.addenda(noteId, signal), api.notes.correctionHistory(noteId, signal)]);
    return { addenda, corrections };
  }, [api, noteId]);
  const { state, reload } = useNoteResource(load);
  return <>
    <p className="note-hint">{t('historyCorrectionsHint')}</p>
    {state.status === 'loading' && <LoadingState label={t('historyLoadingCorrections')} />}
    {state.status === 'error' && <ErrorState description={t(noteErrorMessage(state.error))} onRetry={reload} />}
    {state.status === 'ready' && <>
      {state.data.corrections.events.length > 0 && <CorrectionHistory history={state.data.corrections} idPrefix={`history-${noteId}`} />}
      {state.data.addenda.length === 0 && state.data.corrections.events.length === 0 ? <p role="status">{t('historyNoCorrections')}</p> : null}
      {state.data.addenda.length > 0 && <section aria-labelledby={`history-text-addenda-${noteId}`}>
        <h3 id={`history-text-addenda-${noteId}`}>{t('notesAddenda')}</h3><ol className="history-corrections-list" role="list">{state.data.addenda.map((addendum, index) => {
          const createdAt = new Intl.DateTimeFormat(language === 'ar' ? 'ar-EG' : 'en', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(addendum.createdAt));
          return <li key={addendum.id}>
            <NoteTimelineRow
              entryLabel={<>{t('historyCorrection')} <bdi>{new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(index + 1)}</bdi></>}
              dateLabel={createdAt}
              dateTime={addendum.createdAt}
              provenance={<><span>{t('notesTimelineSourceId')}:</span> <bdi>{addendum.id}</bdi></>}
              body={<ObservationValues values={addendum.observations} providedFields={addendum.providedFields}
                verificationProvided={addendum.medicationVerificationProvided} correction />}
            />
          </li>;
        })}</ol></section>}
    </>}
  </>;
}

export function HistoryNoteCard({ note, recipient }: { note: DailyNote; recipient?: Recipient }) {
  const { t, language } = useI18n();
  const [open, setOpen] = useState(false);
  const correctionCount = note.addendumCount + (note.correctionCount ?? 0);
  const formattedCorrectionCount = new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(correctionCount);
  const date = new Intl.DateTimeFormat(language === 'ar' ? 'ar-EG' : 'en', { dateStyle: 'medium' }).format(new Date(`${note.date}T12:00:00`));
  return <article className="history-note" aria-label={recipient?.name ?? note.recipientId}>
    <NoteTimelineRow
      entryLabel={t('historyOriginal')}
      dateLabel={date}
      dateTime={note.date}
      metadata={<><bdi>{recipient?.name ?? note.recipientId}</bdi>{recipient?.active === false && <> · {t('historyInactive')}</>}</>}
      provenance={<><span>{t('notesTimelineSourceId')}:</span> <bdi>{note.id}</bdi></>}
      actions={<ButtonLink variant="text" to={`/app/notes/${note.id}`}>{t('historyOpenNote')}</ButtonLink>}
      body={<details className="history-original">
        <summary>{t('historyObservations')}</summary>
        <ObservationValues values={note.observations} />
      </details>}
    />
    {correctionCount > 0 ? <div className="history-addenda">
      <Button variant="secondary" id={`history-toggle-${note.id}`} aria-expanded={open} aria-controls={`history-addenda-${note.id}`}
        aria-label={`${t(open ? 'historyHideCorrections' : 'historyShowCorrections')} (${formattedCorrectionCount})`} onClick={() => setOpen(!open)}>
        {t(open ? 'historyHideCorrections' : 'historyShowCorrections')} (<bdi>{formattedCorrectionCount}</bdi>)
      </Button>
      <section id={`history-addenda-${note.id}`} aria-labelledby={`history-toggle-${note.id}`} hidden={!open}>
        {open && <Corrections noteId={note.id} />}
      </section>
    </div> : <p className="note-hint history-no-corrections">{t('historyNoCorrections')}</p>}
  </article>;
}
