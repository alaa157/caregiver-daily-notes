import { ApiError, type ApiClient } from '../lib/api';
import type { MessageKey } from '../i18n/translations';

export type RecipientLifecycleStatus = 'ACTIVE' | 'ARCHIVED' | 'LEGACY_INACTIVE_REVIEW';
export type RecipientListFilter = RecipientLifecycleStatus | 'ALL';

export interface Recipient {
  id: string; name: string; relationship: string | null; active: boolean;
  lifecycleStatus: RecipientLifecycleStatus;
  archivedAt: string | null; archivedBy: string | null;
  restoredAt: string | null; restoredBy: string | null;
  createdAt: string; updatedAt: string;
}
export interface RecipientInput { name: string; relationship: string | null; }
export const isRecipientId = (id: string) => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id);
const isInstantOrNull = (v: unknown): v is string | null => v === null || (typeof v === 'string' && Number.isFinite(Date.parse(v)));
const isIdOrNull = (v: unknown): v is string | null => v === null || (typeof v === 'string' && isRecipientId(v));

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
    restoredAt: item.restoredAt, restoredBy: item.restoredBy };
}
function readRecipient(value: unknown): Recipient {
  const core = readRecipientCore(value);
  const { createdAt, updatedAt } = value as Record<string, unknown>;
  if (typeof createdAt !== 'string' || !Number.isFinite(Date.parse(createdAt))
    || typeof updatedAt !== 'string' || !Number.isFinite(Date.parse(updatedAt))) throw new Error('Invalid recipient response.');
  return { ...core, createdAt, updatedAt };
}
const fields = (input: RecipientInput): RecipientInput => ({ name: input.name.trim(), relationship: input.relationship?.trim() || null });
export function validateRecipient(input: RecipientInput): Partial<Record<keyof RecipientInput, MessageKey>> {
  const value = fields(input); const errors: Partial<Record<keyof RecipientInput, MessageKey>> = {};
  if (!value.name) errors.name = 'recipientNameRequired';
  else if (value.name.length > 120) errors.name = 'recipientNameTooLong';
  if (value.relationship && value.relationship.length > 80) errors.relationship = 'recipientRelationshipTooLong';
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
    create: async (input: RecipientInput, signal?: AbortSignal) => readRecipientCore(await client.request<unknown>({
      path: '/api/recipients', method: 'POST', signal, body: fields(input),
    })),
    update: (id: string, input: RecipientInput, signal?: AbortSignal) => single(pathFor(id), 'PUT', signal, fields(input)),
    archive: (id: string, signal?: AbortSignal) => single(`${pathFor(id)}/archive`, 'PATCH', signal),
    restore: (id: string, signal?: AbortSignal) => single(`${pathFor(id)}/restore`, 'PATCH', signal),
  };
}
