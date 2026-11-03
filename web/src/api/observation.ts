/**
 * Observation-state and verification types for the approved PO-05 / WP-02 contract.
 * Backend Issue #66 must implement the matching DTO shape before end-to-end
 * create/update against the live API will succeed.
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
  /** Present only when state === 'RECORDED'. false and 0 are valid. */
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

/**
 * Map a legacy flat nullable value into a structured observation.
 * Historical null becomes LEGACY_AMBIGUOUS so we never invent meaning.
 */
export function fromLegacyValue<T>(value: T | null | undefined): Observation<T> {
  if (value === null || value === undefined) {
    return { state: 'LEGACY_AMBIGUOUS', rawValue: null };
  }
  return { state: 'RECORDED', value };
}

/**
 * Map structured observation to a flat nullable value for the current
 * backend (Issue #66 not yet landed). Used only as a temporary bridge.
 * When backend is ready, send the structured shape instead.
 */
export function toLegacyValue<T>(obs: Observation<T>): T | null {
  if (obs.state === 'RECORDED' && obs.value !== undefined && obs.value !== null) {
    return obs.value;
  }
  if (obs.state === 'UNKNOWN' && typeof obs.value === 'string') {
    return obs.value as T;
  }
  return null;
}
