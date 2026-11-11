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
  | { status: 'ready'; data: OverviewRow[] };

export function useDashboardOverview(date: string): {
  state: OverviewState;
  reload: () => void;
} {
  if (!isNoteDate(date)) throw new Error('Dashboard overview date must be a valid YYYY-MM-DD date.');

  const api = useNotesApis();
  const load = useCallback(async (signal: AbortSignal): Promise<OverviewRow[]> => {
    const recipients = await api.recipients.list(signal);
    if (!recipients.length) return [];

    const notes: DailyNote[] = [];
    const rows = (unmatchedStatus: 'missing' | 'error') => {
      const notesByRecipient = new Map<string, DailyNote>();
      notes.filter((note) => note.date === date).forEach((note) => notesByRecipient.set(note.recipientId, note));
      return recipients.map((recipient): OverviewRow => {
        const note = notesByRecipient.get(recipient.id) ?? null;
        return { recipient, note, status: note ? 'recorded' : unmatchedStatus };
      });
    };

    try {
      let page = await api.notes.history({ from: date, to: date, page: 0, size: 100 }, signal);
      notes.push(...page.content);
      for (let number = 1; number < page.totalPages; number += 1) {
        page = await api.notes.history({ from: date, to: date, page: number, size: 100 }, signal);
        notes.push(...page.content);
      }
      return rows('missing');
    } catch (error) {
      if (signal.aborted) throw error;
      return rows('error');
    }
  }, [api, date]);

  const { state, reload } = useNoteResource(load);
  return { state, reload };
}
