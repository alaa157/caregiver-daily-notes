import { ApiError, type ApiClient } from '../lib/api';
import { isRecipientId } from './recipients';
import type { MessageKey } from '../i18n/translations';
import { isNoteDate } from './notes';
import { readSummarySafety, summaryPeriods, type SummaryRequest, type SummarySafety } from './summary';

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
  sourceType?: string;
  structuredContent?: PlanDocument | null;
  editable?: boolean;
}
export type PlanAction = 'accept' | 'dismiss' | 'edit-and-accept';
export interface PlanChange { action: string; params: Record<string, string>; reason: string; evidenceNoteIds: string[] }
export interface MedicationEntry { name: string; dose: string; schedule: string }
export interface PlanDocument { schemaVersion: 1; changes: PlanChange[]; medications: MedicationEntry[] }
export interface CreateStructuredPlanInput { schemaVersion: 1; changes: PlanChange[]; medications: MedicationEntry[] }
export interface PlanEditRequest { expectedVersion: number; changes: { index: number; params: Record<string, string> }[] }
export interface PlanGeneration { plan: CarePlan | null; aiUnavailable: boolean; safety: SummarySafety; invalidPlan: boolean }
const catalog: Record<string, readonly string[]> = {
  SCHEDULE_CHECK_IN: ['withinDays'], UPDATE_MEAL_REMINDER: ['meal', 'time'], LOG_MOBILITY_GOAL: ['activity', 'frequency'],
  CAREGIVER_EDUCATION_TOPIC: ['topic'], ESCALATE_TO_DOCTOR: ['reason'],
};
const object = (value: unknown): value is Record<string, unknown> => !!value && typeof value === 'object' && !Array.isArray(value);
function readDocument(value: unknown): PlanDocument | null {
  if (value == null) return null;
  if (!object(value) || value.schemaVersion !== 1 || !Array.isArray(value.changes) || !Array.isArray(value.medications)) throw new Error('Invalid protected plan content.');
  const changes = value.changes.map((raw): PlanChange => {
    if (!object(raw) || typeof raw.action !== 'string' || !Object.hasOwn(catalog, raw.action) || !object(raw.params)
      || !Object.entries(raw.params).every(([key, value]) => catalog[raw.action as string].includes(key) && typeof value === 'string')
      || typeof raw.reason !== 'string' || !Array.isArray(raw.evidenceNoteIds) || !raw.evidenceNoteIds.every((id) => typeof id === 'string')) throw new Error('Invalid protected plan action.');
    return { action: raw.action, params: { ...raw.params } as Record<string, string>, reason: raw.reason, evidenceNoteIds: [...raw.evidenceNoteIds] };
  });
  const medications = value.medications.map((raw): MedicationEntry => {
    if (!object(raw) || !['name', 'dose', 'schedule'].every((key) => typeof raw[key] === 'string' && (raw[key] as string).trim())) throw new Error('Invalid protected medication record.');
    return { name: raw.name as string, dose: raw.dose as string, schedule: raw.schedule as string };
  });
  return { schemaVersion: 1, changes, medications };
}

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
  const metadata = item.sourceType !== undefined || item.structuredContent !== undefined || item.editable !== undefined ? {
    sourceType: typeof item.sourceType === 'string' ? item.sourceType : 'UNSPECIFIED',
    structuredContent: readDocument(item.structuredContent), editable: item.editable === true,
  } : {};
  if (metadata.editable && (!metadata.structuredContent || metadata.sourceType !== 'AI_GENERATED' || item.status !== 'SUGGESTED')) throw new Error('Invalid protected edit capability.');
  return {
    id: item.id, recipientId, version: item.version as number, status: item.status,
    content: item.content, sourceSummaryId: item.sourceSummaryId as string | null,
    sourcePlanId: item.sourcePlanId as string | null, evidenceNoteIds: item.evidenceNoteIds as string | null,
    createdAt: item.createdAt,
    ...metadata,
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
    async generate(input: SummaryRequest, signal?: AbortSignal): Promise<PlanGeneration> {
      if (!isRecipientId(input.recipientId) || !isNoteDate(input.periodEndInclusive) || !summaryPeriods.includes(input.periodDays)) throw new ApiError(400, null);
      const result = await client.request<unknown>({ path: '/api/plans/generate', method: 'POST', signal,
        body: { recipientId: input.recipientId, periodDays: input.periodDays, periodEndInclusive: input.periodEndInclusive } });
      const item = object(result) ? result : {};
      const safety = readSummarySafety(item.safety);
      let plan: CarePlan | null = null; let invalidPlan = typeof item.aiUnavailable !== 'boolean';
      try { if (item.aiUnavailable !== true) plan = readPlan(item.plan, input.recipientId); }
      catch { invalidPlan = true; }
      return { plan, aiUnavailable: item.aiUnavailable === true, safety, invalidPlan };
    },
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
    async createSuggested(recipientId: string, input: CreateStructuredPlanInput, signal?: AbortSignal): Promise<CarePlan> {
      const path: `/api/recipients/${string}/plans/suggestions` = `${root(recipientId)}/suggestions`;
      if (!input || input.schemaVersion !== 1 || !Array.isArray(input.changes)
        || !Array.isArray(input.medications) || input.medications.length !== 0) throw new ApiError(400, null);
      const result = readPlan(await client.request<unknown>({ path, method: 'POST', signal,
        body: { content: '', structuredContent: input } }), recipientId);
      if (result.status !== 'SUGGESTED' || !result.structuredContent) throw new Error('Invalid suggested plan response.');
      return result;
    },
    async transition(recipientId: string, planId: string, action: PlanAction, signal?: AbortSignal): Promise<void> {
      if (!isRecipientId(planId) || (action !== 'accept' && action !== 'dismiss')) throw new Error('Invalid plan action.');
      // No client-owned status, content, medication, owner or version fields.
      await client.request<unknown>({ path: `${root(recipientId)}/${planId}/${action}`, method: 'POST', signal });
      // Always GET the committed snapshot; never invent state from a POST response.
    },
    async editAndAccept(recipientId: string, plan: CarePlan, input: PlanEditRequest, signal?: AbortSignal): Promise<void> {
      if (!isRecipientId(plan.id) || !plan.editable || !plan.structuredContent || plan.recipientId !== recipientId || input.expectedVersion !== plan.version
        || input.changes.length !== plan.structuredContent.changes.length) throw new Error('Invalid protected edit request.');
      const changes = plan.structuredContent.changes.map((original, index) => {
        const change = input.changes.find((item) => item.index === index);
        if (!change || Object.keys(change.params).length !== Object.keys(original.params).length
          || !Object.keys(original.params).every((key) => typeof change.params[key] === 'string' && change.params[key].trim() && change.params[key].length <= 500)) throw new Error('Invalid protected edit parameters.');
        return { index, params: Object.fromEntries(Object.keys(original.params).map((key) => [key, change.params[key]])) };
      });
      await client.request<unknown>({ path: `${root(recipientId)}/${plan.id}/edit-and-accept`, method: 'POST', signal,
        body: { expectedVersion: plan.version, changes } });
    },
  };
}
