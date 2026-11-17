import { useCallback } from 'react';
import { isNoteDate, type DailyNote } from '../../api/notes';
import type { Recipient } from '../../api/recipients';
import { useNoteResource, useNotesApis } from '../notes/useNotes';

export type OverviewRow = {
  recipient: Recipient;
  note: DailyNote | null;
  status: 'recorded' | 'missing' | 'error';
};

export type OverviewState =
  | { status: 'loading' }
  | { status: 'error'; error: unknown }
  | { status: 'ready'; data: OverviewRow[] }
  | { status: 'partial'; data: OverviewRow[]; error: unknown };

type DateScopedOverview = {
  date: string;
  state: Exclude<OverviewState, { status: 'loading' }>;
};

/**
 * The backend enforces one original note per recipient and date
 * (`uk_daily_note_recipient_date`). Fail loudly if a response ever violates
 * that contract instead of letting the recipient-keyed table drop a row.
 */
export function assertUniqueNotesForDate(notes: readonly DailyNote[]): void {
  const seen = new Set<string>();
  for (const note of notes) {
    const key = `${note.recipientId}\u0000${note.date}`;
    if (seen.has(key)) {
      throw new Error(`Duplicate original note for recipient ${note.recipientId} on ${note.date}.`);
    }
    seen.add(key);
  }
}

export function useDashboardOverview(date: string): {
  state: OverviewState;
  reload: () => void;
} {
  if (!isNoteDate(date)) throw new Error('Dashboard overview date must be a valid YYYY-MM-DD date.');

  const api = useNotesApis();
  const load = useCallback(async (signal: AbortSignal): Promise<DateScopedOverview> => {
    let recipients;
    try {
      recipients = await api.recipients.list(signal);
    } catch (error) {
      if (signal.aborted) throw error;
      return { date, state: { status: 'error', error } };
    }

    if (!recipients.length) return { date, state: { status: 'ready', data: [] } };

    const notes: DailyNote[] = [];
    const rows = (unmatchedStatus: 'missing' | 'error') => {
      const notesByRecipient = new Map<string, DailyNote>();
      notes.filter((note) => note.date === date).forEach((note) => notesByRecipient.set(note.recipientId, note));
      return recipients.map((recipient): OverviewRow => {
        const note = notesByRecipient.get(recipient.id) ?? null;
        return { recipient, note, status: note ? 'recorded' : unmatchedStatus };
      });
    };

    let historyError: unknown = null;
    try {
      let page = await api.notes.history({ from: date, to: date, page: 0, size: 100 }, signal);
      notes.push(...page.content);
      for (let number = 1; number < page.totalPages; number += 1) {
        page = await api.notes.history({ from: date, to: date, page: number, size: 100 }, signal);
        notes.push(...page.content);
      }
    } catch (error) {
      if (signal.aborted) throw error;
      historyError = error;
    }

    // Runs on everything received, including partial pages, and throws out of
    // `load` so a contract violation reaches the error state rather than being
    // hidden by the recipient-keyed map in `rows`.
    assertUniqueNotesForDate(notes);
    return {
      date,
      state: historyError === null
        ? { status: 'ready', data: rows('missing') }
        : { status: 'partial', data: rows('error'), error: historyError },
    };
  }, [api, date]);

  const { state: resource, reload } = useNoteResource(load);
  const state = resource.status === 'ready'
    ? resource.data.date === date ? resource.data.state : { status: 'loading' as const }
    : resource;
  return { state, reload };
}
