import { useCallback } from 'react';
import type { NotesHistoryPage, NotesHistoryQuery } from '../../api/notes';
import type { Recipient } from '../../api/recipients';
import { useNoteResource, useNotesApis } from '../notes/useNotes';

export type LatestNoteMetadata = {
  recipientId: string;
  noteId: string | null;
  /** The newest note's `createdAt`; the API does not expose a note-level `updatedAt`. Corrections do not affect this value. */
  lastUpdatedAt: string | null;
  state: 'recorded' | 'missing' | 'error';
};

export type LatestNoteMetadataState =
  | { status: 'loading' }
  | { status: 'error'; error: unknown }
  | { status: 'ready'; data: LatestNoteMetadata[] };

export const LATEST_NOTE_METADATA_CONCURRENCY = 4;
// The existing history API accepts YYYY-MM-DD boundaries. These valid ISO calendar bounds span its complete four-digit date domain.
const ALL_TIME_FROM = '0001-01-01';
const ALL_TIME_TO = '9999-12-31';
const LATEST_NOTE_PAGE_SIZE = 1;

type HistoryRequest = (query: NotesHistoryQuery, signal?: AbortSignal) => Promise<NotesHistoryPage>;

function errorMetadata(recipientId: string): LatestNoteMetadata {
  return { recipientId, noteId: null, lastUpdatedAt: null, state: 'error' };
}

/** Maps the first history item (API order: note date descending, then ID descending) to recipient metadata. */
export function mapLatestNoteMetadata(recipientId: string, page: NotesHistoryPage): LatestNoteMetadata {
  if (!page || !Array.isArray(page.content) || !Number.isSafeInteger(page.totalElements) || page.totalElements < 0) {
    return errorMetadata(recipientId);
  }
  if (page.totalElements === 0) {
    return page.content.length === 0 ? {
      recipientId, noteId: null, lastUpdatedAt: null, state: 'missing',
    } : errorMetadata(recipientId);
  }
  const note = page.content[0];
  if (!note || note.recipientId !== recipientId || typeof note.id !== 'string' || !note.id
    || typeof note.createdAt !== 'string' || !Number.isFinite(Date.parse(note.createdAt))) {
    return errorMetadata(recipientId);
  }
  return { recipientId, noteId: note.id, lastUpdatedAt: note.createdAt, state: 'recorded' };
}

/** Loads one recipient-filtered history page at a time with bounded concurrency. */
export async function loadLatestNoteMetadata(
  recipients: Recipient[],
  history: HistoryRequest,
  signal: AbortSignal,
): Promise<LatestNoteMetadata[]> {
  const results = new Array<LatestNoteMetadata>(recipients.length);
  let nextIndex = 0;
  const worker = async () => {
    while (true) {
      if (signal.aborted) throw signal.reason ?? new Error('Latest-note metadata request was aborted.');
      const index = nextIndex;
      nextIndex += 1;
      if (index >= recipients.length) return;
      const recipient = recipients[index];
      try {
        const page = await history({
          from: ALL_TIME_FROM,
          to: ALL_TIME_TO,
          recipientId: recipient.id,
          page: 0,
          size: LATEST_NOTE_PAGE_SIZE,
        }, signal);
        if (signal.aborted) throw signal.reason ?? new Error('Latest-note metadata request was aborted.');
        results[index] = mapLatestNoteMetadata(recipient.id, page);
      } catch (error) {
        if (signal.aborted) throw error;
        results[index] = errorMetadata(recipient.id);
      }
    }
  };

  await Promise.all(Array.from(
    { length: Math.min(LATEST_NOTE_METADATA_CONCURRENCY, recipients.length) },
    () => worker(),
  ));
  if (signal.aborted) throw signal.reason ?? new Error('Latest-note metadata request was aborted.');
  return results;
}

export function useLatestNoteMetadata(recipients: Recipient[]): {
  state: LatestNoteMetadataState;
  reload: () => void;
} {
  const api = useNotesApis();
  const load = useCallback((signal: AbortSignal) =>
    loadLatestNoteMetadata(recipients, api.notes.history, signal), [api, recipients]);
  return useNoteResource(load);
}
