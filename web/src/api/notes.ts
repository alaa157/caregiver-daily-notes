import { ApiError, type ApiClient } from '../lib/api';
import { isRecipientId } from './recipients';
import type { MessageKey } from '../i18n/translations';
import {
  fromLegacyValue,
  isObservation,
  isVerificationValue,
  structuredObservationFields,
  type NoteObservations,
  type ObservationSet,
  type StructuredObservationField,
  type VerificationValue,
} from './observation';

export const observationFields = structuredObservationFields;
export type ObservationField = StructuredObservationField;
export interface LegacyObservations {
  mood: string | null;
  appetite: string | null;
  sleep: string | null;
  mobility: string | null;
  medicationStatus: 'TAKEN' | 'MISSED' | 'UNKNOWN' | null;
  medicationVerified: boolean | null;
  painScore: number | null;
  fallReported: boolean | null;
  freeText: string | null;
}
export interface DailyNote extends LegacyObservations {
  id: string; recipientId: string; date: string; addendumCount: number; createdAt: string;
  observations: NoteObservations;
}
export interface NoteAddendum extends LegacyObservations {
  id: string; noteId: string; createdAt: string; observations: NoteObservations;
  providedFields: StructuredObservationField[];
  medicationVerificationProvided: boolean;
}
export interface CreateNoteInput {
  recipientId: string;
  date: string;
  observations: ObservationSet;
  medicationVerification: VerificationValue;
}
export interface AddendumInput {
  observations: Partial<ObservationSet>;
  medicationVerification?: VerificationValue;
}
export interface NotesHistoryQuery {
  from: string; to: string; recipientId?: string; page?: number; size?: number;
}
export interface NotesHistoryPage {
  content: DailyNote[]; number: number; size: number;
  totalElements: number; totalPages: number; first: boolean; last: boolean;
}

export function isNoteDate(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(`${value}T00:00:00Z`))
    && new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value;
}

