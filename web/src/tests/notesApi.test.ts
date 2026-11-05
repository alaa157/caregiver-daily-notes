import { describe, expect, it, vi } from 'vitest';
import { createNotesApi, isNoteDate, noteErrorMessage } from '../api/notes';
import { observationFields, type EffectiveObservation } from '../api/notes';
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

function effective(state: EffectiveObservation['state'], value: EffectiveObservation['value'], operation: EffectiveObservation['lastOperation'] = null,
  provenance: Pick<EffectiveObservation, 'eventId' | 'actorId' | 'updatedAt'> = {
    eventId: null, actorId: null, updatedAt: noteFixture.createdAt,
  }): EffectiveObservation {
  return { state, value, rawValue: null, lastOperation: operation, ...provenance };
}
function correctionResponse() {
  const effectiveObservations = Object.fromEntries(observationFields.map((field) => {
    const observation = noteFixture.observations[field];
    return [field, { ...effective(observation.state, observation.value ?? null), rawValue: observation.rawValue ?? null }];
  })) as Record<(typeof observationFields)[number], EffectiveObservation>;
  const event = { id: 'f6666666-6666-4666-8666-666666666666', noteId: noteFixture.id, sequenceNo: 1,
    createdAt: '2026-10-18T12:00:00Z', actorId: 'a1111111-1111-4111-8111-111111111111',
    field: 'mood', operation: 'SET', value: '', previousEffective: effective('RECORDED', noteFixture.mood),
    effectiveAfter: effective('RECORDED', '', 'SET', {
      eventId: 'f6666666-6666-4666-8666-666666666666',
      actorId: 'a1111111-1111-4111-8111-111111111111',
      updatedAt: '2026-10-18T12:00:00Z',
    }) };
  effectiveObservations.mood = event.effectiveAfter;
  effectiveObservations.painScore = effective('RECORDED', 0);
  effectiveObservations.fallReported = effective('RECORDED', false);
  const originalNote = { ...noteFixture, correctionCount: 1 };
  return { originalNote, events: [event], effectiveObservations };
}
function replaceMoodState(
  observation: EffectiveObservation,
  state: EffectiveObservation['state'],
): EffectiveObservation {
  return { ...observation, state };
}

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
      observations: {
        ...structuredObservations,
        fallReported: { state: 'NOT_RECORDED' as const },
      },
      medicationVerification: 'UNVERIFIED' as const,
      caregiverId: 'forged',
    };
    const result = await api.create(input, 'save-key');
    expect(result).toEqual(noteFixture);
    const [path, init] = fetcher.mock.calls[0];
    expect(path).toBe('/api/notes');
    expect(init.method).toBe('POST');
    expect(new Headers(init.headers).get('Idempotency-Key')).toBe('save-key');
    const serializedObservations = JSON.parse(init.body).observations;
    expect(serializedObservations.fallReported).toEqual({ state: 'NOT_RECORDED', value: null });
    expect(serializedObservations.medicationStatus).toEqual({ state: 'UNKNOWN', value: null });
    expect(JSON.parse(init.body)).toEqual({
      recipientId: noteFixture.recipientId,
      date: noteFixture.date,
      observations: Object.fromEntries(observationFields.map((field) => {
        const observation = input.observations[field];
        return [field, {
          state: observation.state,
          value: observation.state === 'RECORDED' ? observation.value : null,
        }];
      })),
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
  it('posts typed corrections without encoding omission as null and validates event replay', async () => {
    const response = correctionResponse();
    const { api, fetcher } = setup(response, 201);
    const input = [
      { field: 'mood', operation: 'SET', value: '' },
      { field: 'painScore', operation: 'SET', value: 0 },
      { field: 'fallReported', operation: 'SET', value: false },
      { field: 'appetite', operation: 'UNKNOWN' },
      { field: 'sleep', operation: 'CLEAR' },
      { field: 'mobility', operation: 'NOT_RECORDED' },
    ] as const;
    const result = await api.correct(noteFixture.id, [...input], 'correction-key');
    expect(result.events[0].value).toBe('');
    expect(result.effectiveObservations.painScore.value).toBe(0);
    expect(result.effectiveObservations.fallReported.value).toBe(false);
    expect(fetcher.mock.calls[0][0]).toBe(`/api/notes/${noteFixture.id}/corrections`);
    expect(JSON.parse(fetcher.mock.calls[0][1].body)).toEqual({ corrections: input });
    expect(new Headers(fetcher.mock.calls[0][1].headers).get('Idempotency-Key')).toBe('correction-key');
    expect(fetcher.mock.calls[0][1].method).toBe('POST');
  });
  it('uses UNKNOWN as a medication observation state rather than a SET value', async () => {
    const { api, fetcher } = setup(correctionResponse());
    await expect(api.correct(noteFixture.id,
      [{ field: 'medicationStatus', operation: 'SET', value: 'UNKNOWN' }], 'invalid-correction-key'))
      .rejects.toMatchObject({ status: 400 });
    expect(fetcher).not.toHaveBeenCalled();
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
  it('rejects typed values for non recorded observation states', async () => {
    const invalidObservations = [
      { ...structuredObservations, painScore: { state: 'UNKNOWN', value: 5 } },
      { ...structuredObservations, painScore: { state: 'NOT_RECORDED', value: 5 } },
      { ...structuredObservations, medicationStatus: { state: 'UNKNOWN', value: 'TAKEN' } },
    ] as const;
    for (const observations of invalidObservations) {
      const { api, fetcher } = setup(noteFixture);
      await expect(api.create({
        recipientId: noteFixture.recipientId,
        date: noteFixture.date,
        observations,
        medicationVerification: 'NOT_ASSERTED',
      }, 'invalid-state-value-key')).rejects.toThrow('Invalid observation input.');
      expect(fetcher).not.toHaveBeenCalled();
    }
  });
  it('rejects a legacy ambiguous state from a create request', async () => {
    const { api, fetcher } = setup(noteFixture);
    await expect(api.create({
      recipientId: noteFixture.recipientId,
      date: noteFixture.date,
      observations: { ...structuredObservations, mood: { state: 'LEGACY_AMBIGUOUS' } },
      medicationVerification: 'NOT_ASSERTED',
    }, 'legacy-state-key')).rejects.toThrow('Invalid observation input.');
    expect(fetcher).not.toHaveBeenCalled();
  });

  it('serializes every non-recorded selectable state as an explicit null value', async () => {
    const { api, fetcher } = setup(noteFixture, 201);
    await api.create({
      recipientId: noteFixture.recipientId,
      date: noteFixture.date,
      observations: {
        ...structuredObservations,
        mood: { state: 'UNKNOWN' },
        appetite: { state: 'NOT_RECORDED' },
        sleep: { state: 'NOT_APPLICABLE' },
      },
      medicationVerification: 'NOT_ASSERTED',
    }, 'non-recorded-key');
    const body = JSON.parse(fetcher.mock.calls[0][1].body);
    expect(body.observations).toMatchObject({
      mood: { state: 'UNKNOWN', value: null },
      appetite: { state: 'NOT_RECORDED', value: null },
      sleep: { state: 'NOT_APPLICABLE', value: null },
    });
  });
  it('rejects correction history with duplicate or decreasing sequence numbers', async () => {
    const response = correctionResponse();
    const first = response.events[0];
    const second = { ...first, id: 'f6666666-6666-4666-8666-666666666667' };
    for (const events of [[{ ...first, sequenceNo: 2 }, { ...second, sequenceNo: 1 }], [
      { ...first, sequenceNo: 1 }, { ...second, sequenceNo: 1 },
    ]]) {
      const { api, fetcher } = setup({ ...response, events });
      await expect(api.correctionHistory(noteFixture.id)).rejects.toThrow('Invalid correction history response');
      expect(fetcher).toHaveBeenCalledTimes(1);
    }
  });
  it('accepts untouched effective observations that match the original note', async () => {
    const response = correctionResponse();
    const { api } = setup(response);
    const history = await api.correctionHistory(noteFixture.id);
    expect(history.effectiveObservations.appetite).toMatchObject({
      state: noteFixture.observations.appetite.state,
      value: noteFixture.observations.appetite.value,
      rawValue: noteFixture.observations.appetite.rawValue ?? null,
      lastOperation: null,
      eventId: null,
      actorId: null,
      updatedAt: noteFixture.createdAt,
    });
  });

  it.each([
    ['different value', (value: EffectiveObservation) => ({ ...value, value: 'angry' })],
    ['different state', (value: EffectiveObservation) => replaceMoodState(value, 'UNKNOWN')],
    ['different raw value', (value: EffectiveObservation) => ({ ...value, rawValue: 'unexpected' })],
    ['different correction provenance', (value: EffectiveObservation) => ({
      ...value, eventId: 'f6666666-6666-4666-8666-666666666667',
    })],
    ['different correction actor', (value: EffectiveObservation) => ({
      ...value, actorId: 'a2222222-2222-4222-8222-222222222222',
    })],
    ['different correction timestamp', (value: EffectiveObservation) => ({ ...value, updatedAt: '2026-10-20T12:00:00Z' })],
    ['different correction operation', (value: EffectiveObservation) => ({ ...value, lastOperation: 'UNKNOWN' as const })],
  ])('rejects an effective observation with %s', async (_label, alter) => {
    const response = correctionResponse();
    const { api } = setup({
      ...response,
      effectiveObservations: {
        ...response.effectiveObservations,
        mood: alter(response.effectiveObservations.mood),
      },
    });
    await expect(api.correctionHistory(noteFixture.id)).rejects.toThrow('Invalid correction history response');
  });

  it.each([
    ['value', (value: EffectiveObservation) => ({ ...value, value: 'angry' })],
    ['state', (value: EffectiveObservation) => replaceMoodState(value, 'UNKNOWN')],
    ['rawValue', (value: EffectiveObservation) => ({ ...value, rawValue: 'unexpected' })],
  ])('rejects an untouched effective field with a different %s from the original', async (_label, alter) => {
    const response = correctionResponse();
    const { api } = setup({
      ...response,
      effectiveObservations: {
        ...response.effectiveObservations,
        appetite: alter(response.effectiveObservations.appetite),
      },
    });
    await expect(api.correctionHistory(noteFixture.id)).rejects.toThrow('Invalid correction history response');
  });

  it.each([0, 2])('rejects correction count %s when one event is present', async (correctionCount) => {
    const response = correctionResponse();
    const { api } = setup({
      ...response,
      originalNote: { ...response.originalNote, correctionCount },
    });
    await expect(api.correctionHistory(noteFixture.id)).rejects.toThrow('Invalid correction history response');
  });

  it('rejects a first event whose previous effective observation differs from the original', async () => {
    const response = correctionResponse();
    const [event] = response.events;
    const { api } = setup({
      ...response,
      events: [{ ...event, previousEffective: { ...event.previousEffective, value: 'angry' } }],
    });
    await expect(api.correctionHistory(noteFixture.id)).rejects.toThrow('Invalid correction history response');
  });

  it('rejects a later event whose previous effective state breaks replay continuity', async () => {
    const response = correctionResponse();
    const first = response.events[0];
    const secondId = 'f6666666-6666-4666-8666-666666666667';
    const second = {
      ...first,
      id: secondId,
      sequenceNo: 2,
      createdAt: '2026-10-19T12:00:00Z',
      value: 'relaxed',
      previousEffective: { ...first.effectiveAfter, value: 'angry' },
      effectiveAfter: effective('RECORDED', 'relaxed', 'SET', {
        eventId: secondId,
        actorId: first.actorId,
        updatedAt: '2026-10-19T12:00:00Z',
      }),
    };
    const { api } = setup({
      ...response,
      events: [first, second],
      originalNote: { ...response.originalNote, correctionCount: 2 },
      effectiveObservations: { ...response.effectiveObservations, mood: second.effectiveAfter },
    });
    await expect(api.correctionHistory(noteFixture.id)).rejects.toThrow('Invalid correction history response');
  });

  it('replays corrections on different fields in sequence and validates untouched fields independently', async () => {
    const response = correctionResponse();
    const first = response.events[0];
    const secondId = 'f6666666-6666-4666-8666-666666666667';
    const appetite = response.originalNote.observations.appetite;
    const second = {
      id: secondId,
      noteId: noteFixture.id,
      sequenceNo: 2,
      createdAt: '2026-10-19T12:00:00Z',
      actorId: first.actorId,
      field: 'appetite',
      operation: 'SET',
      value: 'hungry',
      previousEffective: effective(appetite.state, appetite.value ?? null),
      effectiveAfter: effective('RECORDED', 'hungry', 'SET', {
        eventId: secondId,
        actorId: first.actorId,
        updatedAt: '2026-10-19T12:00:00Z',
      }),
    };
    const { api } = setup({
      ...response,
      events: [first, second],
      originalNote: { ...response.originalNote, correctionCount: 2 },
      effectiveObservations: { ...response.effectiveObservations, appetite: second.effectiveAfter },
    });
    const history = await api.correctionHistory(noteFixture.id);
    expect(history.events.map((event) => event.field)).toEqual(['mood', 'appetite']);
    expect(history.effectiveObservations.sleep.value).toBe(noteFixture.observations.sleep.value);
  });

  it('rejects effective-value provenance that does not reference a correction event', async () => {
    const response = correctionResponse();
    const effectiveObservations = {
      ...response.effectiveObservations,
      mood: { ...response.effectiveObservations.mood, eventId: 'f6666666-6666-4666-8666-666666666667' },
    };
    const { api } = setup({ ...response, effectiveObservations });
    await expect(api.correctionHistory(noteFixture.id)).rejects.toThrow('Invalid correction history response');
  });

  it('preserves effective observation values, including false and zero, and event provenance', async () => {
    const response = correctionResponse();
    const moodEventId = response.events[0].id;
    const { api } = setup(response);
    const history = await api.correctionHistory(noteFixture.id);
    expect(history.effectiveObservations.painScore.value).toBe(0);
    expect(history.effectiveObservations.fallReported.value).toBe(false);
    expect(history.effectiveObservations.mood.eventId).toBe(moodEventId);
  });
  it('validates effective correction provenance for repeated changes to a field', async () => {
    const response = correctionResponse();
    const first = response.events[0];
    const second = {
      ...first,
      id: 'f6666666-6666-4666-8666-666666666667',
      sequenceNo: 2,
      createdAt: '2026-10-19T12:00:00Z',
      value: 'relaxed',
      previousEffective: first.effectiveAfter,
      effectiveAfter: effective('RECORDED', 'relaxed', 'SET', {
        eventId: 'f6666666-6666-4666-8666-666666666667',
        actorId: 'a1111111-1111-4111-8111-111111111111',
        updatedAt: '2026-10-19T12:00:00Z',
      }),
    };
    const { api } = setup({
      ...response,
      events: [first, second],
      originalNote: { ...response.originalNote, correctionCount: 2 },
      effectiveObservations: { ...response.effectiveObservations, mood: second.effectiveAfter },
    });
    const history = await api.correctionHistory(noteFixture.id);
    expect(history.events.map((event) => event.sequenceNo)).toEqual([1, 2]);
    expect(history.effectiveObservations.mood.eventId).toBe(second.id);
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
  it('preserves recorded empty text, false, and zero in create input', () => {
    const values = emptyNoteValues();
    values.observations = {
      ...values.observations,
      freeText: { state: 'RECORDED', value: '' },
      fallReported: { state: 'RECORDED', value: false },
      painScore: { state: 'RECORDED', value: 0 },
    };
    expect(createInput(values).observations).toEqual(values.observations);
  });
  it('maps errors to fixed messages rather than backend diagnostics', () => {
    for (const status of [403, 404]) expect(noteErrorMessage(new ApiError(status, { message: 'private' }))).toBe('notesUnavailable');
    expect(noteErrorMessage(new ApiError(409, { message: 'private' }))).toBe('notesConflict');
    expect(noteErrorMessage(new ApiError(400, null))).toBe('notesValidationError');
    expect(noteErrorMessage(new TypeError('private'))).toBe('notesNetworkError');
  });
});
