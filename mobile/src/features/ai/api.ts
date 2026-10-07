import {ApiClient} from '../../lib/ApiClient';
import {Summary} from '../../types/api';

// Provisional paths — reconciled against backend OpenAPI in Part B (Task B9).
export function requestSummary(
  client: ApiClient,
  recipientId: string,
  periodDays: number,
): Promise<Summary> {
  return client.post<Summary>('/api/summaries', {recipientId, periodDays});
}
