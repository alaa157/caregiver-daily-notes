import { ApiError, type ApiClient } from '../lib/api';
import type { MessageKey } from '../i18n/translations';

export type RecipientLifecycleStatus = 'ACTIVE' | 'ARCHIVED' | 'LEGACY_INACTIVE_REVIEW';
export type RecipientListFilter = RecipientLifecycleStatus | 'ALL';
export type RecipientReviewResolution = 'ARCHIVED' | 'ACTIVE';

export interface Recipient {
  id: string; name: string; relationship: string | null; active: boolean;
  lifecycleStatus: RecipientLifecycleStatus;
  archivedAt: string | null; archivedBy: string | null;
  restoredAt: string | null; restoredBy: string | null;
  createdAt: string; updatedAt: string;
  dateOfBirth?: string | null;
  allergies?: string | null;
  conditions?: string | null;
  emergencyContactName?: string | null;
  emergencyContactPhone?: string | null;
}
export interface RecipientInput { name: string; relationship: string | null; }
export interface RecipientCareDetailsInput {
  dateOfBirth: string | null;
  allergies: string | null;
  conditions: string | null;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
}
export const isRecipientId = (id: string) => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id);
const isInstantOrNull = (v: unknown): v is string | null => v === null || (typeof v === 'string' && Number.isFinite(Date.parse(v)));
const isIdOrNull = (v: unknown): v is string | null => v === null || (typeof v === 'string' && isRecipientId(v));
const isDateOnly = (value: string) => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00.000Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
};
const optionalCareString = (value: unknown, maxLength: number): string | null | undefined => {
  if (value === undefined || value === null) return value;
  if (typeof value !== 'string' || value.length > maxLength) throw new Error('Invalid recipient response.');
  return value;
};

function readRecipientCareDetails(item: Record<string, unknown>): Partial<Pick<Recipient,
  'dateOfBirth' | 'allergies' | 'conditions' | 'emergencyContactName' | 'emergencyContactPhone'>> {
  const careDetails: Partial<Pick<Recipient,
    'dateOfBirth' | 'allergies' | 'conditions' | 'emergencyContactName' | 'emergencyContactPhone'>> = {};
  const dateOfBirth = item.dateOfBirth;
  if (dateOfBirth !== undefined) {
    if (dateOfBirth !== null && (typeof dateOfBirth !== 'string' || !isDateOnly(dateOfBirth))) {
      throw new Error('Invalid recipient response.');
    }
    careDetails.dateOfBirth = dateOfBirth;
  }
  const allergies = optionalCareString(item.allergies, 500);
  const conditions = optionalCareString(item.conditions, 500);
  const emergencyContactName = optionalCareString(item.emergencyContactName, 120);
  const emergencyContactPhone = optionalCareString(item.emergencyContactPhone, 30);
  if (allergies !== undefined) careDetails.allergies = allergies;
  if (conditions !== undefined) careDetails.conditions = conditions;
  if (emergencyContactName !== undefined) careDetails.emergencyContactName = emergencyContactName;
  if (emergencyContactPhone !== undefined) careDetails.emergencyContactPhone = emergencyContactPhone;
  return careDetails;
}

