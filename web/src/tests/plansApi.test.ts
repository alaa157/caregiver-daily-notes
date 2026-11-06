import { describe, expect, it, vi } from 'vitest';
import { createPlansApi, planErrorMessage, readPlan, type CreateStructuredPlanInput, type PlanAction } from '../api/plans';
import { ApiError, createApiClient, type ApiClient, type ApiRequest } from '../lib/api';
import { activePlanFixture as active, suggestedPlanFixture as suggestion } from './planFixtures';
import { protectedSuggestionFixture as protectedSuggestion } from './planFixtures';
import { recipientFixture } from './recipientFixtures';
import { jsonResponse } from './authFixtures';
import type { SummaryRequest } from '../api/summary';
import type { ByokCredentials } from '../features/ai/ByokContext';

const recipientId = recipientFixture.id;
const summaryRequest: SummaryRequest = { recipientId, periodEndInclusive: '2026-10-22', periodDays: 7 };
const byokCredentials: ByokCredentials = { providerId: 'GEMINI', model: 'gemini-3.8-flash', apiKey: 'synthetic-byok-key-123' };
const generationSafety = { signals: [{ ruleId: 'HIGH_PAIN', severity: 'HIGH', reason: 'Review the reported pain.' }], needsDoctorBanner: true };
const draftInput: CreateStructuredPlanInput = {
  schemaVersion: 1,
  changes: [{ action: 'SCHEDULE_CHECK_IN', params: { withinDays: '3' }, reason: 'Review the loaded note.', evidenceNoteIds: ['e5555555-5555-4555-8555-555555555555'] }],
  medications: [],
};
function setup(response: unknown, status = 200) {
  const fetcher = vi.fn().mockResolvedValue(jsonResponse(response, status));
  return { fetcher, api: createPlansApi(createApiClient({ apiBaseUrl: '' }, fetcher)) };
}
describe('plan contract and scoped transport', () => {
  it('reads authoritative AI provenance, protected catalog content and edit capability', () => {
    expect(readPlan(protectedSuggestion, recipientId)).toEqual(protectedSuggestion);
  });
  it.each([
    { structuredContent: { schemaVersion: 99, changes: [], medications: [] } },
    { structuredContent: { schemaVersion: 1, changes: [{ action: 'INVENTED_ACTION', params: {}, reason: 'x', evidenceNoteIds: [] }], medications: [] } },
    { structuredContent: { ...protectedSuggestion.structuredContent, medications: [{ name: 'x', dose: null, schedule: 'x' }] } },
    { sourceType: 'UNSPECIFIED' }, { status: 'ACCEPTED' },
  ])('refuses malformed protected content/capability: %j', (change) => {
    expect(() => readPlan({ ...protectedSuggestion, ...change }, recipientId)).toThrow();
  });
  it('serializes only existing action parameter values, excluding injected protected fields', async () => {
    const { api, fetcher } = setup({});
    const input = { expectedVersion: 2, changes: [{ index: 0, params: { withinDays: '10', medications: 'untrusted' } }], content: 'untrusted', sourceType: 'AI_GENERATED' };
    await expect(api.editAndAccept(recipientId, protectedSuggestion, input)).rejects.toThrow(); expect(fetcher).not.toHaveBeenCalled();
    input.changes = [{ index: 0, params: { withinDays: '10' } } as typeof input.changes[0]];
    await api.editAndAccept(recipientId, protectedSuggestion, input);
    expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({ expectedVersion: 2, changes: [{ index: 0, params: { withinDays: '10' } }] });
    expect(fetcher.mock.calls[0][0]).toBe(`/api/recipients/${recipientId}/plans/${suggestion.id}/edit-and-accept`);
  });
  it('refuses stale versions and opaque/read-only plans before a protected write', async () => {
    const { api, fetcher } = setup({}); const input = { expectedVersion: 99, changes: [{ index: 0, params: { withinDays: '10' } }] };
    await expect(api.editAndAccept(recipientId, protectedSuggestion, input)).rejects.toThrow();
    await expect(api.editAndAccept(recipientId, suggestion, { ...input, expectedVersion: suggestion.version })).rejects.toThrow();
    expect(fetcher).not.toHaveBeenCalled();
  });
  it('loads the authoritative active plan and unpaginated version history', async () => {
    const { api, fetcher } = setup(active);
    expect(await api.active(recipientId)).toEqual(active);
    fetcher.mockResolvedValueOnce(jsonResponse([suggestion, active]));
    expect(await api.history(recipientId)).toEqual([suggestion, active]);
    expect(fetcher.mock.calls.map(([path]) => path)).toEqual([`/api/recipients/${recipientId}/plans/active`, `/api/recipients/${recipientId}/plans/history`]);
    expect(fetcher.mock.calls.every(([, init]) => init.method === 'GET' && init.cache === 'no-store')).toBe(true);
  });
  it('creates a structured caregiver draft at the existing suggestions endpoint', async () => {
    const response = { ...suggestion, content: '', status: 'SUGGESTED', sourceType: 'CAREGIVER_SUBMITTED',
      editable: false, structuredContent: draftInput };
    const { api, fetcher } = setup(response, 201);
    const created = await api.createSuggested(recipientId, draftInput);
    expect(created.status).toBe('SUGGESTED');
    expect(fetcher.mock.calls[0][0]).toBe(`/api/recipients/${recipientId}/plans/suggestions`);
    expect(fetcher.mock.calls[0][1].method).toBe('POST');
    expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({ content: '', structuredContent: draftInput });
  });
  it('rejects invalid recipient IDs before fetching when creating a draft', async () => {
    const { api, fetcher } = setup({});
    await expect(api.createSuggested('../other', draftInput)).rejects.toBeInstanceOf(ApiError);
    expect(fetcher).not.toHaveBeenCalled();
  });
  it('refuses medication changes in a new structured draft before fetching', async () => {
    const { api, fetcher } = setup({});
    await expect(api.createSuggested(recipientId, { ...draftInput, medications: [{ name: 'Item', dose: 'Dose', schedule: 'Schedule' }] })).rejects.toBeInstanceOf(ApiError);
    expect(fetcher).not.toHaveBeenCalled();
  });
  it('preserves exact content and opaque source references while excluding extra fields', () => {
    expect(readPlan({ ...suggestion, caregiverId: 'untrusted', medication: 'untrusted' }, recipientId)).toEqual(suggestion);
  });
  it('retains unknown history statuses as read-only data', async () => {
    const { api } = setup([{ ...suggestion, status: 'toString' }]);
    expect((await api.history(recipientId))[0].status).toBe('toString');
  });
  it.each([
    { id: '../../other' }, { recipientId: 'b2222222-2222-4222-8222-222222222222' }, { version: 0 }, { version: 1.5 },
    { status: '' }, { content: null }, { sourceSummaryId: {} }, { sourcePlanId: '/external' }, { evidenceNoteIds: [] }, { createdAt: 'not-a-date' },
  ])('rejects malformed or mismatched plan data: %j', async (change) => {
    const { api } = setup([{ ...suggestion, ...change }]);
    await expect(api.history(recipientId)).rejects.toThrow('Invalid plan');
  });
  it('accepts an empty active result only for the documented plan-not-found envelope', async () => {
    const { api } = setup({ code: 'NOT_FOUND' }, 404);
    expect(await api.active(recipientId)).toBeNull();
  });
  it.each([{ code: 'RECIPIENT_NOT_FOUND' }, {}, '<html>route missing</html>'])('does not mistake inaccessible recipients/missing routes for no active plan: %j', async (body) => {
    const { api } = setup(body, 404);
    await expect(api.active(recipientId)).rejects.toBeInstanceOf(ApiError);
  });
  it('rejects a suggested record returned as the active plan', async () => {
    await expect(setup(suggestion).api.active(recipientId)).rejects.toThrow('Invalid active');
  });
  it.each([[active, active], [active, { ...suggestion, version: active.version }]])('rejects duplicate history identity/version numbers', async (first, second) => {
    await expect(setup([first, second]).api.history(recipientId)).rejects.toThrow('Invalid plan history');
  });
  it.each(['accept', 'dismiss'] as const)('posts a bodyless %s operation and ignores unreliable POST timestamps', async (action) => {
    const { api, fetcher } = setup({ ...suggestion, createdAt: null });
    const controller = new AbortController();
    await api.transition(recipientId, suggestion.id, action, controller.signal);
    expect(fetcher.mock.calls[0][0]).toBe(`/api/recipients/${recipientId}/plans/${suggestion.id}/${action}`);
    expect(fetcher.mock.calls[0][1]).toMatchObject({ method: 'POST', body: undefined, signal: controller.signal });
    expect(new Headers(fetcher.mock.calls[0][1].headers).has('Idempotency-Key')).toBe(false);
  });
  it('rejects unsafe IDs or unsupported actions before sending anything', async () => {
    const { api, fetcher } = setup([]);
    await expect(api.history('../other')).rejects.toThrow();
    await expect(api.transition(recipientId, '../other', 'accept')).rejects.toThrow();
    await expect(api.transition(recipientId, suggestion.id, 'edit-and-accept' as PlanAction)).rejects.toThrow();
    expect(fetcher).not.toHaveBeenCalled();
  });
  it.each([
    [409, { code: 'INVALID_PLAN_TRANSITION' }, 'plansInvalidTransition'], [409, {}, 'plansConflict'],
    [404, {}, 'plansUnavailable'], [403, {}, 'plansUnavailable'], [422, {}, 'plansRejected'],
    [429, {}, 'plansRateLimited'], [503, {}, 'plansServiceError'],
  ])('translates HTTP %s without displaying private backend messages', (status, body, expected) => {
    expect(planErrorMessage(new ApiError(status as number, { ...(body as object), message: 'private diagnostic' }))).toBe(expected);
  });
  it('posts current BYOK credentials to plan generation endpoint', async () => {
    const response = { plan: protectedSuggestion, aiUnavailable: false, safety: generationSafety };
    const { fetcher } = setup(response);
    const transport = createApiClient({ apiBaseUrl: '' }, fetcher);
    const authenticatedClient: ApiClient = {
      baseUrl: transport.baseUrl,
      request: <TResponse, TBody = unknown>(request: ApiRequest<TBody>) =>
        transport.request<TResponse, TBody>({ ...request, accessToken: 'synthetic-access-token' }),
    };
    const result = await createPlansApi(authenticatedClient).generateByok(summaryRequest, byokCredentials);

    expect(fetcher).toHaveBeenCalledTimes(1);
    expect(fetcher.mock.calls[0][0]).toBe('/api/plans/generate/byok');
    expect(fetcher.mock.calls[0][1].method).toBe('POST');
    expect(new Headers(fetcher.mock.calls[0][1].headers).get('Authorization')).toBe('Bearer synthetic-access-token');
    expect(JSON.parse(fetcher.mock.calls[0][1].body as string)).toEqual({
      summary: { recipientId, periodEndInclusive: '2026-10-22', periodDays: 7 },
      credentials: byokCredentials,
    });
    expect(result).toEqual({
      plan: protectedSuggestion,
      aiUnavailable: false,
      safety: { ...generationSafety, incomplete: false },
      invalidPlan: false,
    });
  });
  it('rejects invalid BYOK credentials before making a request', async () => {
    const { api, fetcher } = setup({});
    const invalidCredentials: unknown[] = [
      {},
      { providerId: 'UNSUPPORTED', model: 'model', apiKey: 'key' },
      { providerId: 'GEMINI', model: '', apiKey: 'key' },
      { providerId: 'GEMINI', model: ' \t ', apiKey: 'key' },
      { providerId: 'GEMINI', model: 'model', apiKey: '' },
      { providerId: 'GEMINI', model: 'model', apiKey: ' \n ' },
    ];

    for (const credentials of invalidCredentials) {
      await expect(api.generateByok(summaryRequest, credentials as ByokCredentials)).rejects.toBeInstanceOf(ApiError);
    }
    expect(fetcher).not.toHaveBeenCalled();
  });
  it('rejects invalid BYOK summary request before fetching', async () => {
    const { api, fetcher } = setup({});
    const invalidRequests: unknown[] = [
      { ...summaryRequest, recipientId: '../other' },
      { ...summaryRequest, periodEndInclusive: '2026-02-30' },
      { ...summaryRequest, periodDays: 8 },
    ];

    for (const request of invalidRequests) {
      await expect(api.generateByok(request as SummaryRequest, byokCredentials)).rejects.toBeInstanceOf(ApiError);
    }
    expect(fetcher).not.toHaveBeenCalled();
  });
  it('does not return or serialize provider credentials', async () => {
    const response = {
      plan: { ...protectedSuggestion, credentials: byokCredentials, apiKey: byokCredentials.apiKey },
      aiUnavailable: false,
      safety: generationSafety,
      credentials: byokCredentials,
    };
    const { api } = setup(response);
    const result = await api.generateByok(summaryRequest, byokCredentials);

    expect(result).not.toHaveProperty('credentials');
    expect(result.plan).not.toHaveProperty('credentials');
    expect(JSON.stringify(result)).not.toContain(byokCredentials.apiKey);
  });
  it('does not return an API key echoed in generated plan content', async () => {
    const response = {
      plan: { ...protectedSuggestion, content: `Unsafe echoed credential: ${byokCredentials.apiKey}` },
      aiUnavailable: false,
      safety: generationSafety,
    };
    const { api } = setup(response);
    const result = await api.generateByok(summaryRequest, byokCredentials);

    expect(result.plan).toBeNull();
    expect(result.invalidPlan).toBe(true);
    expect(JSON.stringify(result)).not.toContain(byokCredentials.apiKey);
  });
  it('does not retain raw provider error details in BYOK request errors', async () => {
    const { api } = setup({ message: byokCredentials.apiKey, providerResponse: byokCredentials.apiKey }, 503);
    const error = await api.generateByok(summaryRequest, byokCredentials).then(
      () => null,
      (cause: unknown) => cause,
    );

    expect(error).toBeInstanceOf(ApiError);
    if (!(error instanceof ApiError)) throw new Error('Expected an API error.');
    expect(error.status).toBe(503);
    expect(error.details).toBeNull();
    expect(error.message).not.toContain(byokCredentials.apiKey);
  });
  it('parses unavailable BYOK generation without an actionable plan', async () => {
    const { api } = setup({ plan: protectedSuggestion, aiUnavailable: true, safety: generationSafety });

    const result = await api.generateByok(summaryRequest, byokCredentials);

    expect(result.aiUnavailable).toBe(true);
    expect(result.plan).toBeNull();
    expect(result.invalidPlan).toBe(false);
    expect(result.safety.needsDoctorBanner).toBe(true);
  });
  it('marks invalid BYOK plan response as invalid', async () => {
    const { api } = setup({
      plan: { ...protectedSuggestion, version: 0, content: 'unsafe provider response' },
      aiUnavailable: false,
      safety: generationSafety,
    });

    const result = await api.generateByok(summaryRequest, byokCredentials);

    expect(result.invalidPlan).toBe(true);
    expect(result.plan).toBeNull();
    expect(JSON.stringify(result)).not.toContain('unsafe provider response');
  });
  it('forwards abort signal to BYOK generation request', async () => {
    const { api, fetcher } = setup({ plan: protectedSuggestion, aiUnavailable: false, safety: generationSafety });
    const controller = new AbortController();

    await api.generateByok(summaryRequest, byokCredentials, controller.signal);

    expect(fetcher.mock.calls[0][1].signal).toBe(controller.signal);
  });
});
