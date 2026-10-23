import type { DailyNote, NotesHistoryPage } from '../api/notes';
import { noteFixture } from './noteFixtures';

export const historyNote = { ...noteFixture, addendumCount: 1 };
export function historyPage(content: DailyNote[] = [historyNote], number = 0, size = 20, totalElements = content.length): NotesHistoryPage {
  const totalPages = Math.ceil(totalElements / size);
  return { content, number, size, totalElements, totalPages, first: number === 0, last: number >= totalPages - 1 };
}
