import { describe, expect, it } from 'vitest';

import { emptyNoteValues, todayDate, validateNoteValues } from '../../features/notes/noteForm';
import { messages } from '../../i18n/translations';

/** YYYY-MM-DD offsets computed from local calendar parts, avoiding UTC drift. */
function dateOffset(days: number): string {
  const [year, month, day] = todayDate().split('-').map(Number);
  const date = new Date(year, month - 1, day + days);
  return [date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0')].join('-');
}

function valuesOn(date: string) {
  return { ...emptyNoteValues(), recipientId: '11111111-1111-4111-8111-111111111111', date };
}

describe('daily note date validation (issue #737)', () => {
  it('rejects tomorrow with a field error', () => {
    expect(validateNoteValues(valuesOn(dateOffset(1))).date).toBe('notesDateFuture');
  });

  it('rejects a far-future typo', () => {
    expect(validateNoteValues(valuesOn(dateOffset(400))).date).toBe('notesDateFuture');
  });

  it('accepts today', () => {
    expect(validateNoteValues(valuesOn(todayDate())).date).toBeUndefined();
  });

  it('accepts a backdated note', () => {
    expect(validateNoteValues(valuesOn(dateOffset(-7))).date).toBeUndefined();
  });

  it('keeps the format error for a malformed date', () => {
    expect(validateNoteValues(valuesOn('not-a-date')).date).toBe('notesRequiredDate');
  });

  it('does not apply the date rule to addenda, which carry no date', () => {
    expect(validateNoteValues(valuesOn(dateOffset(1)), { mood: true }).date).toBeUndefined();
  });

  it('localizes the future-date error in English and Arabic', () => {
    expect(messages.en.notesDateFuture).toBeTruthy();
    expect(messages.ar.notesDateFuture).toBeTruthy();
    expect(messages.en.notesDateFuture).not.toBe(messages.ar.notesDateFuture);
  });
});
