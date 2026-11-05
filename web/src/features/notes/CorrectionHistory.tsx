import { observationFields, type CorrectionEvent, type CorrectionHistory as CorrectionHistoryData, type EffectiveObservation, type ObservationField } from '../../api/notes';
import { useI18n } from '../../i18n/I18nProvider';
import { fieldLabels } from './noteForm';
import { NoteTimelineRow } from './NoteTimelineRow';

function scalarText(field: ObservationField, value: string | number | boolean, t: ReturnType<typeof useI18n>['t'], language: string) {
  if (value === '') return t('notesEmptyValue');
  if (typeof value === 'boolean') return t(value ? 'notesYes' : 'notesNo');
  if (field === 'medicationStatus') return t(value === 'TAKEN' ? 'notesTaken' : 'notesMissed');
  if (typeof value === 'number') return new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(value);
  return value;
}
function valueText(field: ObservationField, observation: EffectiveObservation, t: ReturnType<typeof useI18n>['t'], language: string) {
  if (observation.lastOperation === 'CLEAR') return t('notesCorrectionCleared');
  if (observation.state === 'UNKNOWN') return t('notesUnknown');
  if (observation.state === 'NOT_RECORDED') return observation.lastOperation === 'NOT_RECORDED' ? t('notesCorrectionMarkedNotRecorded') : t('notesNotRecorded');
  if (observation.state === 'NOT_APPLICABLE') return t('obsStateNotApplicable');
  if (observation.state === 'LEGACY_AMBIGUOUS') return observation.rawValue === null
    ? t('obsStateLegacyAmbiguous') : `${t('obsStateLegacyAmbiguous')}: ${scalarText(field, observation.rawValue, t, language)}`;
  const value = observation.value;
  return value === null ? t('notesNotRecorded') : scalarText(field, value, t, language);
}

function operationText(event: CorrectionEvent, t: ReturnType<typeof useI18n>['t'], language: string) {
  if (event.operation === 'CLEAR') return t('notesCorrectionCleared');
  if (event.operation === 'UNKNOWN') return t('notesCorrectionMarkedUnknown');
  if (event.operation === 'SET') return `${t('notesCorrectionSetAs')} ${valueText(event.field, event.effectiveAfter, t, language)}`;
  return t('notesCorrectionMarkedNotRecorded');
}

export function CorrectionHistory({ history, idPrefix = 'note' }: { history: CorrectionHistoryData; idPrefix?: string }) {
  const { t, language } = useI18n();
  const time = (value: string) => new Intl.DateTimeFormat(language === 'ar' ? 'ar-EG' : 'en', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
  const historyTitleId = `structured-correction-history-title-${idPrefix}`;
  const effectiveTitleId = `effective-values-title-${idPrefix}`;
  const sourceEventById = new Map(history.events.map((event) => [event.id, event]));
  const renderEffectiveValue = (field: ObservationField, observation: EffectiveObservation) => {
    const sourceEvent = observation.eventId === null ? undefined : sourceEventById.get(observation.eventId);
    return <>
      <span dir="auto" className="note-verbatim">{valueText(field, observation, t, language)}</span>
      {sourceEvent && <a
        className="effective-observation-source"
        href={`#correction-event-${idPrefix}-${sourceEvent.id}`}
        aria-label={`${t('notesCorrectionSourceEvent')}: ${sourceEvent.id}`}
      >{t('notesCorrectionSourceEvent')}: <bdi>{sourceEvent.id}</bdi></a>}
    </>;
  };
  return <section className="structured-correction-history" aria-labelledby={historyTitleId}>
    <h3 id={historyTitleId}>{t('notesCorrectionHistory')}</h3>
    {history.events.length === 0 ? <p className="note-hint">{t('notesNoStructuredCorrections')}</p> : <>
      <section aria-labelledby={effectiveTitleId} className="effective-observations" role="region">
        <h3 id={effectiveTitleId}>{t('notesCorrectionUpdatedValue')}</h3>
        <dl className="note-values">{observationFields.map((field) => <div key={field} className={field === 'freeText' ? 'note-field--wide' : ''}>
          <dt>{t(fieldLabels[field])}</dt><dd>{renderEffectiveValue(field, history.effectiveObservations[field])}</dd>
        </div>)}</dl>
      </section>
      <ol className="structured-correction-list">{history.events.map((event) => <li key={event.id}>
        <NoteTimelineRow
          entryLabel={`${t('notesCorrectionEvent')} ${new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(event.sequenceNo)} · ${t(fieldLabels[event.field])}`}
          dateLabel={time(event.createdAt)}
          dateTime={event.createdAt}
          metadata={<>{t('notesCorrectionActor')}: <bdi>{event.actorId}</bdi></>}
          provenance={<>{t('notesTimelineSourceId')}: <bdi>{event.id}</bdi></>}
          collapsible={false}
          body={<dl className="correction-event-values structured-correction-event">
            <div><dt>{t('notesCorrectionPrevious')}</dt><dd dir="auto" className="note-verbatim">{valueText(event.field, event.previousEffective, t, language)}</dd></div>
            <div><dt>{t('notesCorrectionNew')}</dt><dd dir="auto" className="note-verbatim">{operationText(event, t, language)}</dd></div>
          </dl>}
          actions={<span id={`correction-event-${idPrefix}-${event.id}`} tabIndex={-1} className="correction-event-anchor" />}
        />
      </li>)}</ol>
    </>}
  </section>;
}
