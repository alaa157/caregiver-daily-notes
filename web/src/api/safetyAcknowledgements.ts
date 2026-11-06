import { ApiError, type ApiClient } from '../lib/api';
import { isNoteDate } from './notes';
import { isRecipientId } from './recipients';
import { summaryPeriods, type SummaryRequest } from './summary';

export interface SafetyAcknowledgementPeriod {
  periodEndInclusive: string;
  periodDays: SummaryRequest['periodDays'];
}

export interface SafetyAcknowledgement extends SafetyAcknowledgementPeriod {
  id: string;
  recipientId: string;
  ruleId: string;
  actorCaregiverId?: string;
  acknowledgedAt: string;
}

export interface CreateSafetyAcknowledgementInput extends SafetyAcknowledgementPeriod {
  ruleId: string;
}

function isPeriodDays(value: unknown): value is SummaryRequest['periodDays'] {
  return typeof value === 'number' && summaryPeriods.some((period) => period === value);
}

function validPeriod(period: SafetyAcknowledgementPeriod): boolean {
  return isNoteDate(period.periodEndInclusive) && isPeriodDays(period.periodDays);
}

function object(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function readAcknowledgement(value: unknown, recipientId: string): SafetyAcknowledgement {
  if (!object(value) || typeof value.id !== 'string' || !isRecipientId(value.id)
    || value.recipientId !== recipientId
    || typeof value.ruleId !== 'string' || !value.ruleId.trim() || value.ruleId.length > 64
    || typeof value.periodEndInclusive !== 'string' || !isNoteDate(value.periodEndInclusive)
    || !isPeriodDays(value.periodDays)
    || typeof value.acknowledgedAt !== 'string' || !Number.isFinite(Date.parse(value.acknowledgedAt))
    || (value.actorCaregiverId !== undefined
      && (typeof value.actorCaregiverId !== 'string' || !isRecipientId(value.actorCaregiverId)))) {
    throw new Error('Invalid safety acknowledgement response.');
  }
  return {
    id: value.id,
    recipientId,
    ruleId: value.ruleId,
    periodEndInclusive: value.periodEndInclusive,
    periodDays: value.periodDays,
    ...(typeof value.actorCaregiverId === 'string' ? { actorCaregiverId: value.actorCaregiverId } : {}),
    acknowledgedAt: value.acknowledgedAt,
  };
}

export function createSafetyAcknowledgementsApi(client: ApiClient) {
  const root = (recipientId: string): `/api/recipients/${string}/safety-acknowledgements` => {
    if (!isRecipientId(recipientId)) throw new ApiError(404, null);
    return `/api/recipients/${recipientId}/safety-acknowledgements`;
  };
  return {
    async list(recipientId: string, period: SafetyAcknowledgementPeriod, signal?: AbortSignal): Promise<SafetyAcknowledgement[]> {
      const endpoint = root(recipientId);
      if (!validPeriod(period)) throw new ApiError(400, null);
      const query = new URLSearchParams({
        periodEndInclusive: period.periodEndInclusive,
        periodDays: String(period.periodDays),
      });
      const path: `/${string}` = `${endpoint}?${query}`;
      const result = await client.request<unknown>({ path, signal });
      if (!Array.isArray(result)) throw new Error('Invalid safety acknowledgement response.');
      const records = result.map((item) => readAcknowledgement(item, recipientId));
      if (records.some((record) => record.periodEndInclusive !== period.periodEndInclusive || record.periodDays !== period.periodDays)
        || new Set(records.map((record) => record.id)).size !== records.length
        || new Set(records.map((record) => record.ruleId)).size !== records.length) {
        throw new Error('Invalid safety acknowledgement response.');
      }
      return records;
    },
    async create(recipientId: string, input: CreateSafetyAcknowledgementInput, signal?: AbortSignal): Promise<SafetyAcknowledgement> {
      const endpoint = root(recipientId);
      if (!input || typeof input.ruleId !== 'string' || !input.ruleId.trim() || input.ruleId.length > 64 || !validPeriod(input)) {
        throw new ApiError(400, null);
      }
      const result = readAcknowledgement(await client.request<unknown>({ path: endpoint, method: 'POST', signal,
        body: {
          ruleId: input.ruleId,
          periodEndInclusive: input.periodEndInclusive,
          periodDays: input.periodDays,
        } }), recipientId);
      if (result.ruleId !== input.ruleId || result.periodEndInclusive !== input.periodEndInclusive || result.periodDays !== input.periodDays) {
        throw new Error('Invalid safety acknowledgement response.');
      }
      return result;
    },
  };
}
