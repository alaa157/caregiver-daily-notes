import {ApiClient} from '../../lib/ApiClient';
import {Plan} from '../../types/api';

// Provisional paths — reconciled against backend OpenAPI in Part B (Task B9).
export function listPlans(client: ApiClient): Promise<Plan[]> {
  return client.get<Plan[]>('/api/plans');
}

export function transitionPlan(
  client: ApiClient,
  planId: string,
  action: 'accept' | 'edit-accept' | 'dismiss' | 'archive',
): Promise<Plan> {
  return client.post<Plan>(`/api/plans/${planId}/${action}`, {});
}
