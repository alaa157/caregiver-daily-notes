import { describe, expect, it } from 'vitest';
import { messages } from '../i18n/translations';

describe('plan dismiss wording', () => {
  it('uses a set-aside verb for dismiss in Arabic, never reject', () => {
    for (const key of [
      'plansDismiss', 'plansConfirmDismiss', 'plansDismissHint',
      'plansStatusDismissed', 'plansDismissed',
    ] as const) {
      expect(messages.ar[key], key).not.toContain('رفض');
    }
    expect(messages.ar.plansDismiss).toBe('استبعاد الاقتراح');
    expect(messages.ar.plansStatusDismissed).toBe('مُستبعد');
  });

  it('phrases result messages around the caregiver action in both languages', () => {
    for (const key of ['plansAccepted', 'plansDismissed'] as const) {
      expect(messages.en[key], key).not.toMatch(/the server/i);
      expect(messages.ar[key], key).not.toContain('الخادم');
    }
    expect(messages.en.plansDismissed).toMatch(/Suggestion dismissed/);
    expect(messages.ar.plansDismissed).toContain('تم استبعاد الاقتراح');
  });

  it('keeps confirmation hints caregiver-oriented', () => {
    for (const key of ['plansAcceptHint', 'plansDismissHint'] as const) {
      expect(messages.en[key], key).not.toMatch(/the server/i);
      expect(messages.ar[key], key).not.toContain('الخادم');
    }
  });
});
