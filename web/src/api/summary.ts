import { ApiError, createApiClient, type ApiClient } from '../lib/api';
import { isNoteDate } from './notes';
import { isRecipientId } from './recipients';
import type { MessageKey } from '../i18n/translations';

/** Matches the Java SummaryRequest/Result records; the browser never calls an LLM. */
export const summaryPeriods = [7, 14, 30] as const;
export interface SummaryRequest { recipientId: string; periodEndInclusive: string; periodDays: 7 | 14 | 30 }
export interface SummaryObservation { text: string; noteId: string; quote: string }
export interface SummaryUncertainty { topic: string; detail: string }
export interface SummaryContent {
  observations: SummaryObservation[]; uncertainties: SummaryUncertainty[]; aiUnavailable: boolean;
}
export interface SafetySignal { ruleId: string | null; severity: string | null; reason: string | null }
export interface SummarySafety { signals: SafetySignal[]; needsDoctorBanner: boolean; incomplete: boolean }
export interface SummaryTrend { metric: string; previousCount: number; currentCount: number; delta: number; previousSampleSize: number; currentSampleSize: number }
export interface SummaryResponse { safety: SummarySafety; summary: SummaryContent | null; trends: SummaryTrend[]; invalidContent: boolean }

const object = (value: unknown): Record<string, unknown> | null => value !== null && typeof value === 'object' && !Array.isArray(value)
  ? value as Record<string, unknown> : null;
const nonempty = (value: unknown): value is string => typeof value === 'string' && value.trim().length > 0;

/** Parse safety independently: malformed model content must never erase a valid banner. */
export function readSummarySafety(value: unknown): SummarySafety {
  const raw = object(value);
  let incomplete = !raw || typeof raw.needsDoctorBanner !== 'boolean' || !Array.isArray(raw.signals);
  const signals = (Array.isArray(raw?.signals) ? raw.signals : []).map((value): SafetySignal => {
    const item = object(value);
    const ruleId = nonempty(item?.ruleId) ? item.ruleId : null;
    const severity = nonempty(item?.severity) ? item.severity : null;
    const reason = typeof item?.reason === 'string' ? item.reason : null;
    if (!ruleId || !severity || !['INFO', 'WATCH', 'HIGH'].includes(severity) || (item?.reason !== null && reason === null)) incomplete = true;
    return { ruleId, severity, reason };
  });
  return { signals, needsDoctorBanner: raw?.needsDoctorBanner === true, incomplete };
}

export function readSummaryResponse(value: unknown): SummaryResponse {
  const item = object(value);
  const safety = readSummarySafety(item?.safety);
  let summary: SummaryContent | null = null;
  let trends: SummaryTrend[] = [];
  let invalidContent = false;
  try {
    const raw = object(item?.summary);
    if (!raw || typeof raw.aiUnavailable !== 'boolean' || !Array.isArray(raw.observations) || !Array.isArray(raw.uncertainties)) throw new Error();
    const uncertainties = raw.uncertainties.map((value): SummaryUncertainty => {
      const uncertainty = object(value);
      if (!uncertainty || !nonempty(uncertainty.topic) || !nonempty(uncertainty.detail)) throw new Error();
      return { topic: uncertainty.topic, detail: uncertainty.detail };
    });
    // An unavailable response can contain stray provider output; never inspect or expose it as observations.
    const observations = raw.aiUnavailable ? [] : raw.observations.map((value): SummaryObservation => {
      const observation = object(value);
      if (!observation || !nonempty(observation.text) || !nonempty(observation.noteId) || !nonempty(observation.quote)) throw new Error();
      return { text: observation.text, noteId: observation.noteId, quote: observation.quote };
    });
    summary = { observations, uncertainties, aiUnavailable: raw.aiUnavailable };
  } catch { invalidContent = true; }
  try {
    if (!Array.isArray(item?.trends)) throw new Error();
    trends = item.trends.map((value): SummaryTrend => {
      const trend = object(value);
      if (!trend || !nonempty(trend.metric) || !Number.isSafeInteger(trend.previousCount) || (trend.previousCount as number) < 0
        || !Number.isSafeInteger(trend.currentCount) || (trend.currentCount as number) < 0 || !Number.isSafeInteger(trend.delta)
        || !Number.isSafeInteger(trend.previousSampleSize) || (trend.previousSampleSize as number) < 0
        || !Number.isSafeInteger(trend.currentSampleSize) || (trend.currentSampleSize as number) < 0
        || trend.delta !== (trend.currentCount as number) - (trend.previousCount as number)) throw new Error();
      return { metric: trend.metric, previousCount: trend.previousCount as number, currentCount: trend.currentCount as number, delta: trend.delta as number,
        previousSampleSize: trend.previousSampleSize as number, currentSampleSize: trend.currentSampleSize as number };
    });
  } catch { invalidContent = true; }
  return { safety, summary, trends, invalidContent };
}

