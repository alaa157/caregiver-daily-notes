import { ApiError, type ApiClient } from '../lib/api';
import { isRecipientId } from './recipients';
import type { MessageKey } from '../i18n/translations';

export interface MedicationRecord {
  id: string;
  recipientId: string;
  displayName: string;
  doseText: string;
  scheduleText: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface MedicationInput {
  displayName: string;
  doseText: string;
  scheduleText: string;
}

export type MedicationFieldErrors = Partial<Record<keyof MedicationInput, MessageKey>>;

const isMedicationId = (value: unknown): value is string =>
  typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
const isTimestamp = (value: unknown): value is string =>
  typeof value === 'string' && value.length > 0 && Number.isFinite(Date.parse(value));

export function readMedication(value: unknown): MedicationRecord {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new Error('Invalid medication response.');
  }
  const item = value as Record<string, unknown>;
  if (!isMedicationId(item.id) || typeof item.recipientId !== 'string' || !isRecipientId(item.recipientId)
    || typeof item.displayName !== 'string' || typeof item.doseText !== 'string'
    || typeof item.scheduleText !== 'string' || typeof item.active !== 'boolean'
    || !isTimestamp(item.createdAt) || !isTimestamp(item.updatedAt)) {
    throw new Error('Invalid medication response.');
  }
  return {
    id: item.id,
    recipientId: item.recipientId as string,
    displayName: item.displayName,
    doseText: item.doseText,
    scheduleText: item.scheduleText,
    active: item.active,
    createdAt: item.createdAt,
    updatedAt: item.updatedAt,
  };
}

function readForRecipient(value: unknown, recipientId: string): MedicationRecord {
  const record = readMedication(value);
  if (record.recipientId.toLowerCase() !== recipientId.toLowerCase()) {
    throw new Error('Medication response belongs to a different recipient.');
  }
  return record;
}

export function validateMedication(input: MedicationInput): MedicationFieldErrors {
  const errors: MedicationFieldErrors = {};
  if (!input.displayName.trim()) errors.displayName = 'medicationNameRequired';
  else if (input.displayName.length > 160) errors.displayName = 'medicationNameTooLong';
  if (input.doseText.length === 0) errors.doseText = 'medicationDoseRequired';
  else if (input.doseText.length > 500) errors.doseText = 'medicationTextTooLong';
  if (input.scheduleText.length === 0) errors.scheduleText = 'medicationScheduleRequired';
  else if (input.scheduleText.length > 500) errors.scheduleText = 'medicationTextTooLong';
  return errors;
}

export function medicationErrorMessage(error: unknown): MessageKey {
  if (error instanceof ApiError) {
    if (error.status === 400 || error.status === 422) return 'medicationValidationError';
    if (error.status === 404 || error.status === 403) return 'medicationUnavailable';
    if (error.status === 409) return 'medicationConflict';
  }
  if (error instanceof TypeError) return 'medicationNetworkError';
  return 'medicationServiceError';
}

export function createMedicationsApi(client: ApiClient) {
  const recipientPath = (recipientId: string) => {
    if (!isRecipientId(recipientId)) throw new ApiError(404, null);
    return `/api/recipients/${encodeURIComponent(recipientId)}/medications` as const;
  };
  const medicationPath = (recipientId: string, medicationId: string) => {
    if (!isMedicationId(medicationId)) throw new ApiError(404, null);
    return `${recipientPath(recipientId)}/${encodeURIComponent(medicationId)}` as const;
  };
  const fields = (input: MedicationInput): MedicationInput => ({
    displayName: input.displayName,
    doseText: input.doseText,
    scheduleText: input.scheduleText,
  });
  const readOne = async (recipientId: string, path: `/api/recipients/${string}`,
    method: 'POST' | 'PUT' | 'PATCH', signal?: AbortSignal, body?: MedicationInput, expectedMedicationId?: string) => {
    const record = readForRecipient(await client.request<unknown>({ path, method, signal, body }), recipientId);
    if (expectedMedicationId && record.id.toLowerCase() !== expectedMedicationId.toLowerCase()) {
      throw new Error('Medication response has an unexpected ID.');
    }
    return record;
  };

  return {
    async list(recipientId: string, signal?: AbortSignal): Promise<MedicationRecord[]> {
      const response = await client.request<unknown>({ path: recipientPath(recipientId), signal });
      if (!Array.isArray(response)) throw new Error('Invalid medication list response.');
      return response.map((item) => readForRecipient(item, recipientId));
    },
    create: (recipientId: string, input: MedicationInput, signal?: AbortSignal) =>
      readOne(recipientId, recipientPath(recipientId), 'POST', signal, fields(input)),
    update: (recipientId: string, medicationId: string, input: MedicationInput, signal?: AbortSignal) =>
      readOne(recipientId, medicationPath(recipientId, medicationId), 'PUT', signal, fields(input), medicationId),
    archive: (recipientId: string, medicationId: string, signal?: AbortSignal) =>
      readOne(recipientId, `${medicationPath(recipientId, medicationId)}/archive`, 'PATCH', signal, undefined, medicationId),
  };
}
