import { isNoteDate } from '../../api/notes';
import { isRecipientId } from '../../api/recipients';
import type { MessageKey } from '../../i18n/translations';

export interface HistoryFilters { recipientId: string; from: string; to: string }
export type HistoryErrors = Partial<Record<keyof HistoryFilters, MessageKey>>;
export const historyPageSizes = [20, 50, 100] as const;

const localDate = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
export function defaultHistoryFilters(now = new Date()): HistoryFilters {
  const from = new Date(now);
  from.setDate(from.getDate() - 29);
  return { recipientId: '', from: localDate(from), to: localDate(now) };
}

export function validateHistoryFilters(filters: HistoryFilters): HistoryErrors {
  const errors: HistoryErrors = {};
  if (filters.recipientId && !isRecipientId(filters.recipientId)) errors.recipientId = 'historyInvalidRecipient';
  if (!isNoteDate(filters.from)) errors.from = 'historyInvalidDate';
  if (!isNoteDate(filters.to)) errors.to = 'historyInvalidDate';
  if (!errors.from && !errors.to && filters.from > filters.to) errors.to = 'historyInvalidRange';
  return errors;
}

export function readHistorySearch(search: URLSearchParams, defaults: HistoryFilters) {
  const page = Number(search.get('page') ?? 0);
  const size = Number(search.get('size') ?? 20);
  return {
    filters: { recipientId: search.get('recipientId') ?? '', from: search.get('from') ?? defaults.from, to: search.get('to') ?? defaults.to },
    page: Number.isSafeInteger(page) && page >= 0 ? page : 0,
    size: historyPageSizes.some((value) => value === size) ? size : 20,
  };
}

export function historySearch(filters: HistoryFilters, page: number, size: number) {
  const search = new URLSearchParams({ from: filters.from, to: filters.to });
  if (filters.recipientId) search.set('recipientId', filters.recipientId);
  if (page) search.set('page', String(page));
  if (size !== 20) search.set('size', String(size));
  return search;
}
