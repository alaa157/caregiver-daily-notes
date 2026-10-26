import { ApiError, type ApiClient } from '../lib/api';
import { isRecipientId } from './recipients';
import type { MessageKey } from '../i18n/translations';

/** Exact PlanResponse wire fields. Content is opaque, immutable backend text. */
export interface CarePlan {
  id: string;
  recipientId: string;
  version: number;
  status: string;
  content: string;
  sourceSummaryId: string | null;
  sourcePlanId: string | null;
  evidenceNoteIds: string | null;
  createdAt: string;
}
export type PlanAction = 'accept' | 'dismiss';

export function readPlan(value: unknown, recipientId: string): CarePlan {
  if (!value || typeof value !== 'object') throw new Error('Invalid plan response.');
  const item = value as Record<string, unknown>;
  if (typeof item.id !== 'string' || !isRecipientId(item.id)
    || item.recipientId !== recipientId
    || !Number.isSafeInteger(item.version) || (item.version as number) < 1
    || typeof item.status !== 'string' || !item.status.trim()
    || typeof item.content !== 'string'
    || (item.sourceSummaryId !== null && typeof item.sourceSummaryId !== 'string')
    || (item.sourcePlanId !== null && (typeof item.sourcePlanId !== 'string' || !isRecipientId(item.sourcePlanId)))
    || (item.evidenceNoteIds !== null && typeof item.evidenceNoteIds !== 'string')
    || typeof item.createdAt !== 'string' || !Number.isFinite(Date.parse(item.createdAt))) {
    throw new Error('Invalid plan response.');
  }
  return {
    id: item.id, recipientId, version: item.version as number, status: item.status,
    content: item.content, sourceSummaryId: item.sourceSummaryId as string | null,
    sourcePlanId: item.sourcePlanId as string | null, evidenceNoteIds: item.evidenceNoteIds as string | null,
    createdAt: item.createdAt,
  };
}

export function planErrorCode(error: unknown): string | undefined {
  if (!(error instanceof ApiError) || !error.details || typeof error.details !== 'object') return;
  const { code } = error.details as Record<string, unknown>;
  return typeof code === 'string' ? code : undefined;
}

export function planErrorMessage(error: unknown): MessageKey {
  if (error instanceof ApiError) {
    if (error.status === 409) return planErrorCode(error) === 'INVALID_PLAN_TRANSITION' ? 'plansInvalidTransition' : 'plansConflict';
    if (error.status === 403 || error.status === 404) return 'plansUnavailable';
    if (error.status === 400 || error.status === 422) return 'plansRejected';
    if (error.status === 429) return 'plansRateLimited';
  }
  return 'plansServiceError';
}

export function createPlansApi(client: ApiClient) {
  const root = (recipientId: string): `/api/recipients/${string}/plans` => {
    if (!isRecipientId(recipientId)) throw new ApiError(404, null);
    return `/api/recipients/${recipientId}/plans`;
  };
  return {
    async active(recipientId: string, signal?: AbortSignal): Promise<CarePlan | null> {
      try {
        const result = readPlan(await client.request<unknown>({ path: `${root(recipientId)}/active`, signal }), recipientId);
        if (result.status !== 'ACCEPTED' && result.status !== 'EDITED_AND_ACCEPTED') throw new Error('Invalid active plan response.');
        return result;
      } catch (error) {
        // A missing route/recipient is NOT an empty active-plan result.
        if (error instanceof ApiError && error.status === 404 && planErrorCode(error) === 'NOT_FOUND') return null;
        throw error;
      }
    },
    async history(recipientId: string, signal?: AbortSignal): Promise<CarePlan[]> {
      const result = await client.request<unknown>({ path: `${root(recipientId)}/history`, signal });
      if (!Array.isArray(result)) throw new Error('Invalid plan history response.');
      const plans = result.map((item) => readPlan(item, recipientId));
      if (new Set(plans.map((item) => item.id)).size !== plans.length
        || new Set(plans.map((item) => item.version)).size !== plans.length) throw new Error('Invalid plan history response.');
      return plans;
    },
    async transition(recipientId: string, planId: string, action: PlanAction, signal?: AbortSignal): Promise<void> {
      if (!isRecipientId(planId) || (action !== 'accept' && action !== 'dismiss')) throw new Error('Invalid plan action.');
      // No client-owned status, content, medication, owner or version fields.
      await client.request<unknown>({ path: `${root(recipientId)}/${planId}/${action}`, method: 'POST', signal });
      // Always GET the committed snapshot; never invent state from a POST response.
    },
  };
}
