import { describe, expect, it } from 'vitest';
import { dayCountLabel } from '../i18n/dayCount';

describe('dayCountLabel', () => {
  it('uses the correct Arabic plural for the supported periods', () => {
    expect(dayCountLabel(7, 'ar')).toBe('7 أيام');
    expect(dayCountLabel(14, 'ar')).toBe('14 يومًا');
    expect(dayCountLabel(30, 'ar')).toBe('30 يومًا');
  });

  it('uses English singular and plural', () => {
    expect(dayCountLabel(1, 'en')).toBe('1 day');
    expect(dayCountLabel(7, 'en')).toBe('7 days');
    expect(dayCountLabel(14, 'en')).toBe('14 days');
    expect(dayCountLabel(30, 'en')).toBe('30 days');
  });
});
