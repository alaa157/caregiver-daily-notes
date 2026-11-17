import { describe, expect, it } from 'vitest';
import { ApiError } from '../lib/api';
import { isInvalidIntakeTime } from '../api/medications';
import { messages } from '../i18n/translations';

describe('medication intake time', () => {
  it('localizes the intake time label and validation message in English and Arabic', () => {
    for (const key of ['medicationIntakeTime', 'medicationIntakeTimeInvalid'] as const) {
      expect(messages.en[key]).toBeTruthy();
      expect(messages.ar[key]).toBeTruthy();
      expect(messages.en[key]).not.toBe(messages.ar[key]);
    }
  });

  it('recognizes the server intake-time validation code', () => {
    expect(isInvalidIntakeTime(new ApiError(400, { code: 'INVALID_INTAKE_TIME' }))).toBe(true);
    expect(isInvalidIntakeTime(new ApiError(400, { code: 'VALIDATION_ERROR' }))).toBe(false);
    expect(isInvalidIntakeTime(new ApiError(500, { code: 'INVALID_INTAKE_TIME' }))).toBe(false);
    expect(isInvalidIntakeTime(new Error('INVALID_INTAKE_TIME'))).toBe(false);
  });
});
