import { describe, expect, it, vi } from 'vitest';
import { createPlansApi, planErrorMessage, readPlan, type PlanAction } from '../api/plans';
import { ApiError, createApiClient } from '../lib/api';
import { activePlanFixture as active, suggestedPlanFixture as suggestion } from './planFixtures';
import { recipientFixture } from './recipientFixtures';
import { jsonResponse } from './authFixtures';

const recipientId = recipientFixture.id;
function setup(response: unknown, status = 200) {
  const fetcher = vi.fn().mockResolvedValue(jsonResponse(response, status));
  return { fetcher, api: createPlansApi(createApiClient({ apiBaseUrl: '' }, fetcher)) };
}
describe('plan contract and scoped transport', () => {
  it('loads the authoritative active plan and unpaginated version history', async () => {
    const { api, fetcher } = setup(active);
    expect(await api.active(recipientId)).toEqual(active);
    fetcher.mockResolvedValueOnce(jsonResponse([suggestion, active]));
    expect(await api.history(recipientId)).toEqual([suggestion, active]);
    expect(fetcher.mock.calls.map(([path]) => path)).toEqual([`/api/recipients/${recipientId}/plans/active`, `/api/recipients/${recipientId}/plans/history`]);
    expect(fetcher.mock.calls.every(([, init]) => init.method === 'GET' && init.cache === 'no-store')).toBe(true);
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
});
