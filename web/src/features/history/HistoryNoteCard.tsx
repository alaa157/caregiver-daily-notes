import { useCallback, useState } from 'react';
import type { DailyNote } from '../../api/notes';
import { noteErrorMessage } from '../../api/notes';
import type { Recipient } from '../../api/recipients';
import { Button, ButtonLink } from '../../components/Button';
import { ErrorState, LoadingState } from '../../components/Feedback';
import { useI18n } from '../../i18n/I18nProvider';
import { ObservationValues } from '../notes/ObservationValues';
import { useNoteResource, useNotesApis } from '../notes/useNotes';

function Corrections({ noteId }: { noteId: string }) {
  const { t, language } = useI18n();
  const api = useNotesApis();
  const load = useCallback((signal: AbortSignal) => api.notes.addenda(noteId, signal), [api, noteId]);
  const { state, reload } = useNoteResource(load);
  return <>
    <p className="note-hint">{t('historyCorrectionsHint')}</p>
    {state.status === 'loading' && <LoadingState label={t('historyLoadingCorrections')} />}
    {state.status === 'error' && <ErrorState description={t(noteErrorMessage(state.error))} onRetry={reload} />}
    {state.status === 'ready' && (state.data.length === 0 ? <p role="status">{t('historyNoCorrections')}</p>
      : <ol className="history-corrections-list" role="list">{state.data.map((addendum, index) => <li key={addendum.id}>
        <article className="history-correction" aria-labelledby={`history-correction-${addendum.id}`}>
          <h4 id={`history-correction-${addendum.id}`}>{t('historyCorrection')} {new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(index + 1)}</h4>
          <p className="note-hint"><time dateTime={addendum.createdAt}>{new Intl.DateTimeFormat(language === 'ar' ? 'ar-EG' : 'en', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(addendum.createdAt))}</time></p>
          <ObservationValues values={addendum} correction />
        </article>
      </li>)}</ol>)}
  </>;
}

export function HistoryNoteCard({ note, recipient }: { note: DailyNote; recipient?: Recipient }) {
  const { t, language } = useI18n();
  const [open, setOpen] = useState(false);
  const date = new Intl.DateTimeFormat(language === 'ar' ? 'ar-EG' : 'en', { dateStyle: 'medium' }).format(new Date(`${note.date}T12:00:00`));
  return <article className="note-surface history-note" aria-labelledby={`history-note-${note.id}`}>
    <header className="history-note-heading">
      <div><span className="history-original-label">{t('historyOriginal')}</span>
        <h3 id={`history-note-${note.id}`}><bdi>{recipient?.name ?? note.recipientId}</bdi></h3>
        <p><time dateTime={note.date}>{date}</time>{recipient?.active === false && <> · {t('historyInactive')}</>}</p>
      </div>
      <ButtonLink variant="text" to={`/app/notes/${note.id}`}>{t('historyOpenNote')}</ButtonLink>
    </header>
    <details className="history-original">
      <summary>{t('historyObservations')}</summary>
      <ObservationValues values={note} />
    </details>
    {note.addendumCount > 0 ? <div className="history-addenda">
      <Button variant="secondary" id={`history-toggle-${note.id}`} aria-expanded={open} aria-controls={`history-addenda-${note.id}`} onClick={() => setOpen(!open)}>
        {t(open ? 'historyHideCorrections' : 'historyShowCorrections')} ({new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(note.addendumCount)})
      </Button>
      <section id={`history-addenda-${note.id}`} aria-labelledby={`history-toggle-${note.id}`} hidden={!open}>
        {open && <Corrections noteId={note.id} />}
      </section>
    </div> : <p className="note-hint history-no-corrections">{t('historyNoCorrections')}</p>}
  </article>;
}
