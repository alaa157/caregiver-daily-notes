import { useCallback } from 'react';
import { isNoteDate, type DailyNote } from '../../api/notes';
import { useNoteResource, useNotesApis } from '../notes/useNotes';

const PAGE_SIZE = 100;
const EMPTY_BY_RECIPIENT: Map<string, DailyNote> = new Map();

type TodayNotesData = {
  date: string;
  byRecipient: Map<string, DailyNote>;
};

export type TodayNotesState = 'loading' | 'ready' | 'error';
export type TodayNotesResult = {
  state: TodayNotesState;
  byRecipient: Map<string, DailyNote>;
  reload: () => void;
};

function localToday() {
  const now = new Date();
  return [now.getFullYear(), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0')].join('-');
}

export function useTodayNotes(date = localToday()): TodayNotesResult {
  if (!isNoteDate(date)) throw new Error('Today notes date must be a valid YYYY-MM-DD date.');

  const api = useNotesApis();
  const load = useCallback(async (signal: AbortSignal): Promise<TodayNotesData> => {
    const byRecipient = new Map<string, DailyNote>();
    const addPage = (content: DailyNote[]) => content.forEach((note) => byRecipient.set(note.recipientId, note));
    let page = await api.notes.history({ from: date, to: date, page: 0, size: PAGE_SIZE }, signal);
    addPage(page.content);
    for (let number = 1; number < page.totalPages; number += 1) {
      page = await api.notes.history({ from: date, to: date, page: number, size: PAGE_SIZE }, signal);
      addPage(page.content);
    }
    return { date, byRecipient };
  }, [api, date]);

  const { state: resource, reload } = useNoteResource(load);
  const ready = resource.status === 'ready' && resource.data.date === date ? resource.data : null;
  const state: TodayNotesState = resource.status === 'ready'
    ? ready ? 'ready' : 'loading'
    : resource.status;
  return { state, byRecipient: ready?.byRecipient ?? EMPTY_BY_RECIPIENT, reload };
}