function readRecipientCore(value: unknown): Pick<Recipient, 'id' | 'name' | 'relationship' | 'active' | 'lifecycleStatus' | 'archivedAt' | 'archivedBy' | 'restoredAt' | 'restoredBy'> {
  if (typeof value !== 'object' || value === null) throw new Error('Invalid recipient response.');
  const item = value as Record<string, unknown>;
  const status = item.lifecycleStatus;
  if (typeof item.id !== 'string' || !isRecipientId(item.id)
    || typeof item.name !== 'string' || !item.name.trim()
    || (item.relationship !== null && typeof item.relationship !== 'string')
    || typeof item.active !== 'boolean'
    || !(['ACTIVE', 'ARCHIVED', 'LEGACY_INACTIVE_REVIEW'] as unknown[]).includes(status)
    || (status === 'ACTIVE') !== item.active
    || !isInstantOrNull(item.archivedAt) || !isIdOrNull(item.archivedBy)
    || !isInstantOrNull(item.restoredAt) || !isIdOrNull(item.restoredBy)
    || (item.archivedAt === null) !== (item.archivedBy === null)
    || (item.restoredAt === null) !== (item.restoredBy === null)
    || (status === 'ARCHIVED' && item.archivedAt === null)
    || (status === 'LEGACY_INACTIVE_REVIEW' && (item.archivedAt !== null || item.restoredAt !== null))) {
    throw new Error('Invalid recipient response.');
  }
  return { id: item.id, name: item.name, relationship: item.relationship, active: item.active,
    lifecycleStatus: status as RecipientLifecycleStatus, archivedAt: item.archivedAt, archivedBy: item.archivedBy,
    restoredAt: item.restoredAt, restoredBy: item.restoredBy, ...readRecipientCareDetails(item) };
}
function readRecipient(value: unknown): Recipient {
  const core = readRecipientCore(value);
  const { createdAt, updatedAt } = value as Record<string, unknown>;
  if (typeof createdAt !== 'string' || !Number.isFinite(Date.parse(createdAt))
    || typeof updatedAt !== 'string' || !Number.isFinite(Date.parse(updatedAt))) throw new Error('Invalid recipient response.');
  return { ...core, createdAt, updatedAt };
}
const fields = (input: RecipientInput): RecipientInput => ({ name: input.name.trim(), relationship: input.relationship?.trim() || null });
const careDetailsFields = (input: RecipientCareDetailsInput): RecipientCareDetailsInput => ({
  dateOfBirth: input.dateOfBirth?.trim() || null,
  allergies: input.allergies?.trim() || null,
  conditions: input.conditions?.trim() || null,
  emergencyContactName: input.emergencyContactName?.trim() || null,
  emergencyContactPhone: input.emergencyContactPhone?.trim() || null,
});
export function validateRecipient(input: RecipientInput): Partial<Record<keyof RecipientInput, MessageKey>> {
  const value = fields(input); const errors: Partial<Record<keyof RecipientInput, MessageKey>> = {};
  if (!value.name) errors.name = 'recipientNameRequired';
  else if (value.name.length > 120) errors.name = 'recipientNameTooLong';
  if (value.relationship && value.relationship.length > 80) errors.relationship = 'recipientRelationshipTooLong';
  return errors;
}
/** Mirrors RecipientCareDetailsRequest.MIN_PHONE_DIGITS. */
const MIN_PHONE_DIGITS = 3;
export function validateCareDetails(input: RecipientCareDetailsInput): Partial<Record<keyof RecipientCareDetailsInput, MessageKey>> {
  const value = careDetailsFields(input); const errors: Partial<Record<keyof RecipientCareDetailsInput, MessageKey>> = {};
  if (value.dateOfBirth && !isDateOnly(value.dateOfBirth)) errors.dateOfBirth = 'historyInvalidDate';
  if (value.allergies && value.allergies.length > 500) errors.allergies = 'medicationTextTooLong';
  if (value.conditions && value.conditions.length > 500) errors.conditions = 'medicationTextTooLong';
  if (value.emergencyContactName && value.emergencyContactName.length > 120) errors.emergencyContactName = 'recipientNameTooLong';
  // Mirrors RecipientCareDetailsRequest: permitted separators plus at least three digits.
  if (value.emergencyContactPhone && (!/^[0-9+() -]{3,30}$/.test(value.emergencyContactPhone)
    || value.emergencyContactPhone.replace(/\D/g, '').length < MIN_PHONE_DIGITS)) {
    errors.emergencyContactPhone = 'recipientValidationError';
  }
  return errors;
}
export function recipientErrorMessage(error: unknown): MessageKey {
  if (error instanceof ApiError) {
    if (error.status === 404 || error.status === 403) return 'recipientUnavailable';
    if (error.status === 409) return 'recipientConflict';
    if (error.status === 400 || error.status === 422) return 'recipientValidationError';
    if (error.status === 429) return 'recipientRateLimited';
  }
  if (error instanceof TypeError) return 'recipientNetworkError';
  return 'recipientServiceError';
}
function idempotencyHeaders(key?: string) {
  if (key === undefined) return undefined;
  if (!key || key.length > 100 || !/^[\w-]+$/.test(key)) throw new Error('Invalid save key.');
  return { 'Idempotency-Key': key };
}
export function createRecipientsApi(client: ApiClient) {
  const pathFor = (id: string): `/api/recipients/${string}` => {
    if (!isRecipientId(id)) throw new ApiError(404, null);
    return `/api/recipients/${encodeURIComponent(id)}`;
  };
  const single = async (path: `/api/recipients${string}`, method: 'GET' | 'POST' | 'PUT' | 'PATCH',
    signal?: AbortSignal, body?: RecipientInput) => readRecipient(await client.request<unknown>({ path, method, signal, body }));
  return {
    async list(signal?: AbortSignal, filter: RecipientListFilter = 'ALL'): Promise<Recipient[]> {
      const path: `/api/recipients${string}` = filter === 'ALL' ? '/api/recipients' : `/api/recipients?status=${encodeURIComponent(filter)}`;
      const response = await client.request<unknown>({ path, signal });
      if (!Array.isArray(response)) throw new Error('Invalid recipient list response.');
      return response.map(readRecipient);
    },
    get: async (id: string, signal?: AbortSignal) => single(pathFor(id), 'GET', signal),
    create: async (input: RecipientInput, key?: string, signal?: AbortSignal) => readRecipientCore(await client.request<unknown>({
      path: '/api/recipients', method: 'POST', signal, headers: idempotencyHeaders(key), body: fields(input),
    })),
    update: (id: string, input: RecipientInput, signal?: AbortSignal) => single(pathFor(id), 'PUT', signal, fields(input)),
    updateCareDetails: async (id: string, input: RecipientCareDetailsInput, signal?: AbortSignal) =>
      readRecipient(await client.request<unknown>({
        path: `${pathFor(id)}/care-details`, method: 'PATCH', signal, body: careDetailsFields(input),
      })),
    archive: (id: string, signal?: AbortSignal) => single(`${pathFor(id)}/archive`, 'PATCH', signal),
    restore: (id: string, signal?: AbortSignal) => single(`${pathFor(id)}/restore`, 'PATCH', signal),
    resolveReview: async (id: string, resolution: RecipientReviewResolution, signal?: AbortSignal) =>
      readRecipient(await client.request<unknown>({
        path: `${pathFor(id)}/resolve-review`, method: 'PATCH', signal, body: { resolution },
      })),
  };
}
