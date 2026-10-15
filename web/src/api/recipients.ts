import { ApiError, type ApiClient } from '../lib/api';
import type { MessageKey } from '../i18n/translations';

/** Mirrors RecipientResponse; ownership is intentionally absent. */
export interface Recipient {
  id: string;
  name: string;
  relationship: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface RecipientInput {
  name: string;
  relationship: string | null;
}

export const isRecipientId = (id: string) => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id);

function readRecipient(value: unknown): Recipient {
  if (typeof value !== 'object' || value === null) throw new Error('Invalid recipient response.');
  const item = value as Record<string, unknown>;
  if (typeof item.id !== 'string' || !isRecipientId(item.id)
    || typeof item.name !== 'string' || !item.name.trim()
    || (item.relationship !== null && typeof item.relationship !== 'string')
    || typeof item.active !== 'boolean'
    || typeof item.createdAt !== 'string' || !Number.isFinite(Date.parse(item.createdAt))
    || typeof item.updatedAt !== 'string' || !Number.isFinite(Date.parse(item.updatedAt))) {
    throw new Error('Invalid recipient response.');
  }
  // Pick safe fields even if a future response contains additional properties.
  return { id: item.id, name: item.name, relationship: item.relationship, active: item.active,
    createdAt: item.createdAt, updatedAt: item.updatedAt };
}

const fields = (input: RecipientInput): RecipientInput => ({
  name: input.name.trim(), relationship: input.relationship?.trim() || null,
});

export function validateRecipient(input: RecipientInput): Partial<Record<keyof RecipientInput, MessageKey>> {
  const value = fields(input);
  const errors: Partial<Record<keyof RecipientInput, MessageKey>> = {};
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
    async list(signal?: AbortSignal): Promise<Recipient[]> {
      const response = await client.request<unknown>({ path: '/api/recipients', signal });
      if (!Array.isArray(response)) throw new Error('Invalid recipient list response.');
      return response.map(readRecipient);
    },
    get: async (id: string, signal?: AbortSignal) => single(pathFor(id), 'GET', signal),
    create: (input: RecipientInput, signal?: AbortSignal) => single('/api/recipients', 'POST', signal, fields(input)),
    update: (id: string, input: RecipientInput, signal?: AbortSignal) => single(pathFor(id), 'PUT', signal, fields(input)),
    deactivate: (id: string, signal?: AbortSignal) => single(`${pathFor(id)}/deactivate`, 'PATCH', signal),
  };
}