function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid note response.');
  return value as Record<string, unknown>;
}
function readLegacyObservations(item: Record<string, unknown>): LegacyObservations {
  const result = {} as LegacyObservations;
  for (const key of [...observationFields, 'medicationVerified'] as const) {
    const value = item[key];
    const valid = value === null || (key === 'painScore'
      ? typeof value === 'number' && Number.isInteger(value) && value >= 0 && value <= 10
      : key === 'medicationVerified' || key === 'fallReported'
        ? typeof value === 'boolean'
        : key === 'medicationStatus' ? ['TAKEN', 'MISSED', 'UNKNOWN'].includes(value as string)
          : typeof value === 'string' && value.length <= (key === 'freeText' ? 20000 : 40));
    if (!valid) throw new Error('Invalid note response.');
    Object.assign(result, { [key]: value });
  }
  return result;
}
function readStructuredObservations(item: Record<string, unknown>, legacy: LegacyObservations): NoteObservations {
  const observations = item.observations;
  if (observations === undefined) {
    const medicationStatus = legacy.medicationStatus === 'UNKNOWN'
      ? { state: 'UNKNOWN' as const }
      : fromLegacyValue(legacy.medicationStatus);
    return {
      mood: fromLegacyValue(legacy.mood),
      appetite: fromLegacyValue(legacy.appetite),
      sleep: fromLegacyValue(legacy.sleep),
      mobility: fromLegacyValue(legacy.mobility),
      medicationStatus,
      painScore: fromLegacyValue(legacy.painScore),
      fallReported: fromLegacyValue(legacy.fallReported),
      freeText: fromLegacyValue(legacy.freeText),
      medicationVerification: legacy.medicationVerified === true ? 'VERIFIED'
        : legacy.medicationVerified === false ? 'UNVERIFIED' : 'NOT_ASSERTED',
    };
  }
  if (!observations || typeof observations !== 'object' || Array.isArray(observations)) {
    throw new Error('Invalid note response.');
  }
  const fields = observations as Record<string, unknown>;
  for (const field of observationFields) {
    if (!isObservation(fields[field], field)) throw new Error('Invalid note response.');
  }
  if (!isVerificationValue(fields.medicationVerification)) throw new Error('Invalid note response.');
  return fields as unknown as NoteObservations;
}
function readProvidedFields(value: unknown, legacy: LegacyObservations): StructuredObservationField[] {
  if (value === undefined) {
    return observationFields.filter((field) => legacy[field] !== null);
  }
  if (!Array.isArray(value) || value.some((field) =>
    typeof field !== 'string' || !(observationFields as readonly string[]).includes(field))) {
    throw new Error('Invalid note response.');
  }
  const fields = value as StructuredObservationField[];
  if (new Set(fields).size !== fields.length) throw new Error('Invalid note response.');
  return fields;
}
function readIdentity(item: Record<string, unknown>) {
  if (typeof item.id !== 'string' || !isRecipientId(item.id)
    || typeof item.createdAt !== 'string' || !Number.isFinite(Date.parse(item.createdAt))) throw new Error('Invalid note response.');
  return { id: item.id, createdAt: item.createdAt };
}
function readNote(value: unknown): DailyNote {
  const item = object(value);
  if (typeof item.recipientId !== 'string' || !isRecipientId(item.recipientId)
    || typeof item.date !== 'string' || !isNoteDate(item.date)
    || typeof item.addendumCount !== 'number' || !Number.isSafeInteger(item.addendumCount) || item.addendumCount < 0) throw new Error('Invalid note response.');
  const legacy = readLegacyObservations(item);
  return {
    ...readIdentity(item), ...legacy, recipientId: item.recipientId, date: item.date,
    addendumCount: item.addendumCount, observations: readStructuredObservations(item, legacy),
  };
}
function readAddendum(value: unknown, noteId: string): NoteAddendum {
  const item = object(value);
  if (item.noteId !== noteId) throw new Error('Invalid note response.');
  const legacy = readLegacyObservations(item);
  const medicationVerificationProvided = item.medicationVerificationProvided === undefined
    ? legacy.medicationVerified !== null
    : item.medicationVerificationProvided;
  if (typeof medicationVerificationProvided !== 'boolean') throw new Error('Invalid note response.');
  return {
    ...readIdentity(item), ...legacy, noteId,
    observations: readStructuredObservations(item, legacy),
    providedFields: readProvidedFields(item.providedFields, legacy),
    medicationVerificationProvided,
  };
}
const notePath = (id: string): `/api/notes/${string}` => {
  if (!isRecipientId(id)) throw new ApiError(404, null);
  return `/api/notes/${encodeURIComponent(id)}`;
};
function editableFields(input: AddendumInput): AddendumInput {
  const observations: Partial<ObservationSet> = {};
  for (const key of observationFields) {
    const observation = input.observations?.[key];
    if (observation !== undefined) {
      if (!isObservation(observation, key)) throw new Error('Invalid observation input.');
      Object.assign(observations, { [key]: observation });
    }
  }
  if (input.medicationVerification !== undefined && !isVerificationValue(input.medicationVerification)) {
    throw new Error('Invalid verification input.');
  }
  return {
    observations,
    ...(input.medicationVerification === undefined ? {} : { medicationVerification: input.medicationVerification }),
  };
}
function writeHeaders(key: string) {
  if (!key || key.length > 100 || !/^[\w-]+$/.test(key)) throw new Error('Invalid save key.');
  return { 'Idempotency-Key': key };
}
export function createNotesApi(client: ApiClient) {
  return {
    async history({ from, to, recipientId, page = 0, size = 20 }: NotesHistoryQuery, signal?: AbortSignal): Promise<NotesHistoryPage> {
      if (!isNoteDate(from) || !isNoteDate(to) || from > to
        || (recipientId !== undefined && !isRecipientId(recipientId))
        || !Number.isSafeInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 100) throw new ApiError(400, null);
      const query = new URLSearchParams({ from, to, page: String(page), size: String(size) });
      if (recipientId) query.set('recipientId', recipientId);
      const item = object(await client.request({ path: `/api/notes?${query}`, signal }));
      if (!Array.isArray(item.content) || item.number !== page || item.size !== size
        || typeof item.totalElements !== 'number' || !Number.isSafeInteger(item.totalElements) || item.totalElements < 0
        || item.totalPages !== Math.ceil(item.totalElements / size)
        || item.first !== (page === 0) || item.last !== (page >= (item.totalPages as number) - 1)
        || item.content.length > size) throw new Error('Invalid history response.');
      const content = item.content.map(readNote);
      if (new Set(content.map((note) => note.id)).size !== content.length
        || content.some((note) => note.date < from || note.date > to || (recipientId && note.recipientId !== recipientId))
        || (content.length > 0 && page * size + content.length > item.totalElements)) throw new Error('Invalid history response.');
      return { content, number: page, size, totalElements: item.totalElements,
        totalPages: item.totalPages as number, first: item.first as boolean, last: item.last as boolean };
    },
    async get(id: string, signal?: AbortSignal) {
      const result = readNote(await client.request({ path: notePath(id), signal }));
      if (result.id !== id) throw new Error('Invalid note response.');
      return result;
    },
    async find(recipientId: string, date: string, signal?: AbortSignal) {
      if (!isRecipientId(recipientId) || !isNoteDate(date)) throw new ApiError(400, null);
      const result = readNote(await client.request({ path: `/api/notes/by-recipient/${recipientId}?date=${encodeURIComponent(date)}`, signal }));
      if (result.recipientId !== recipientId || result.date !== date) throw new Error('Invalid note response.');
      return result;
    },
    async create(input: CreateNoteInput, key: string, signal?: AbortSignal) {
      if (!isRecipientId(input.recipientId) || !isNoteDate(input.date)) throw new ApiError(400, null);
      for (const field of observationFields) {
        if (!isObservation(input.observations?.[field], field)) throw new Error('Invalid observation input.');
      }
      if (!isVerificationValue(input.medicationVerification)) throw new Error('Invalid verification input.');
      const result = readNote(await client.request({ path: '/api/notes', method: 'POST', signal,
        headers: writeHeaders(key), body: {
          recipientId: input.recipientId, date: input.date,
          observations: input.observations, medicationVerification: input.medicationVerification,
        } }));
      if (result.recipientId !== input.recipientId || result.date !== input.date) throw new Error('Invalid note response.');
      return result;
    },
    async addenda(id: string, signal?: AbortSignal) {
      const result = await client.request<unknown>({ path: `${notePath(id)}/addenda`, signal });
      if (!Array.isArray(result)) throw new Error('Invalid note response.');
      return result.map((value) => readAddendum(value, id));
    },
    async append(id: string, input: AddendumInput, key: string, signal?: AbortSignal) {
      return readAddendum(await client.request({ path: `${notePath(id)}/addenda`, method: 'POST', signal,
        headers: writeHeaders(key), body: editableFields(input) }), id);
    },
  };
}

export function noteErrorMessage(error: unknown): MessageKey {
  if (error instanceof ApiError) {
    if (error.status === 403 || error.status === 404) return 'notesUnavailable';
    if (error.status === 400 || error.status === 422) return 'notesValidationError';
    if (error.status === 409) return 'notesConflict';
    if (error.status === 429) return 'notesRateLimit';
  }
  return error instanceof TypeError ? 'notesNetworkError' : 'notesServiceError';
}
/** A lost response, malformed success or server error may follow a committed write. */
export function uncertainNoteSave(error: unknown) {
  return !(error instanceof ApiError && [400, 401, 403, 404, 409, 422, 429].includes(error.status));
}
