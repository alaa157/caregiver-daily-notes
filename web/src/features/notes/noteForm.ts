import { isNoteDate, observationFields, type AddendumInput, type CreateNoteInput, type ObservationField } from '../../api/notes';
import { isRecipientId } from '../../api/recipients';
import type { MessageKey } from '../../i18n/translations';

export type NoteValues = Record<ObservationField | 'recipientId' | 'date', string>;
export type NoteErrors = Partial<Record<keyof NoteValues | 'form', MessageKey>>;
export type Corrections = Partial<Record<ObservationField, boolean>>;
export const fieldLabels: Record<ObservationField, MessageKey> = {
  mood: 'notesMood', appetite: 'notesAppetite', sleep: 'notesSleep', mobility: 'notesMobility',
  medicationStatus: 'notesMedicationStatus', medicationVerified: 'notesMedicationVerified',
  painScore: 'notesPainScore', fallReported: 'notesFallReported', freeText: 'notesFreeText',
};
export function emptyNoteValues(): NoteValues {
  const today = new Date();
  const date = [today.getFullYear(), String(today.getMonth() + 1).padStart(2, '0'), String(today.getDate()).padStart(2, '0')].join('-');
  return { recipientId: '', date, mood: '', appetite: '', sleep: '', mobility: '', medicationStatus: '', medicationVerified: '', painScore: '', fallReported: '', freeText: '' };
}
export function validateNoteValues(values: NoteValues, corrections?: Corrections): NoteErrors {
  const errors: NoteErrors = {};
  if (!corrections) {
    if (!isRecipientId(values.recipientId)) errors.recipientId = 'notesRequiredRecipient';
    if (!isNoteDate(values.date)) errors.date = 'notesRequiredDate';
  } else if (!observationFields.some((field) => corrections[field])) errors.form = 'notesSelectCorrection';
  for (const field of observationFields) {
    if (corrections && !corrections[field]) continue;
    const value = values[field];
    if (corrections && value === '') { errors[field] = 'notesChooseValue'; continue; }
    if (field === 'painScore') {
      if (value !== '' && (!/^\d+$/.test(value) || !Number.isInteger(Number(value)) || Number(value) > 10)) errors[field] = 'notesPainInvalid';
    } else if (field === 'medicationStatus') {
      if (value !== '' && !['TAKEN', 'MISSED', 'UNKNOWN'].includes(value)) errors[field] = 'notesChooseValue';
    } else if (field === 'medicationVerified' || field === 'fallReported') {
      if (value !== '' && !['true', 'false'].includes(value)) errors[field] = 'notesChooseValue';
    } else if (value.length > (field === 'freeText' ? 20000 : 40)) errors[field] = field === 'freeText' ? 'notesFreeTextLimit' : 'notesTextLimit';
  }
  return errors;
}
/** Values are not trimmed, normalized, translated or inferred. */
export function observationInput(values: NoteValues, corrections?: Corrections): AddendumInput {
  const input: AddendumInput = {};
  for (const field of observationFields) {
    if (corrections && !corrections[field]) continue;
    const raw = values[field];
    const value = raw === '' ? null : field === 'painScore' ? Number(raw)
      : field === 'medicationVerified' || field === 'fallReported' ? raw === 'true' : raw;
    Object.assign(input, { [field]: value });
  }
  return input;
}
export function createInput(values: NoteValues): CreateNoteInput {
  return { recipientId: values.recipientId, date: values.date, ...observationInput(values) } as CreateNoteInput;
}
