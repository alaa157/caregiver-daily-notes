/**
 * Observation-state and verification types for the approved PO-05 / WP-02 API.
 *
 * Canonical states (required on every observation):
 *   RECORDED | UNKNOWN | NOT_RECORDED | NOT_APPLICABLE | LEGACY_AMBIGUOUS
 *
 * Typed value is present only when state === 'RECORDED'.
 * Verification is independent: VERIFIED | UNVERIFIED | NOT_ASSERTED.
 */

export const OBSERVATION_STATES = [
  'RECORDED',
  'UNKNOWN',
  'NOT_RECORDED',
  'NOT_APPLICABLE',
  'LEGACY_AMBIGUOUS',
] as const;

export type ObservationState = (typeof OBSERVATION_STATES)[number];

export const VERIFICATION_VALUES = [
  'VERIFIED',
  'UNVERIFIED',
  'NOT_ASSERTED',
] as const;

export type VerificationValue = (typeof VERIFICATION_VALUES)[number];

/** User-selectable states when creating or correcting a note (no LEGACY_AMBIGUOUS). */
export const SELECTABLE_OBSERVATION_STATES = [
  'RECORDED',
  'UNKNOWN',
  'NOT_RECORDED',
  'NOT_APPLICABLE',
] as const;

export type SelectableObservationState = (typeof SELECTABLE_OBSERVATION_STATES)[number];

export type MedicationStatusValue = 'TAKEN' | 'MISSED' | 'UNKNOWN';

/** Wrapper for a single typed observation. */
export interface Observation<T> {
  state: ObservationState;
  /** Present only when state === 'RECORDED'. false, 0, and empty text are valid. */
  value?: T | null;
  /** Optional raw legacy source when state === 'LEGACY_AMBIGUOUS'. */
  rawValue?: unknown;
  /** Independent verification where the field supports it (e.g. medication). */
  verification?: VerificationValue | null;
}

export type StringObservation = Observation<string>;
export type BooleanObservation = Observation<boolean>;
export type NumberObservation = Observation<number>;
export type MedicationStatusObservation = Observation<MedicationStatusValue>;

export const structuredObservationFields = [
  'mood',
  'appetite',
  'sleep',
  'mobility',
  'medicationStatus',
  'painScore',
  'fallReported',
  'freeText',
] as const;

export type StructuredObservationField = (typeof structuredObservationFields)[number];
export type ObservationValues = {
  mood: string;
  appetite: string;
  sleep: string;
  mobility: string;
  medicationStatus: MedicationStatusValue;
  painScore: number;
  fallReported: boolean;
  freeText: string;
};
export type NoteObservations = {
  [K in keyof ObservationValues]: Observation<ObservationValues[K]>;
} & { medicationVerification: VerificationValue };
export type ObservationSet = {
  [K in keyof ObservationValues]: Observation<ObservationValues[K]>;
};

const valueMatches = (field: StructuredObservationField, value: unknown): boolean => {
  switch (field) {
    case 'mood':
    case 'appetite':
    case 'sleep':
    case 'mobility':
      return typeof value === 'string' && value.length <= 40;
    case 'freeText':
      return typeof value === 'string' && value.length <= 20000;
    case 'medicationStatus':
      return value === 'TAKEN' || value === 'MISSED';
    case 'painScore':
      return typeof value === 'number' && Number.isInteger(value) && value >= 0 && value <= 10;
    case 'fallReported':
      return typeof value === 'boolean';
  }
};

export function isObservation<T>(
  value: unknown,
  field: StructuredObservationField,
): value is Observation<T> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const item = value as Record<string, unknown>;
  if (!isObservationState(item.state)) return false;
  if (item.state === 'RECORDED') {
    return valueMatches(field, item.value)
      && (item.rawValue === undefined || item.rawValue === null);
  }
  if (item.value !== undefined && item.value !== null) return false;
  if (item.state !== 'LEGACY_AMBIGUOUS') {
    return item.rawValue === undefined || item.rawValue === null;
  }
  return item.rawValue === undefined || item.rawValue === null || valueMatches(field, item.rawValue);
}

export function isObservationState(value: unknown): value is ObservationState {
  return typeof value === 'string' && (OBSERVATION_STATES as readonly string[]).includes(value);
}

export function isSelectableObservationState(value: unknown): value is SelectableObservationState {
  return typeof value === 'string' && (SELECTABLE_OBSERVATION_STATES as readonly string[]).includes(value);
}

export function isVerificationValue(value: unknown): value is VerificationValue {
  return typeof value === 'string' && (VERIFICATION_VALUES as readonly string[]).includes(value);
}

/** Default empty observation for a new form field. */
export function emptyObservation<T>(): Observation<T> {
  return { state: 'NOT_RECORDED' };
}

/** True when a recorded value is present (including false and 0). */
export function hasRecordedValue(obs: Observation<unknown>): boolean {
  return obs.state === 'RECORDED' && obs.value !== undefined && obs.value !== null;
}

/** Conservative display-only fallback for responses from an older server. */
export function fromLegacyValue<T>(value: T | null | undefined): Observation<T> {
  if (value === null || value === undefined) {
    return { state: 'LEGACY_AMBIGUOUS', rawValue: null };
  }
  return { state: 'RECORDED', value };
}
