import type { ByokProviderId } from './ByokContext';
import { MODEL_ID_PATTERN } from './modelCatalog';

export interface AiPreferences {
  providerId: ByokProviderId;
  model: string;
}

const STORAGE_KEY = 'caregiver-ai-preferences-v1';
const PROVIDER_IDS: readonly ByokProviderId[] = ['OPENAI', 'GEMINI', 'XAI', 'OPEN_ROUTER'];

export function loadAiPreferences(): AiPreferences | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed: unknown = JSON.parse(raw);
    if (typeof parsed !== 'object' || parsed === null) return null;
    const { providerId, model } = parsed as { providerId?: unknown; model?: unknown };
    if (typeof providerId !== 'string' || !PROVIDER_IDS.includes(providerId as ByokProviderId)) return null;
    if (typeof model !== 'string' || !MODEL_ID_PATTERN.test(model)) return null;
    return { providerId: providerId as ByokProviderId, model };
  } catch {
    return null;
  }
}

export function saveAiPreferences(preferences: AiPreferences): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ providerId: preferences.providerId, model: preferences.model }));
  } catch {
    // Preferences are best-effort; connection-test success must not depend on storage.
  }
}

export function clearAiPreferences(): void {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Ignore storage failures.
  }
}
