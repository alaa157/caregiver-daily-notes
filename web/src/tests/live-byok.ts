import type { Page } from '@playwright/test';

/**
 * Live suites exercise real provider calls through the BYOK routes, so they
 * need a real user-supplied key. Provide it via environment:
 *
 *   CAREGIVER_E2E_BYOK_PROVIDER=OPENAI|GEMINI|XAI|OPEN_ROUTER
 *   CAREGIVER_E2E_BYOK_MODEL=<model id>
 *   CAREGIVER_E2E_BYOK_KEY=<api key>
 *
 * The app must be built with `--mode e2e` for credential seeding to take
 * effect (see the `test:e2e:live` script).
 */
export interface LiveByokCredentials {
  providerId: 'OPENAI' | 'GEMINI' | 'XAI' | 'OPEN_ROUTER';
  model: string;
  apiKey: string;
}

const PROVIDER_IDS: readonly string[] = ['OPENAI', 'GEMINI', 'XAI', 'OPEN_ROUTER'];

export function liveByokCredentials(): LiveByokCredentials | null {
  const providerId = process.env.CAREGIVER_E2E_BYOK_PROVIDER;
  const model = process.env.CAREGIVER_E2E_BYOK_MODEL;
  const apiKey = process.env.CAREGIVER_E2E_BYOK_KEY;
  if (!providerId || !PROVIDER_IDS.includes(providerId) || !model || !apiKey) return null;
  return { providerId: providerId as LiveByokCredentials['providerId'], model, apiKey };
}

export async function seedByokCredentials(page: Page): Promise<void> {
  const credentials = liveByokCredentials();
  if (!credentials) return;
  await page.addInitScript((value) => {
    (window as unknown as { __E2E_BYOK_CREDENTIALS__?: unknown }).__E2E_BYOK_CREDENTIALS__ = value;
  }, credentials);
}
