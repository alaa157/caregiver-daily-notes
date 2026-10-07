import {ApiClient} from '../../lib/ApiClient';
import {Addendum, Note} from '../../types/api';

// Provisional paths — reconciled against backend OpenAPI in Part B (Task B9).
export function createNote(
  client: ApiClient,
  body: {recipientId: string; [key: string]: unknown},
): Promise<Note> {
  return client.post<Note>('/api/notes', body);
}

export function getNote(client: ApiClient, id: string): Promise<Note> {
  return client.get<Note>(`/api/notes/${id}`);
}

export function appendAddendum(client: ApiClient, noteId: string, text: string): Promise<Addendum> {
  return client.post<Addendum>(`/api/notes/${noteId}/addenda`, {text});
}