/** An incomplete/error response can add warnings, but cannot clear previously returned warnings. */
export function retainSafety(previous: SummarySafety | undefined, next: SummarySafety, failed = false): SummarySafety {
  if (!previous || (!next.incomplete && !failed)) return next;
  const signals = [...previous.signals];
  for (const signal of next.signals) if (!signals.some((existing) => JSON.stringify(existing) === JSON.stringify(signal))) signals.push(signal);
  return { signals, needsDoctorBanner: previous.needsDoctorBanner || next.needsDoctorBanner, incomplete: next.incomplete };
}

export class SummaryEndpointUnavailable extends Error {
  constructor() { super('No unambiguous compatible summary operation is advertised by the backend.'); }
}

/**
 * Use only the compatible SummaryRequest/SummaryResult POST advertised by the
 * backend's OpenAPI document; never redirect authenticated generation externally.
 * Ignore servers/external refs/templated paths so discovery cannot redirect tokens.
 */
export function findSummaryEndpoint(value: unknown): `/api/${string}` {
  const document = object(value);
  const schemas = object(object(document?.components)?.schemas);
  const schemaNamed = (value: unknown, name: string) => {
    const ref = object(value)?.$ref;
    return ref === `#/components/schemas/${name}` && object(schemas?.[name]) !== null;
  };
  const matches: string[] = [];
  if (typeof document?.openapi !== 'string' || !document.openapi.startsWith('3.')) throw new SummaryEndpointUnavailable();
  for (const [path, value] of Object.entries(object(document.paths) ?? {})) {
    if (!/^\/api\/[a-zA-Z0-9_-]+(?:\/[a-zA-Z0-9_-]+)*$/.test(path)) continue;
    const operation = object(object(value)?.post);
    if (!operation || (Array.isArray(operation.parameters) && operation.parameters.some((parameter) => object(parameter)?.required === true))) continue;
    const body = object(object(object(operation.requestBody)?.content)?.['application/json']);
    const responses = object(operation.responses);
    const response = object(object(object(responses?.['200'])?.content)?.['application/json']);
    if (schemaNamed(body?.schema, 'SummaryRequest') && schemaNamed(response?.schema, 'SummaryResult')) matches.push(path);
  }
  const request = object(object(schemas?.SummaryRequest)?.properties);
  const result = object(object(schemas?.SummaryResult)?.properties);
  if (matches.length !== 1 || !request || !result
    || !['recipientId', 'periodEndInclusive', 'periodDays'].every((key) => Object.hasOwn(request, key))
    || !['summary', 'safety', 'trends'].every((key) => Object.hasOwn(result, key))) throw new SummaryEndpointUnavailable();
  return matches[0] as `/api/${string}`;
}

export function createSummaryApi(client: ApiClient, fetcher: typeof fetch = fetch) {
  // OpenAPI is public in SecurityConfig and sits beside /api, not beneath it.
  const documentation = createApiClient({ apiBaseUrl: client.baseUrl.replace(/\/api$/, '') }, fetcher);
  return {
    async generate(input: SummaryRequest, signal?: AbortSignal): Promise<SummaryResponse> {
      if (!isRecipientId(input.recipientId) || !isNoteDate(input.periodEndInclusive) || !summaryPeriods.includes(input.periodDays)) throw new ApiError(400, null);
      const document = await documentation.request<unknown>({ path: '/v3/api-docs', signal });
      if (signal?.aborted) throw new DOMException('Cancelled.', 'AbortError');
      const path = findSummaryEndpoint(document);
      const response = await client.request<unknown>({ path, method: 'POST', signal, body: {
        recipientId: input.recipientId, periodEndInclusive: input.periodEndInclusive, periodDays: input.periodDays,
      } });
      return readSummaryResponse(response);
    },
  };
}

export function summaryErrorMessage(error: unknown): MessageKey {
  if (error instanceof SummaryEndpointUnavailable) return 'summaryEndpointUnavailable';
  if (error instanceof ApiError) {
    if ([403, 404].includes(error.status)) return 'summaryUnavailable';
    if ([408, 504].includes(error.status)) return 'summaryTimeout';
    if ([400, 422].includes(error.status)) return 'summaryValidation';
    if (error.status === 429) return 'summaryRateLimited';
  }
  return error instanceof TypeError ? 'summaryNetworkError' : 'summaryServiceError';
}
