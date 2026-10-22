import { observationFields, type Observations } from '../../api/notes';
import { useI18n } from '../../i18n/I18nProvider';
import { fieldLabels } from './noteForm';

export function ObservationValues({ values, correction = false }: { values: Observations; correction?: boolean }) {
  const { t, language } = useI18n();
  function renderValue(field: keyof Observations) {
    const value = values[field];
    if (value === null) return t('notesNotRecorded');
    if (value === '') return t('notesEmptyValue');
    if (typeof value === 'boolean') return t(value ? 'notesYes' : 'notesNo');
    if (field === 'medicationStatus') return t(value === 'TAKEN' ? 'notesTaken' : value === 'MISSED' ? 'notesMissed' : 'notesUnknown');
    if (typeof value === 'number') return new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(value);
    return value;
  }
  return <dl className="note-values">{observationFields.filter((field) => !correction || values[field] !== null).map((field) => <div key={field} className={field === 'freeText' ? 'note-field--wide' : ''}>
    <dt>{t(fieldLabels[field])}</dt><dd dir="auto" className="note-verbatim">{renderValue(field)}</dd>
  </div>)}</dl>;
}

