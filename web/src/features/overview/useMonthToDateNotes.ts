import { useCallback } from 'react';
import { isNoteDate } from '../../api/notes';
import { useNoteResource, useNotesApis } from '../notes/useNotes';

export type MonthToDateMetric = {
  total: number | null;
  state: 'loading' | 'ready' | 'error';
};

type ScopedMetric = {
  date: string;
  metric: Exclude<MonthToDateMetric, { state: 'loading' }>;
};

const PAGE_SIZE = 100;

export function useMonthToDateNotes(date: string): MonthToDateMetric {
  if (!isNoteDate(date)) throw new Error('Month-to-date date must be a valid YYYY-MM-DD date.');

  const api = useNotesApis();
  const load = useCallback(async (signal: AbortSignal): Promise<ScopedMetric> => {
    try {
      const from = `${date.slice(0, 7)}-01`;
      let page = await api.notes.history({ from, to: date, page: 0, size: PAGE_SIZE }, signal);
      let total = page.content.length;
      for (let number = 1; number < page.totalPages; number += 1) {
        page = await api.notes.history({ from, to: date, page: number, size: PAGE_SIZE }, signal);
        total += page.content.length;
      }
      return { date, metric: { total, state: 'ready' } };
    } catch (error) {
      if (signal.aborted) throw error;
      return { date, metric: { total: null, state: 'error' } };
    }
  }, [api, date]);

  const { state: resource } = useNoteResource(load);
  if (resource.status === 'error') return { total: null, state: 'error' };
  if (resource.status !== 'ready' || resource.data.date !== date) return { total: null, state: 'loading' };
  return resource.data.metric;
}
