import { describe, expect, it } from 'vitest';

import { chipFieldOptions } from '../../features/notes/chipOptions';
import { chipKeyFor, observationForChip } from '../../features/notes/chipState';

describe('chip state helpers', () => {
  const moodOptions = chipFieldOptions.mood;
  const appetiteOptions = chipFieldOptions.appetite;
  const painOptions = chipFieldOptions.painScore;
  const fallOptions = chipFieldOptions.fallReported;
  const medicationOptions = chipFieldOptions.medicationStatus;

  it('returns null for unrecorded or unsupported states', () => {
    expect(chipKeyFor({ state: 'NOT_RECORDED', value: null }, painOptions)).toBeNull();
    expect(chipKeyFor({ state: 'UNKNOWN', value: null }, moodOptions)).toBe('unknown');
    expect(chipKeyFor({ state: 'RECORDED', value: 'CALM' }, moodOptions)).toBe('CALM');
    expect(chipKeyFor({ state: 'RECORDED', value: 0 }, painOptions)).toBe('0');
    expect(chipKeyFor({ state: 'RECORDED', value: false }, fallOptions)).toBe('false');
    expect(chipKeyFor({ state: 'RECORDED', value: 'good' }, appetiteOptions)).toBeNull();
    expect(chipKeyFor({ state: 'LEGACY_AMBIGUOUS', value: 'x' }, moodOptions)).toBeNull();
  });

  it('builds observations from null or known chip keys', () => {
    expect(observationForChip(null, moodOptions)).toEqual({ state: 'NOT_RECORDED', value: null });
    expect(observationForChip('unknown', moodOptions)).toEqual({ state: 'UNKNOWN', value: null });
    expect(observationForChip('CALM', moodOptions)).toEqual({ state: 'RECORDED', value: 'CALM' });
  });

  it('preserves zero, false, and recorded string values', () => {
    const zeroObs = observationForChip('0', painOptions);
    expect(zeroObs.state).toBe('RECORDED');
    expect(zeroObs.value).toBe(0);

    const falseObs = observationForChip('false', fallOptions);
    expect(falseObs.state).toBe('RECORDED');
    expect(falseObs.value).toBe(false);

    const missedObs = observationForChip('MISSED', medicationOptions);
    expect(missedObs.state).toBe('RECORDED');
    expect(missedObs.value).toBe('MISSED');
  });

  it('round-trips all chip options in both directions', () => {
    for (const options of Object.values(chipFieldOptions)) {
      for (const option of options) {
        const observation = observationForChip(option.key, options);
        const key = chipKeyFor(observation, options);
        expect(key).toBe(option.key);
        expect(observationForChip(key, options)).toEqual(observation);
      }
    }
  });
});
