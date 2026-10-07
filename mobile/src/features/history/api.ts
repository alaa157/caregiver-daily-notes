import {ApiClient} from '../../lib/ApiClient';
import {Note} from '../../types/api';

// Provisional paths — reconciled against backend OpenAPI in Part B (Task B9).
export function queryHistory(client: ApiClient, recipientId: string): Promise<Note[]> {
  return client.get<Note[]>(`/api/notes?recipientId=${encodeURIComponent(recipientId)}`);
}
