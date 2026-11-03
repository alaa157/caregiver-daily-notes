import { describe, expect, it, vi } from 'vitest';
import { createNotesApi, isNoteDate, noteErrorMessage } from '../api/notes';
import { ApiError, createApiClient } from '../lib/api';
import { jsonResponse } from './authFixtures';
import {
  addendumFixture,
  noteFixture,
  noteObservations,
  structuredObservations,
} from './noteFixtures';
import {
  createInput,
  emptyNoteValues,
  observationInput,
  validateNoteValues,
} from '../features/notes/noteForm';

function setup(body: unknown, status = 200) {
  const fetcher = vi.fn().mockImplementation(() => Promise.resolve(jsonResponse(body, status)));
  return { api: createNotesApi(createApiClient({ apiBaseUrl: '' }, fetcher)), fetcher };
}
describe('daily-note API and explicit observation values', () => {
  it('saves the structured observation envelope with an idempotency key and no ownership fields', async () => {
    const { api, fetcher } = setup({ ...noteFixture, caregiverId: 'private' }, 201);
    const input = {
      recipientId: noteFixture.recipientId,
      date: noteFixture.date,
      observations: structuredObservations,
      medicationVerification: 'UNVERIFIED' as const,
      caregiverId: 'forged',
    };
    const result = await api.create(input, 'save-key');
    expect(result).toEqual(noteFixture);
    const [path, init] = fetcher.mock.calls[0];
    expect(path).toBe('/api/notes');
    expect(init.method).toBe('POST');
    expect(new Headers(init.headers).get('Idempotency-Key')).toBe('save-key');
    expect(JSON.parse(init.body)).toEqual({
      recipientId: noteFixture.recipientId,
      date: noteFixture.date,
      observations: structuredObservations,
      medicationVerification: 'UNVERIFIED',
    });
  });
  it('only appends selected structured fields and independent verification', async () => {
    const { api, fetcher } = setup(addendumFixture, 201);
    await api.append(noteFixture.id, {
      observations: { medicationStatus: { state: 'RECORDED', value: 'MISSED' } },
      medicationVerification: 'UNVERIFIED',
      noteId: 'forged',
      caregiverId: 'forged',
    } as Parameters<typeof api.append>[1], 'append-key');
    expect(fetcher.mock.calls[0][0]).toBe(`/api/notes/${noteFixture.id}/addenda`);
    expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({
      observations: { medicationStatus: { state: 'RECORDED', value: 'MISSED' } },
      medicationVerification: 'UNVERIFIED',
    });
    expect(fetcher.mock.calls[0][1].method).toBe('POST');
  });
  it('reads structured notes/addenda and opens an exact recipient/date using existing endpoints', async () => {
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
    { ...noteFixture, painScore: 11 },
    { ...noteFixture, medicationStatus: 'YES' },
    { ...noteFixture, fallReported: 'false' },
    { ...noteFixture, date: '2026-02-30' },
    { ...noteFixture, createdAt: null },
    { ...noteFixture, addendumCount: -1 },
    { ...noteFixture, observations: { ...structuredObservations, mood: { state: 'RECORDED' } } },
    { ...noteFixture, observations: { ...structuredObservations, painScore: { state: 'RECORDED', value: 11 } } },
    { ...noteFixture, observations: { ...structuredObservations, fallReported: { state: 'UNKNOWN', value: false } } },
    { ...noteFixture, observations: { ...structuredObservations, medicationVerification: 'MAYBE' } },
  ])('rejects malformed state or value instead of asserting an observation: %j', async (body) => {
    const { api } = setup(body);
    await expect(api.get(noteFixture.id)).rejects.toThrow('Invalid note response');
  });
  it('conservatively classifies a legacy flat response when the state envelope is absent', async () => {
    const { api } = setup(Object.fromEntries(
      Object.entries(noteFixture).filter(([key]) => key !== 'observations'),
    ));
    const result = await api.get(noteFixture.id);
    expect(result.observations.mood.state).toBe('RECORDED');
    expect(result.observations.appetite.state).toBe('RECORDED');
  });
  it('rejects malformed IDs, dates, keys and mismatched record identities', async () => {
    const { api, fetcher } = setup(noteFixture);
    await expect(api.get('../private')).rejects.toMatchObject({ status: 404 });
    await expect(api.find(noteFixture.recipientId, '2026-02-30')).rejects.toMatchObject({ status: 400 });
    await expect(api.append(noteFixture.id, { observations: { freeText: { state: 'RECORDED', value: 'x' } } }, 'bad\nkey')).rejects.toThrow('Invalid save key');
    expect(fetcher).not.toHaveBeenCalled();
    fetcher.mockResolvedValue(jsonResponse({ ...noteFixture, id: addendumFixture.id }));
    await expect(api.get(noteFixture.id)).rejects.toThrow('Invalid note response');
    fetcher.mockResolvedValue(jsonResponse([{ ...addendumFixture, noteId: addendumFixture.id }]));
    await expect(api.addenda(noteFixture.id)).rejects.toThrow('Invalid note response');
  });
  it('validates required metadata, field lengths, state/value pairs and numeric boundaries', () => {
    const values = emptyNoteValues();
    values.date = '';
    expect(validateNoteValues(values)).toMatchObject({ recipientId: 'notesRequiredRecipient', date: 'notesRequiredDate' });
    Object.assign(values, { recipientId: noteFixture.recipientId, date: noteFixture.date });
    values.observations = {
      ...values.observations,
      mood: { state: 'RECORDED', value: 'م'.repeat(40) },
      freeText: { state: 'RECORDED', value: 'x'.repeat(20000) },
      painScore: { state: 'RECORDED', value: 0 },
      fallReported: { state: 'RECORDED', value: false },
    };
    expect(validateNoteValues(values)).toEqual({});
    expect(isNoteDate('2024-02-29')).toBe(true);
    expect(isNoteDate('2025-02-29')).toBe(false);
    expect(isNoteDate('2026-13-01')).toBe(false);
    values.observations = {
      ...values.observations,
      mood: { state: 'RECORDED', value: 'x'.repeat(41) },
      freeText: { state: 'RECORDED', value: 'x'.repeat(20001) },
      painScore: { state: 'RECORDED', value: 11 },
    };
    expect(validateNoteValues(values)).toMatchObject({
      mood: 'notesTextLimit', freeText: 'notesFreeTextLimit', painScore: 'notesPainInvalid',
    });
    for (const value of [-1, 11, 1.5]) {
      expect(validateNoteValues({
        ...values,
        observations: { ...values.observations, painScore: { state: 'RECORDED', value } },
      }).painScore).toBe('notesPainInvalid');
    }
  });
  it('preserves empty text, false, zero, explicit states, and selected-field corrections', () => {
    const values = emptyNoteValues();
    values.recipientId = noteFixture.recipientId;
    values.date = noteFixture.date;
    values.observations = {
      ...values.observations,
      mood: { state: 'RECORDED', value: '' },
      medicationStatus: { state: 'UNKNOWN' },
      painScore: { state: 'RECORDED', value: 0 },
      fallReported: { state: 'RECORDED', value: false },
      freeText: { state: 'RECORDED', value: noteObservations.freeText! },
    };
    expect(createInput(values)).toEqual({
      recipientId: noteFixture.recipientId, date: noteFixture.date,
      observations: values.observations, medicationVerification: 'NOT_ASSERTED',
    });
    expect(observationInput(values, { fallReported: true, medicationVerification: true })).toEqual({
      observations: { fallReported: { state: 'RECORDED', value: false } },
      medicationVerification: 'NOT_ASSERTED',
    });
    expect(validateNoteValues(values, {})).toEqual({ form: 'notesSelectCorrection' });
    expect(validateNoteValues(values, { mood: true })).toEqual({});
    expect(validateNoteValues({
      ...values,
      observations: { ...values.observations, mood: { state: 'RECORDED' } },
    })).toMatchObject({ mood: 'obsRecordedValueRequired' });
  });
  it('maps errors to fixed messages rather than backend diagnostics', () => {
    for (const status of [403, 404]) expect(noteErrorMessage(new ApiError(status, { message: 'private' }))).toBe('notesUnavailable');
    expect(noteErrorMessage(new ApiError(409, { message: 'private' }))).toBe('notesConflict');
    expect(noteErrorMessage(new ApiError(400, null))).toBe('notesValidationError');
    expect(noteErrorMessage(new TypeError('private'))).toBe('notesNetworkError');
  });
});
