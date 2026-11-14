import { describe, expect, it } from 'vitest';
import { noteMessages } from '../../i18n/noteTranslations';

const newKeys = [
  'notesChipMoodCalm',
  'notesChipMoodHappy',
  'notesChipMoodAnxious',
  'notesChipMoodAgitated',
  'notesChipMoodSad',
  'notesChipMoodConfused',
  'notesChipAppetiteGood',
  'notesChipAppetiteFair',
  'notesChipAppetitePoor',
  'notesChipAppetiteRefused',
  'notesChipSleepWell',
  'notesChipSleepRestless',
  'notesChipSleepPoor',
  'notesChipSleepDidNotSleep',
  'notesChipMobilityIndependent',
  'notesChipMobilityNeedsAssistance',
  'notesChipMobilityBedridden',
  'notesChipClear',
  'notesChipAddDetails',
  'notesChipChooseOne',
  'notesNothingRecorded',
] as const;

const reusedKeys = [
  'notesTaken',
  'notesMissed',
  'notesYes',
  'notesNo',
  'obsStateUnknown',
  'obsVerificationVerified',
  'obsVerificationUnverified',
] as const;

describe('note chip translations', () => {
  it('defines every new key in English', () => {
    for (const key of newKeys) {
      expect(noteMessages.en).toHaveProperty(key);
    }
  });

  it('defines every new key in Arabic', () => {
    for (const key of newKeys) {
      expect(noteMessages.ar).toHaveProperty(key);
    }
  });

  it('has full key parity between en and ar', () => {
    expect(Object.keys(noteMessages.en).sort()).toEqual(
      Object.keys(noteMessages.ar).sort(),
    );
  });

  it('has no empty or whitespace-only values for the new keys', () => {
    for (const key of newKeys) {
      expect(noteMessages.en[key].trim()).not.toBe('');
      expect(noteMessages.ar[key].trim()).not.toBe('');
    }
  });

  it('keeps the reused keys without duplicating them', () => {
    for (const key of reusedKeys) {
      expect(noteMessages.en).toHaveProperty(key);
      expect(noteMessages.ar).toHaveProperty(key);
    }
    const occurrences = (key: string) =>
      Object.keys(noteMessages.en).filter((k) => k === key).length
      + Object.keys(noteMessages.ar).filter((k) => k === key).length;
    for (const key of reusedKeys) {
      expect(occurrences(key)).toBe(2);
    }
  });
});
