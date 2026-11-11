import type { OverviewState } from './useDashboardOverview';

export type SummaryRow = Readonly<{
  recipientId: string;
  noteId: string | null;
  observedAt: string | null;
  status: 'recorded' | 'missing' | 'error';
}>;

export type SummaryState =
  | { status: 'loading' }
  | { status: 'error'; error: unknown }
  | { status: 'ready'; data: readonly SummaryRow[] };

/**
 * Project the authenticated, date-scoped overview result onto the minimal
 * read-only summary contract. `DailyNote.createdAt` is note-entry time, not an
 * observation timestamp, so it is deliberately not presented as `observedAt`.
 */
export function toSummaryState(state: OverviewState, selectedDate: string): SummaryState {
  if (state.status !== 'ready') return state;

  return {
    status: 'ready',
    data: state.data.map(({ recipient, note, status }): SummaryRow => {
      const confirmedNote = status === 'recorded' && note
        && note.recipientId === recipient.id && note.date === selectedDate;
      if (status === 'recorded' && !confirmedNote) {
        return { recipientId: recipient.id, noteId: null, observedAt: null, status: 'error' };
      }
      if (confirmedNote) {
        return { recipientId: recipient.id, noteId: note.id, observedAt: null, status: 'recorded' };
      }
      return { recipientId: recipient.id, noteId: null, observedAt: null, status };
    }),
  };
}
