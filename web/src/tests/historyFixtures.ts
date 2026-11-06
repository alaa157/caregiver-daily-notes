import type { NoteListItem, NotesHistoryPage } from '../api/notes';
import { observationFields } from '../api/notes';
import { noteFixture, noteListFixture } from './noteFixtures';

export const historyNote = { ...noteFixture, addendumCount: 1, correctionCount: 0 };
export function correctionHistoryFixture() {
  const effectiveObservations = Object.fromEntries(observationFields.map((field) => {
    const observation = noteFixture.observations[field];
    return [field, {
      state: observation.state,
      value: observation.state === 'RECORDED' ? observation.value : null,
      rawValue: observation.rawValue ?? null,
      lastOperation: null,
      eventId: null,
      actorId: null,
      updatedAt: noteFixture.createdAt,
    }];
  }));
  return { originalNote: noteListFixture, events: [], effectiveObservations };
}
export function historyPage(content: NoteListItem[] = [historyNote], number = 0, size = 20, totalElements = content.length): NotesHistoryPage {
  const totalPages = Math.ceil(totalElements / size);
  return { content, number, size, totalElements, totalPages, first: number === 0, last: number >= totalPages - 1 };
}
