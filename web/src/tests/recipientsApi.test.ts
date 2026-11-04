import { describe, expect, it, vi } from 'vitest';
import { createApiClient, ApiError } from '../lib/api';
import { createRecipientsApi, recipientErrorMessage, validateRecipient } from '../api/recipients';
import { jsonResponse } from './authFixtures';
import { recipientFixture as recipient } from './recipientFixtures';

function apiWithResponse(body: unknown, status = 200) {
  const fetcher = vi.fn().mockResolvedValue(jsonResponse(body, status));
  const api = createRecipientsApi(createApiClient({ apiBaseUrl: '' }, fetcher));
  return { api, fetcher };
}

describe('recipient API contract', () => {
  it('loads the backend array and safe response fields', async () => {
    const { api, fetcher } = apiWithResponse([{ ...recipient, caregiverId: 'must-not-be-exposed' }]);
    expect(await api.list()).toEqual([recipient]);
    expect(fetcher).toHaveBeenCalledWith('/api/recipients', expect.objectContaining({ method: 'GET', cache: 'no-store', body: undefined }));
  });

  it('creates with only the editable fields even when passed extra properties', async () => {
    const { api, fetcher } = apiWithResponse(recipient, 201);
    await api.create({ name: ' Samir Hassan ', relationship: ' Father ', caregiverId: 'forged', active: false } as Parameters<typeof api.create>[0]);
    expect(fetcher).toHaveBeenCalledWith('/api/recipients', expect.objectContaining({ method: 'POST', body: JSON.stringify({ name: 'Samir Hassan', relationship: 'Father' }) }));
  });

  it('accepts a real creation response with pending timestamps without inventing dates or exposing ownership', async () => {
    const { api, fetcher } = apiWithResponse({ ...recipient, createdAt: null, updatedAt: null, caregiverId: 'private-owner' }, 201);
    expect(await api.create({ name: recipient.name, relationship: recipient.relationship })).toEqual({
      id: recipient.id, name: recipient.name, relationship: recipient.relationship, active: true,
      lifecycleStatus: 'ACTIVE', archivedAt: null, archivedBy: null, restoredAt: null, restoredBy: null,
    });
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it('still rejects an invalid identity in a creation response', async () => {
    const { api } = apiWithResponse({ ...recipient, id: '../unsafe', createdAt: null, updatedAt: null }, 201);
    await expect(api.create({ name: recipient.name, relationship: null })).rejects.toThrow('Invalid recipient');
  });

  it('sends the full editable representation with PUT and clears an optional relationship', async () => {
    const { api, fetcher } = apiWithResponse({ ...recipient, relationship: null });
    await api.update(recipient.id, { name: recipient.name, relationship: '  ' });
    expect(fetcher).toHaveBeenCalledWith(`/api/recipients/${recipient.id}`, expect.objectContaining({ method: 'PUT', body: JSON.stringify({ name: recipient.name, relationship: null }) }));
  });

  it('lists a specific lifecycle state using the server filter', async () => {
    const archived = { ...recipient, active: false, lifecycleStatus: 'ARCHIVED' as const,
      archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
    const { api, fetcher } = apiWithResponse([archived]);
    expect(await api.list(undefined, 'ARCHIVED')).toEqual([archived]);
    expect(fetcher).toHaveBeenCalledWith('/api/recipients?status=ARCHIVED', expect.objectContaining({ method: 'GET' }));
  });

  it('archives and restores through distinct PATCH endpoints', async () => {
    const archived = { ...recipient, active: false, lifecycleStatus: 'ARCHIVED' as const,
      archivedAt: '2026-10-15T10:00:00Z', archivedBy: 'b1111111-1111-4111-8111-111111111111' };
    const restored = { ...recipient, restoredAt: '2026-10-16T10:00:00Z', restoredBy: 'b1111111-1111-4111-8111-111111111111' };
    const fetcher = vi.fn().mockResolvedValueOnce(jsonResponse(archived)).mockResolvedValueOnce(jsonResponse(restored));
    const api = createRecipientsApi(createApiClient({ apiBaseUrl: '' }, fetcher));
    expect((await api.archive(recipient.id)).lifecycleStatus).toBe('ARCHIVED');
    expect(fetcher.mock.calls[0][0]).toBe(`/api/recipients/${recipient.id}/archive`);
    expect((await api.restore(recipient.id)).lifecycleStatus).toBe('ACTIVE');
    expect(fetcher.mock.calls[1][0]).toBe(`/api/recipients/${recipient.id}/restore`);
  });

  it('forwards cancellation and never sends malformed IDs', async () => {
    const { api, fetcher } = apiWithResponse(recipient);
    const controller = new AbortController();
    await api.get(recipient.id, controller.signal);
    expect(fetcher.mock.calls[0][1].signal).toBe(controller.signal);
    await expect(api.get('../someone-else')).rejects.toMatchObject({ status: 404 });
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it.each([{ items: [] }, [null], [{ ...recipient, active: 'true' }], [{ ...recipient, updatedAt: 'not-a-date' }]])('rejects malformed responses instead of rendering false data: %j', async (body) => {
    const { api } = apiWithResponse(body);
    await expect(api.list()).rejects.toThrow('Invalid recipient');
  });

  it('rejects an invalid lifecycle status in recipient response', async () => {
    const { api } = apiWithResponse({ ...recipient, lifecycleStatus: 'DELETED' });
    await expect(api.get(recipient.id)).rejects.toThrow('Invalid recipient response.');
  });

  it('validates required names and backend length boundaries', () => {
    expect(validateRecipient({ name: '   ', relationship: null })).toEqual({ name: 'recipientNameRequired' });
    expect(validateRecipient({ name: 'x'.repeat(120), relationship: 'y'.repeat(80) })).toEqual({});
    expect(validateRecipient({ name: 'x'.repeat(121), relationship: 'y'.repeat(81) })).toEqual({ name: 'recipientNameTooLong', relationship: 'recipientRelationshipTooLong' });
    expect(validateRecipient({ name: ' سمير ', relationship: '   ' })).toEqual({});
  });

  it.each([
    [403, 'recipientUnavailable'], [404, 'recipientUnavailable'], [409, 'recipientConflict'],
    [400, 'recipientValidationError'], [422, 'recipientValidationError'], [429, 'recipientRateLimited'], [500, 'recipientServiceError'],
  ])('translates HTTP %i without exposing backend diagnostics', (status, key) => {
    expect(recipientErrorMessage(new ApiError(status as number, { message: 'private diagnostics' }))).toBe(key);
  });
});
