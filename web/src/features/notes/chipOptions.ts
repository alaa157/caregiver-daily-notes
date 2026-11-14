import type { MessageKey } from '../../i18n/translations';

export type ChipOption<T = string> = {
  key: string;
  labelKey: MessageKey;
  state: 'RECORDED' | 'UNKNOWN';
  value: T | null;
};

export type ChipField = 'mood' | 'appetite' | 'sleep' | 'mobility' | 'medicationStatus' | 'fallReported' | 'painScore';

export const chipFieldOptions: Record<ChipField, readonly ChipOption<any>[]> = {
  mood: [
    { key: 'CALM', labelKey: 'notesChipMoodCalm', state: 'RECORDED', value: 'CALM' },
    { key: 'HAPPY', labelKey: 'notesChipMoodHappy', state: 'RECORDED', value: 'HAPPY' },
    { key: 'ANXIOUS', labelKey: 'notesChipMoodAnxious', state: 'RECORDED', value: 'ANXIOUS' },
    { key: 'AGITATED', labelKey: 'notesChipMoodAgitated', state: 'RECORDED', value: 'AGITATED' },
    { key: 'SAD', labelKey: 'notesChipMoodSad', state: 'RECORDED', value: 'SAD' },
    { key: 'CONFUSED', labelKey: 'notesChipMoodConfused', state: 'RECORDED', value: 'CONFUSED' },
    { key: 'unknown', labelKey: 'obsStateUnknown', state: 'UNKNOWN', value: null },
  ],
  appetite: [
    { key: 'GOOD', labelKey: 'notesChipAppetiteGood', state: 'RECORDED', value: 'GOOD' },
    { key: 'FAIR', labelKey: 'notesChipAppetiteFair', state: 'RECORDED', value: 'FAIR' },
    { key: 'POOR', labelKey: 'notesChipAppetitePoor', state: 'RECORDED', value: 'POOR' },
    { key: 'REFUSED', labelKey: 'notesChipAppetiteRefused', state: 'RECORDED', value: 'REFUSED' },
    { key: 'unknown', labelKey: 'obsStateUnknown', state: 'UNKNOWN', value: null },
  ],
  sleep: [
    { key: 'SLEPT_WELL', labelKey: 'notesChipSleepWell', state: 'RECORDED', value: 'SLEPT_WELL' },
    { key: 'RESTLESS', labelKey: 'notesChipSleepRestless', state: 'RECORDED', value: 'RESTLESS' },
    { key: 'POOR', labelKey: 'notesChipSleepPoor', state: 'RECORDED', value: 'POOR' },
    { key: 'DID_NOT_SLEEP', labelKey: 'notesChipSleepDidNotSleep', state: 'RECORDED', value: 'DID_NOT_SLEEP' },
    { key: 'unknown', labelKey: 'obsStateUnknown', state: 'UNKNOWN', value: null },
  ],
  mobility: [
    { key: 'INDEPENDENT', labelKey: 'notesChipMobilityIndependent', state: 'RECORDED', value: 'INDEPENDENT' },
    { key: 'NEEDS_ASSISTANCE', labelKey: 'notesChipMobilityNeedsAssistance', state: 'RECORDED', value: 'NEEDS_ASSISTANCE' },
    { key: 'BEDRIDDEN', labelKey: 'notesChipMobilityBedridden', state: 'RECORDED', value: 'BEDRIDDEN' },
    { key: 'unknown', labelKey: 'obsStateUnknown', state: 'UNKNOWN', value: null },
  ],
  medicationStatus: [
    { key: 'TAKEN', labelKey: 'notesTaken', state: 'RECORDED', value: 'TAKEN' },
    { key: 'MISSED', labelKey: 'notesMissed', state: 'RECORDED', value: 'MISSED' },
    { key: 'unknown', labelKey: 'obsStateUnknown', state: 'UNKNOWN', value: null },
  ],
  fallReported: [
    { key: 'true', labelKey: 'notesYes', state: 'RECORDED', value: true },
    { key: 'false', labelKey: 'notesNo', state: 'RECORDED', value: false },
    { key: 'unknown', labelKey: 'obsStateUnknown', state: 'UNKNOWN', value: null },
  ],
  painScore: [
    ...Array.from({ length: 11 }, (_, n) => ({
      key: String(n),
      labelKey: 'notesPainScore' as MessageKey,
      state: 'RECORDED' as const,
      value: n,
    })),
    { key: 'unknown', labelKey: 'obsStateUnknown', state: 'UNKNOWN', value: null },
  ],
};

export function chipOptionsFor(field: ChipField): readonly ChipOption<any>[] {
  return chipFieldOptions[field];
}

export function unknownChipFor(field: ChipField): ChipOption<any> {
  const options = chipFieldOptions[field];
  const unknown = options[options.length - 1];

  if (unknown.state !== 'UNKNOWN' || unknown.value !== null) {
    throw new Error(`The last chip option for ${field} must be the unknown option.`);
  }

  return unknown;
}
