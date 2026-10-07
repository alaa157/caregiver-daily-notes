import {ApiClient} from '../../lib/ApiClient';
import {Recipient} from '../../types/api';

// Provisional paths — reconciled against backend OpenAPI in Part B (Task B9).
export function listRecipients(client: ApiClient): Promise<Recipient[]> {
  return client.get<Recipient[]>('/api/recipients');
}

export function createRecipient(client: ApiClient, name: string): Promise<Recipient> {
  return client.post<Recipient>('/api/recipients', {name});
}
