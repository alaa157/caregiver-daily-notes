import { describe, expect, it } from 'vitest';

import { noteMessages } from '../../i18n/noteTranslations';
import { chipFieldOptions, unknownChipFor } from '../../features/notes/chipOptions';

describe('chip option definitions', () => {
  it('has the expected fields and counts', () => {
    expect(Object.keys(chipFieldOptions)).toEqual([
      'mood',
      'appetite',
      'sleep',
      'mobility',
      'medicationStatus',
      'fallReported',
      'painScore',
    ]);

    expect(chipFieldOptions.mood).toHaveLength(7);
    expect(chipFieldOptions.appetite).toHaveLength(5);
    expect(chipFieldOptions.sleep).toHaveLength(5);
    expect(chipFieldOptions.mobility).toHaveLength(4);
    expect(chipFieldOptions.medicationStatus).toHaveLength(3);
    expect(chipFieldOptions.fallReported).toHaveLength(3);
    expect(chipFieldOptions.painScore).toHaveLength(12);
  });

  it('uses the required recorded values in the expected order', () => {
    expect(chipFieldOptions.mood.filter((option) => option.state === 'RECORDED').map((option) => option.key)).toEqual([
      'CALM',
      'HAPPY',
      'ANXIOUS',
      'AGITATED',
      'SAD',
      'CONFUSED',
    ]);
    expect(chipFieldOptions.appetite.filter((option) => option.state === 'RECORDED').map((option) => option.key)).toEqual([
      'GOOD',
      'FAIR',
      'POOR',
      'REFUSED',
    ]);
    expect(chipFieldOptions.sleep.filter((option) => option.state === 'RECORDED').map((option) => option.key)).toEqual([
      'SLEPT_WELL',
      'RESTLESS',
      'POOR',
      'DID_NOT_SLEEP',
    ]);
    expect(chipFieldOptions.mobility.filter((option) => option.state === 'RECORDED').map((option) => option.key)).toEqual([
      'INDEPENDENT',
      'NEEDS_ASSISTANCE',
      'BEDRIDDEN',
    ]);
    expect(chipFieldOptions.medicationStatus.filter((option) => option.state === 'RECORDED').map((option) => option.key)).toEqual([
      'TAKEN',
      'MISSED',
    ]);
    expect(chipFieldOptions.fallReported.filter((option) => option.state === 'RECORDED').map((option) => option.key)).toEqual(['true', 'false']);
    expect(chipFieldOptions.painScore.filter((option) => option.state === 'RECORDED').map((option) => Number(option.key))).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
  });

  it('stores booleans and numbers as typed values', () => {
    const boolValues = chipFieldOptions.fallReported.slice(0, 2).map((option) => option.value);
    expect(boolValues).toEqual([true, false]);
    expect(typeof boolValues[0]).toBe('boolean');
    expect(typeof boolValues[1]).toBe('boolean');

    const painValues = chipFieldOptions.painScore.slice(0, 11).map((option) => option.value);
    expect(painValues).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
    expect(typeof painValues[0]).toBe('number');
    expect(typeof painValues[10]).toBe('number');
  });

  it('marks the last entry in each field as the unknown chip', () => {
    for (const field of Object.keys(chipFieldOptions) as Array<keyof typeof chipFieldOptions>) {
      const last = chipFieldOptions[field][chipFieldOptions[field].length - 1];
      expect(last.state).toBe('UNKNOWN');
      expect(last.value).toBeNull();
      expect(last.key).toBe('unknown');
    }
  });

  it('uses known translation keys and unique keys within each field', () => {
    for (const field of Object.keys(chipFieldOptions) as Array<keyof typeof chipFieldOptions>) {
      const seen = new Set<string>();
      for (const option of chipFieldOptions[field]) {
        expect(noteMessages.en[option.labelKey as keyof typeof noteMessages.en]).toBeDefined();
        expect(noteMessages.ar[option.labelKey as keyof typeof noteMessages.ar]).toBeDefined();
        expect(seen.has(option.key)).toBe(false);
        seen.add(option.key);
      }
    }
  });

  it('returns the unknown chip helper for pain score', () => {
    expect(unknownChipFor('painScore')).toMatchObject({
      key: 'unknown',
      labelKey: 'obsStateUnknown',
      state: 'UNKNOWN',
      value: null,
    });
  });
});
