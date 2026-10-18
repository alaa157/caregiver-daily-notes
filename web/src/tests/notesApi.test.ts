import { describe, expect, it, vi } from 'vitest';
import { createNotesApi, isNoteDate, noteErrorMessage } from '../api/notes';
import { ApiError, createApiClient } from '../lib/api';
import { jsonResponse } from './authFixtures';
import { addendumFixture, noteFixture, noteObservations } from './noteFixtures';
import { createInput, emptyNoteValues, observationInput, validateNoteValues } from '../features/notes/noteForm';

function setup(body: unknown, status = 200) {
  const fetcher = vi.fn().mockImplementation(() => Promise.resolve(jsonResponse(body, status)));
  return { api: createNotesApi(createApiClient({ apiBaseUrl: '' }, fetcher)), fetcher };
}
describe('daily-note API and entered values', () => {
  it('saves all reported values verbatim with an idempotency key and no ownership fields', async () => {
    const { api, fetcher } = setup({ ...noteFixture, caregiverId: 'private' }, 201);
    const result = await api.create({ ...noteObservations, recipientId: noteFixture.recipientId, date: noteFixture.date, caregiverId: 'forged' } as Parameters<typeof api.create>[0], 'save-key');
    expect(result).toEqual(noteFixture);
    const [path, init] = fetcher.mock.calls[0];
    expect(path).toBe('/api/notes');
    expect(init.method).toBe('POST');
    expect(new Headers(init.headers).get('Idempotency-Key')).toBe('save-key');
    expect(JSON.parse(init.body)).toEqual({ ...noteObservations, recipientId: noteFixture.recipientId, date: noteFixture.date });
  });
  it('only appends selected fields and rejects client-controlled note/owner fields', async () => {
    const { api, fetcher } = setup(addendumFixture, 201);
    await api.append(noteFixture.id, { medicationStatus: 'MISSED', medicationVerified: false, noteId: 'forged', caregiverId: 'forged' } as Parameters<typeof api.append>[1], 'append-key');
    expect(fetcher.mock.calls[0][0]).toBe(`/api/notes/${noteFixture.id}/addenda`);
    expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({ medicationStatus: 'MISSED', medicationVerified: false });
    expect(fetcher.mock.calls[0][1].method).toBe('POST');
  });
  it('reads original/addenda and opens an exact recipient/date using existing endpoints', async () => {
    const { api, fetcher } = setup(noteFixture);
    const controller = new AbortController();
    expect(await api.get(noteFixture.id, controller.signal)).toEqual(noteFixture);
    expect(fetcher.mock.calls[0][1].signal).toBe(controller.signal);
    await api.find(noteFixture.recipientId, noteFixture.date);
    expect(fetcher.mock.calls[1][0]).toBe(`/api/notes/by-recipient/${noteFixture.recipientId}?date=${noteFixture.date}`);
    fetcher.mockResolvedValue(jsonResponse([addendumFixture]));
    expect(await api.addenda(noteFixture.id)).toEqual([addendumFixture]);
  });
  it.each([
    { ...noteFixture, painScore: 11 }, { ...noteFixture, painScore: 1.5 }, { ...noteFixture, medicationStatus: 'YES' },
    { ...noteFixture, fallReported: 'false' }, { ...noteFixture, date: '2026-02-30' }, { ...noteFixture, createdAt: null },
    { ...noteFixture, freeText: undefined }, { ...noteFixture, addendumCount: -1 },
  ])('rejects a malformed note instead of asserting an observation: %j', async (body) => {
    const { api } = setup(body);
    await expect(api.get(noteFixture.id)).rejects.toThrow('Invalid note response');
  });
  it('rejects malformed IDs, dates, keys and mismatched record identities', async () => {
    const { api, fetcher } = setup(noteFixture);
    await expect(api.get('../private')).rejects.toMatchObject({ status: 404 });
    await expect(api.find(noteFixture.recipientId, '2026-02-30')).rejects.toMatchObject({ status: 400 });
    await expect(api.append(noteFixture.id, { freeText: 'x' }, 'bad\nkey')).rejects.toThrow('Invalid save key');
    expect(fetcher).not.toHaveBeenCalled();
    fetcher.mockResolvedValue(jsonResponse({ ...noteFixture, id: addendumFixture.id }));
    await expect(api.get(noteFixture.id)).rejects.toThrow('Invalid note response');
    fetcher.mockResolvedValue(jsonResponse([{ ...addendumFixture, noteId: addendumFixture.id }]));
    await expect(api.addenda(noteFixture.id)).rejects.toThrow('Invalid note response');
  });
  it('validates required recipient/date, exact lengths, leap days and pain boundaries', () => {
    const values = emptyNoteValues();
    values.date = '';
    expect(validateNoteValues(values)).toMatchObject({ recipientId: 'notesRequiredRecipient', date: 'notesRequiredDate' });
    Object.assign(values, { recipientId: noteFixture.recipientId, date: noteFixture.date, mood: 'م'.repeat(40), freeText: 'x'.repeat(20000), painScore: '0' });
    expect(validateNoteValues(values)).toEqual({});
    expect(isNoteDate('2024-02-29')).toBe(true);
    expect(isNoteDate('2025-02-29')).toBe(false);
    expect(isNoteDate('2026-13-01')).toBe(false);
    Object.assign(values, { mood: 'x'.repeat(41), freeText: 'x'.repeat(20001), painScore: '1.5' });
    expect(validateNoteValues(values)).toMatchObject({ mood: 'notesTextLimit', freeText: 'notesFreeTextLimit', painScore: 'notesPainInvalid' });
    for (const value of ['-1', '11', '1e1']) expect(validateNoteValues({ ...values, painScore: value }).painScore).toBe('notesPainInvalid');
  });
  it('distinguishes omitted, unsure, false, and zero, preserving Unicode and whitespace', () => {
    const values = { ...emptyNoteValues(), recipientId: noteFixture.recipientId, freeText: noteObservations.freeText!, medicationStatus: 'UNKNOWN', medicationVerified: 'false', painScore: '0', fallReported: 'false' };
    expect(createInput(values)).toMatchObject({ mood: null, freeText: noteObservations.freeText, medicationStatus: 'UNKNOWN', medicationVerified: false, painScore: 0, fallReported: false });
    expect(observationInput(values, { medicationVerified: true, painScore: true })).toEqual({ medicationVerified: false, painScore: 0 });
    expect(validateNoteValues(values, {})).toEqual({ form: 'notesSelectCorrection' });
    expect(validateNoteValues(values, { mood: true })).toEqual({ mood: 'notesChooseValue' });
  });
  it('maps errors to fixed messages rather than backend diagnostics', () => {
    for (const status of [403, 404]) expect(noteErrorMessage(new ApiError(status, { message: 'private' }))).toBe('notesUnavailable');
    expect(noteErrorMessage(new ApiError(409, { message: 'private' }))).toBe('notesConflict');
    expect(noteErrorMessage(new ApiError(400, null))).toBe('notesValidationError');
    expect(noteErrorMessage(new TypeError('private'))).toBe('notesNetworkError');
  });
});
