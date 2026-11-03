import { isNoteDate, observationFields, type AddendumInput, type CreateNoteInput, type ObservationField } from '../../api/notes';
import { isRecipientId } from '../../api/recipients';
import {
  emptyObservation,
  isSelectableObservationState,
  isVerificationValue,
  type MedicationStatusValue,
  type Observation,
  type ObservationSet,
  type ObservationValues,
  type SelectableObservationState,
  type VerificationValue,
} from '../../api/observation';
import type { MessageKey } from '../../i18n/translations';

export type NoteValues = {
  recipientId: string;
  date: string;
  observations: ObservationSet;
  medicationVerification: VerificationValue;
};
export type NoteErrors = Partial<Record<ObservationField | 'recipientId' | 'date' | 'form' | 'medicationVerification', MessageKey>>;
export type Corrections = Partial<Record<ObservationField | 'medicationVerification', boolean>>;
export const fieldLabels: Record<ObservationField, MessageKey> = {
  mood: 'notesMood', appetite: 'notesAppetite', sleep: 'notesSleep', mobility: 'notesMobility',
  medicationStatus: 'notesMedicationStatus', painScore: 'notesPainScore',
  fallReported: 'notesFallReported', freeText: 'notesFreeText',
};

function observationSet(): ObservationSet {
  return {
    mood: emptyObservation<string>(),
    appetite: emptyObservation<string>(),
    sleep: emptyObservation<string>(),
    mobility: emptyObservation<string>(),
    medicationStatus: emptyObservation<MedicationStatusValue>(),
    painScore: emptyObservation<number>(),
    fallReported: emptyObservation<boolean>(),
    freeText: emptyObservation<string>(),
  };
}

export function emptyNoteValues(): NoteValues {
  const today = new Date();
  const date = [today.getFullYear(), String(today.getMonth() + 1).padStart(2, '0'), String(today.getDate()).padStart(2, '0')].join('-');
  return { recipientId: '', date, observations: observationSet(), medicationVerification: 'NOT_ASSERTED' };
}

function validateObservation<K extends keyof ObservationValues>(
  field: K,
  observation: Observation<ObservationValues[K]>,
): MessageKey | undefined {
  if (!isSelectableObservationState(observation.state)) return 'obsChooseState';
  if (observation.state !== 'RECORDED') return undefined;
  const value = observation.value;
  if (value === undefined || value === null) return 'obsRecordedValueRequired';
  if (field === 'painScore') {
    return typeof value === 'number' && Number.isInteger(value) && value >= 0 && value <= 10
      ? undefined : 'notesPainInvalid';
  }
  if (field === 'fallReported') return typeof value === 'boolean' ? undefined : 'obsRecordedValueRequired';
  if (field === 'medicationStatus') {
    return value === 'TAKEN' || value === 'MISSED' ? undefined : 'notesChooseValue';
  }
  if (typeof value !== 'string') return 'obsRecordedValueRequired';
  const max = field === 'freeText' ? 20000 : 40;
  return value.length <= max ? undefined : field === 'freeText' ? 'notesFreeTextLimit' : 'notesTextLimit';
}

export function validateNoteValues(values: NoteValues, corrections?: Corrections): NoteErrors {
  const errors: NoteErrors = {};
  if (!corrections) {
    if (!isRecipientId(values.recipientId)) errors.recipientId = 'notesRequiredRecipient';
    if (!isNoteDate(values.date)) errors.date = 'notesRequiredDate';
  } else if (!observationFields.some((field) => corrections[field]) && !corrections.medicationVerification) {
    errors.form = 'notesSelectCorrection';
  }

  for (const field of observationFields) {
    if (corrections && !corrections[field]) continue;
    const error = validateObservation(field, values.observations[field]);
    if (error) errors[field] = error;
  }
  if ((!corrections || corrections.medicationVerification)
      && !isVerificationValue(values.medicationVerification)) {
    errors.medicationVerification = 'obsChooseState';
  }
  return errors;
}

/** Builds only the selected correction fields; state and value remain independent. */
export function observationInput(values: NoteValues, corrections: Corrections = {}): AddendumInput {
  const observations: Partial<ObservationSet> = {};
  for (const field of observationFields) {
    if (!corrections[field]) continue;
    Object.assign(observations, { [field]: values.observations[field] });
  }
  return {
    observations,
    ...(corrections.medicationVerification
      ? { medicationVerification: values.medicationVerification }
      : {}),
  };
}

export function createInput(values: NoteValues): CreateNoteInput {
  return {
    recipientId: values.recipientId,
    date: values.date,
    observations: values.observations,
    medicationVerification: values.medicationVerification,
  };
}

export function isSelectableState(value: string): value is SelectableObservationState {
  return isSelectableObservationState(value);
}
