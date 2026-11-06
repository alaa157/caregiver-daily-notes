import { expect, test, type Page, type Route } from '@playwright/test';
import { testToken } from '../src/tests/authFixtures';
import { noteDetailFixture, noteFixture, noteListFixture } from '../src/tests/noteFixtures';
import { recipientFixture } from '../src/tests/recipientFixtures';
import { summaryFixture } from '../src/tests/summaryFixtures';
import { protectedActiveFixture, protectedSuggestionFixture } from '../src/tests/planFixtures';

const SYNTHETIC_KEY = 'TEST-BYOK-DO-NOT-USE';
const RECIPIENT_ID = '11111111-1111-4111-8111-111111111111';
const NOTE_ID = noteFixture.id;
const NOTE_DATE = '2026-10-18';
const PERIOD_END = '2026-10-24';
const PERIOD_DAYS = 7;
const DISCLAIMER = 'Educational project. Not medical advice.';
const RECIPIENT_NAME = 'Demo Recipient';

interface RecordedRequest {
  method: string;
  path: string;
  body: Record<string, unknown> | null;
}

interface MockOptions {
  aiUnavailable?: boolean;
}

function recipientRecord() {
  return {
    ...recipientFixture, id: RECIPIENT_ID, name: RECIPIENT_NAME, relationship: 'Parent',
    active: true, lifecycleStatus: 'ACTIVE', archivedAt: null, archivedBy: null,
    restoredAt: null, restoredBy: null,
  };
}

function planActive() {
  return { ...protectedActiveFixture, recipientId: RECIPIENT_ID };
}

function planSuggestion() {
  const structured = {
    ...protectedSuggestionFixture.structuredContent!,
    changes: protectedSuggestionFixture.structuredContent!.changes.map((change) => ({
      ...change, evidenceNoteIds: [NOTE_ID],
    })),
  };
  return {
    ...protectedSuggestionFixture, recipientId: RECIPIENT_ID, evidenceNoteIds: NOTE_ID,
    structuredContent: structured, content: JSON.stringify(structured),
  };
}

function summaryResponse(aiUnavailable: boolean) {
  const base = summaryFixture();
  if (aiUnavailable) {
    return {
      summary: { observations: [], uncertainties: [], aiUnavailable: true },
      safety: base.safety, trends: [],
    };
  }
  return {
    ...base,
    summary: {
      ...base.summary, aiUnavailable: false,
      observations: base.summary.observations.map((observation) => ({ ...observation, noteId: NOTE_ID })),
    },
  };
}

function correctionHistory(note: typeof noteFixture) {
  return {
    originalNote: note,
    events: [],
    effectiveObservations: Object.fromEntries(
      (['mood', 'appetite', 'sleep', 'mobility', 'medicationStatus', 'painScore', 'fallReported', 'freeText'] as const)
        .map((field) => {
          const observation = note.observations[field];
          return [field, {
            state: observation.state,
            value: observation.state === 'RECORDED' ? observation.value ?? null : null,
            rawValue: null, lastOperation: null, eventId: null, actorId: null, updatedAt: note.createdAt,
          }];
        }),
    ),
  };
}

async function installApiMocks(page: Page, options: MockOptions = {}) {
  const unmocked: string[] = [];
  const requests: RecordedRequest[] = [];
  let recipient: ReturnType<typeof recipientRecord> | null = null;
  let note: typeof noteFixture | null = null;
  let activePlan: ReturnType<typeof planActive> | null = planActive();
  let historyPlans: Array<ReturnType<typeof planActive> | ReturnType<typeof planSuggestion>> = [planActive()];
  let generated = false;

  const handler = async (route: Route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const method = request.method();
    let body: Record<string, unknown> | null = null;
    try { body = request.postDataJSON() as Record<string, unknown>; } catch { body = null; }
    requests.push({ method, path, body });

    const fulfill = (json: unknown, status = 200) => route.fulfill({ status, json });

    if (path === '/api/auth/login' && method === 'POST') return fulfill({ token: testToken() });

    if (path === '/api/recipients') {
      if (method === 'GET') return fulfill(recipient ? [recipient] : []);
      if (method === 'POST') {
        recipient = {
          ...recipientRecord(),
          name: typeof body?.name === 'string' ? body.name : RECIPIENT_NAME,
          relationship: typeof body?.relationship === 'string' ? body.relationship : 'Parent',
        };
        return fulfill(recipient, 201);
      }
    }
    if (path === `/api/recipients/${RECIPIENT_ID}` && method === 'GET' && recipient) return fulfill(recipient);
    if (path === `/api/recipients/${RECIPIENT_ID}/medications` && method === 'GET') return fulfill([]);
    if (path === `/api/recipients/${RECIPIENT_ID}/safety-acknowledgements` && method === 'GET') return fulfill([]);

    if (path === '/api/notes' && method === 'POST') {
      const observations = body?.observations as typeof noteFixture.observations;
      note = {
        ...noteFixture, id: NOTE_ID, recipientId: RECIPIENT_ID,
        date: typeof body?.date === 'string' ? body.date : NOTE_DATE,
        observations: { ...noteFixture.observations, ...observations },
      } as typeof noteFixture;
      return fulfill({ ...noteListFixture, id: NOTE_ID, recipientId: RECIPIENT_ID, date: note.date,
        observations: note.observations }, 201);
    }
    if (path === '/api/notes' && method === 'GET') {
      const from = url.searchParams.get('from') ?? '';
      const to = url.searchParams.get('to') ?? '';
      const pageNumber = Number(url.searchParams.get('page') ?? '0');
      const size = Number(url.searchParams.get('size') ?? '20');
      const inRange = note !== null && note.date >= from && note.date <= to
        && (!url.searchParams.get('recipientId') || url.searchParams.get('recipientId') === RECIPIENT_ID);
      const content = inRange ? [{ ...noteListFixture, id: NOTE_ID, recipientId: RECIPIENT_ID, date: note!.date }] : [];
      const totalElements = content.length;
      const totalPages = Math.ceil(totalElements / size);
      return fulfill({ content, number: pageNumber, size, totalElements, totalPages,
        first: pageNumber === 0, last: pageNumber >= totalPages - 1 });
    }
    if (path === `/api/notes/${NOTE_ID}` && method === 'GET' && note) {
      return fulfill({ ...noteDetailFixture, id: NOTE_ID, recipientId: RECIPIENT_ID, date: note.date,
        observations: note.observations, addenda: [] });
    }
    if (path === `/api/notes/${NOTE_ID}/addenda` && method === 'GET') return fulfill([]);
    if (path === `/api/notes/${NOTE_ID}/corrections` && method === 'GET' && note) {
      return fulfill(correctionHistory(note));
    }
    if (path === `/api/notes/by-recipient/${RECIPIENT_ID}` && method === 'GET') {
      if (note && url.searchParams.get('date') === note.date) {
        return fulfill({ ...noteDetailFixture, id: NOTE_ID, recipientId: RECIPIENT_ID, date: note.date,
          observations: note.observations, addenda: [] });
      }
      return fulfill({ code: 'NOT_FOUND' }, 404);
    }

    if (path === '/api/ai/byok/connection-test' && method === 'POST') return fulfill({ success: true });
    if (path === '/api/summaries/byok' && method === 'POST') return fulfill(summaryResponse(!!options.aiUnavailable));
    if (path === '/api/plans/generate/byok' && method === 'POST') {
      const suggestion = planSuggestion();
      if (!generated) { historyPlans = [suggestion, ...historyPlans]; generated = true; }
      return fulfill({ plan: suggestion, aiUnavailable: false, safety: summaryFixture().safety, invalidPlan: false });
    }
    if (path === `/api/recipients/${RECIPIENT_ID}/plans/active` && method === 'GET') {
      return activePlan ? fulfill(activePlan) : fulfill({ code: 'NOT_FOUND' }, 404);
    }
    if (path === `/api/recipients/${RECIPIENT_ID}/plans/history` && method === 'GET') return fulfill(historyPlans);
    if (path === `/api/recipients/${RECIPIENT_ID}/plans/${planSuggestion().id}/accept` && method === 'POST') {
      const suggestion = planSuggestion();
      const accepted = { ...suggestion, status: 'ACCEPTED', editable: false };
      const archivedActive = activePlan ? { ...activePlan, status: 'ARCHIVED' } : null;
      activePlan = accepted;
      historyPlans = [accepted, ...(archivedActive ? [archivedActive] : [])];
      return fulfill(accepted);
    }

    unmocked.push(`${method} ${path}`);
    return fulfill({ code: 'UNMOCKED_TEST_REQUEST' }, 501);
  };

  await page.route('**/api/**', handler);
  return { unmocked, requests };
}

async function signIn(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Email address', { exact: true }).fill('synthetic@example.com');
  await page.getByLabel('Password', { exact: true }).fill('synthetic password');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Caregiver workspace');
}

async function createRecipient(page: Page) {
  await page.goto('/app/recipients/new');
  await page.getByLabel('Name', { exact: true }).fill(RECIPIENT_NAME);
  await page.getByLabel(/Relationship/, { exact: true }).fill('Parent');
  await page.getByRole('form', { name: 'Add care recipient' })
    .getByRole('button', { name: 'Add care recipient', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/app/recipients/${RECIPIENT_ID}$`));
  await expect(page.getByRole('heading', { name: RECIPIENT_NAME })).toBeVisible();
}

async function chooseState(page: Page, fieldLabel: string, stateLabel: string) {
  await page.getByRole('group', { name: `${fieldLabel} — Observation status`, exact: true })
    .getByText(stateLabel, { exact: true }).click();
}

async function fillMixedNote(page: Page) {
  await page.goto('/app/notes/new');
  await page.getByLabel('Care recipient', { exact: true }).selectOption(RECIPIENT_ID);
  await page.getByLabel('Observation date', { exact: true }).fill(NOTE_DATE);
  // RECORDED zero (Pain) and RECORDED false (Fall).
  await chooseState(page, 'Pain score (0–10)', 'Recorded');
  await page.getByLabel('Pain score (0–10)', { exact: true }).fill('0');
  await chooseState(page, 'Fall reported', 'Recorded');
  await page.getByLabel('Fall reported', { exact: true }).selectOption('false');
  // UNKNOWN medication state, NOT_RECORDED sleep, remaining fields NOT_RECORDED.
  await chooseState(page, 'Medication taken', 'Unknown');
  for (const label of ['Mood', 'Appetite', 'Sleep', 'Mobility', 'Free-text observations']) {
    await chooseState(page, label, 'Not recorded');
  }
  await page.getByRole('button', { name: 'Save original note', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/app/notes/${NOTE_ID}$`));
}

async function enterSyntheticKey(page: Page) {
  await page.goto('/app/settings');
  await page.getByRole('button', { name: 'AI Settings', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'AI provider settings' });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel('Model', { exact: true }).fill('synthetic-model');
  await dialog.getByLabel('API key', { exact: true }).fill(SYNTHETIC_KEY);
  await dialog.getByRole('button', { name: 'Test connection', exact: true }).click();
  await expect(dialog.getByRole('status')).toContainText('Connection test succeeded.');
  await expect(page.locator('body')).not.toContainText(SYNTHETIC_KEY);
  await dialog.getByRole('button', { name: 'Close', exact: true }).click();
  await expect(dialog).toBeHidden();
}

async function generateSummary(page: Page) {
  await page.getByRole('link', { name: 'AI summary & safety', exact: true }).click();
  await expect(page).toHaveURL(/\/app\/notes\/summary$/);
  await page.getByLabel('Care recipient', { exact: true }).selectOption(RECIPIENT_ID);
  await page.getByLabel('End date (inclusive)', { exact: true }).fill(PERIOD_END);
  await page.getByRole('button', { name: 'Generate summary', exact: true }).click();
}

async function generatePlan(page: Page) {
  await page.getByRole('navigation', { name: 'Main navigation' })
    .getByRole('link', { name: 'Care plans', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/app/notes/plans`));
  await page.getByLabel('Care recipient', { exact: true }).selectOption(RECIPIENT_ID);
  await expect(page.getByRole('region', { name: 'Suggested plans' })).toBeVisible();
  await page.getByLabel('End date (inclusive)', { exact: true }).fill(PERIOD_END);
  await page.getByRole('button', { name: 'Request plan suggestion', exact: true }).click();
  await expect(page.getByText('A new AI suggestion was saved for your review.')).toBeVisible();
}

test('completes sign in note summary and accepted plan journey', async ({ page }) => {
  test.setTimeout(120_000);
  const { unmocked, requests } = await installApiMocks(page);
  await signIn(page);
  await createRecipient(page);
  expect(requests.some((item) => item.method === 'POST' && item.path === '/api/recipients')).toBe(true);

  await fillMixedNote(page);
  const notePost = requests.find((item) => item.method === 'POST' && item.path === '/api/notes');
  expect(notePost).toBeDefined();
  const payload = notePost!.body as { observations: Record<string, { state: string; value: unknown }> };
  expect(payload.observations.painScore).toEqual({ state: 'RECORDED', value: 0 });
  expect((payload.observations.painScore.value as number)).toBe(0);
  expect(payload.observations.fallReported).toEqual({ state: 'RECORDED', value: false });
  expect((payload.observations.fallReported.value as boolean)).toBe(false);
  expect(payload.observations.medicationStatus).toEqual({ state: 'UNKNOWN', value: null });
  expect(payload.observations.sleep).toEqual({ state: 'NOT_RECORDED', value: null });

  // Note detail: note ID in URL survives reload.
  await expect(page.getByRole('heading', { name: 'Daily note details' })).toBeVisible();
  await page.reload();
  await expect(page).toHaveURL(new RegExp(`/app/notes/${NOTE_ID}$`));
  await expect(page.getByText(NOTE_ID, { exact: true }).first()).toBeVisible();

  // History: recipient + date range in URL survives reload and direct navigation.
  const historyUrl = `/app/notes/history?recipientId=${RECIPIENT_ID}&from=${NOTE_DATE}&to=${NOTE_DATE}`;
  await page.goto(historyUrl);
  expect(page.url()).toContain(`recipientId=${RECIPIENT_ID}`);
  expect(page.url()).toContain(`from=${NOTE_DATE}`);
  expect(page.url()).toContain(`to=${NOTE_DATE}`);
  await expect(page.locator(`a[href="/app/notes/${NOTE_ID}"]`).first()).toBeVisible();
  await page.reload();
  expect(page.url()).toContain(`recipientId=${RECIPIENT_ID}`);
  await page.goto(historyUrl);
  await expect(page.locator(`a[href="/app/notes/${NOTE_ID}"]`).first()).toBeVisible();

  await enterSyntheticKey(page);
  const connectionTest = requests.find((item) => item.path === '/api/ai/byok/connection-test');
  expect(connectionTest?.body).toMatchObject({ credentials: { apiKey: SYNTHETIC_KEY } });

  // Client-side navigation to the dashboard (keeps in-memory BYOK credentials),
  // then to Summary via the workflow link.
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Home', exact: true }).click();
  await generateSummary(page);
  await expect(page.getByRole('heading', { name: 'Requested summary', exact: true })).toBeVisible();
  await expect(page.getByText('AI-generated', { exact: true })).toBeVisible();
  await expect(page.getByText(DISCLAIMER, { exact: true })).toBeVisible();
  await expect(page.locator(`a[href="/app/notes/${NOTE_ID}"]`).first()).toBeVisible();
  const aiSection = page.locator('.summary-ai');
  const safetySection = page.locator('.summary-safety');
  await expect(safetySection).toBeVisible();
  await expect(safetySection.getByRole('heading', { name: /Clinical review requested|Safety information/ })).toBeVisible();
  expect(await aiSection.evaluate((element) => element.contains(element.ownerDocument.querySelector('.summary-safety')))).toBe(false);
  expect(await safetySection.evaluate((element) => element.contains(element.ownerDocument.querySelector('.summary-ai')))).toBe(false);
  const summaryPost = requests.find((item) => item.path === '/api/summaries/byok');
  expect(summaryPost?.body).toMatchObject({ credentials: { apiKey: SYNTHETIC_KEY } });
  expect(summaryPost?.body).toMatchObject({ summary: { recipientId: RECIPIENT_ID, periodEndInclusive: PERIOD_END, periodDays: PERIOD_DAYS } });

  await generatePlan(page);
  const suggested = page.getByRole('region', { name: 'Suggested plans' });
  await expect(suggested.getByText('Suggested — not accepted')).toBeVisible();
  await expect(suggested.getByText('AI-generated', { exact: true })).toBeVisible();
  const acceptPath = `/api/recipients/${RECIPIENT_ID}/plans/${planSuggestion().id}/accept`;
  expect(requests.filter((item) => item.path === acceptPath)).toHaveLength(0);

  await suggested.getByRole('button', { name: 'Accept suggestion', exact: true }).click();
  await expect(page.getByRole('group', { name: 'Confirm acceptance' })).toBeVisible();
  expect(requests.filter((item) => item.path === acceptPath)).toHaveLength(0);
  await page.getByRole('button', { name: 'Confirm acceptance', exact: true }).click();
  await expect(page.getByText('The server accepted the suggestion.')).toBeVisible();
  expect(requests.filter((item) => item.path === acceptPath)).toHaveLength(1);

  const active = page.getByRole('region', { name: 'Active accepted plan' });
  await expect(active.getByText('Suggested — not accepted')).toHaveCount(0);
  await expect(active.getByText('Accepted', { exact: true })).toBeVisible();
  const history = page.getByRole('region', { name: 'Plan history' });
  await expect(history.getByText('Archived', { exact: true }).first()).toBeVisible();
  expect(unmocked).toEqual([]);
});

test('preserves unknown and recorded zero and false values through note submission', async ({ page }) => {
  test.setTimeout(120_000);
  const { unmocked, requests } = await installApiMocks(page);
  await signIn(page);
  await createRecipient(page);
  await fillMixedNote(page);
  const notePost = requests.find((item) => item.method === 'POST' && item.path === '/api/notes');
  expect(notePost).toBeDefined();
  const payload = notePost!.body as {
    recipientId: string; date: string;
    observations: Record<string, { state: string; value: unknown }>;
    medicationVerification: string;
  };
  expect(payload.recipientId).toBe(RECIPIENT_ID);
  expect(payload.date).toBe(NOTE_DATE);
  expect(payload.observations.painScore).toEqual({ state: 'RECORDED', value: 0 });
  expect((payload.observations.painScore.value as number)).toBe(0);
  expect(payload.observations.fallReported).toEqual({ state: 'RECORDED', value: false });
  expect((payload.observations.fallReported.value as boolean)).toBe(false);
  expect(payload.observations.medicationStatus).toEqual({ state: 'UNKNOWN', value: null });
  expect(payload.observations.sleep).toEqual({ state: 'NOT_RECORDED', value: null });
  expect(payload.observations.mood).toEqual({ state: 'NOT_RECORDED', value: null });
  expect(payload.medicationVerification).toBeTruthy();
  expect(unmocked).toEqual([]);
});

test('does not persist synthetic BYOK key in browser storage', async ({ page }) => {
  test.setTimeout(120_000);
  const { unmocked } = await installApiMocks(page);
  await signIn(page);
  await createRecipient(page);
  await fillMixedNote(page);
  await enterSyntheticKey(page);
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Home', exact: true }).click();
  await generateSummary(page);
  await generatePlan(page);
  await page.getByRole('region', { name: 'Suggested plans' })
    .getByRole('button', { name: 'Accept suggestion', exact: true }).click();
  await page.getByRole('button', { name: 'Confirm acceptance', exact: true }).click();
  await expect(page.getByText('The server accepted the suggestion.')).toBeVisible();

  const scan = await page.evaluate(async () => {
    const chunks: string[] = [];
    chunks.push(JSON.stringify(Object.entries(localStorage)));
    chunks.push(JSON.stringify(Object.entries(sessionStorage)));
    const databases = await indexedDB.databases();
    for (const database of databases) {
      if (!database.name) continue;
      const records = await new Promise<unknown[]>((resolve, reject) => {
        const open = indexedDB.open(database.name!);
        open.onsuccess = () => {
          const db = open.result;
          const stores = Array.from(db.objectStoreNames);
          Promise.all(stores.map((store) => new Promise<unknown>((done, failInner) => {
            const request = db.transaction(store).objectStore(store).getAll();
            request.onsuccess = () => done(request.result);
            request.onerror = () => failInner(request.error);
          }))).then((values) => { db.close(); resolve(values); }, (error: unknown) => reject(error));
        };
        open.onerror = () => reject(open.error);
      });
      chunks.push(JSON.stringify(records));
    }
    return { blob: chunks.join('\n'), localKeys: Object.keys(localStorage), sessionKeys: Object.keys(sessionStorage), databaseNames: databases.map((item) => item.name) };
  });
  expect(scan.blob).not.toContain(SYNTHETIC_KEY);
  // Prove the scan saw persisted state: language + auth session keys exist.
  expect(scan.localKeys).toContain('caregiver-language');
  expect(scan.sessionKeys).toContain('caregiver-auth-session-v1');
  expect(unmocked).toEqual([]);
});

test('keeps provider failure out of successful summary and plan generation', async ({ page }) => {
  test.setTimeout(120_000);
  const { unmocked, requests } = await installApiMocks(page, { aiUnavailable: true });
  await signIn(page);
  await createRecipient(page);
  await fillMixedNote(page);
  await enterSyntheticKey(page);
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Home', exact: true }).click();
  await generateSummary(page);
  await expect(page.getByText('AI is unavailable. No generated observations are shown.')).toBeVisible();
  await expect(page.getByText('AI-generated', { exact: true })).toHaveCount(0);
  await expect(page.locator('.summary-ai')).toHaveCount(0);
  await expect(page.locator('.summary-observations')).toHaveCount(0);
  expect(requests.filter((item) => item.path === '/api/plans/generate/byok')).toHaveLength(0);
  expect(requests.filter((item) => item.path.includes('/plans/') && item.path.endsWith('/accept'))).toHaveLength(0);
  expect(unmocked).toEqual([]);
});

test('does not auto accept a generated plan', async ({ page }) => {
  test.setTimeout(120_000);
  const { unmocked, requests } = await installApiMocks(page);
  await signIn(page);
  await createRecipient(page);
  await fillMixedNote(page);
  await enterSyntheticKey(page);
  await page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Home', exact: true }).click();
  await generateSummary(page);
  await generatePlan(page);
  const suggested = page.getByRole('region', { name: 'Suggested plans' });
  await expect(suggested.getByText('Suggested — not accepted')).toBeVisible();
  const acceptPath = `/api/recipients/${RECIPIENT_ID}/plans/${planSuggestion().id}/accept`;
  expect(requests.filter((item) => item.path === acceptPath)).toHaveLength(0);
  await suggested.getByRole('button', { name: 'Accept suggestion', exact: true }).click();
  await expect(page.getByRole('group', { name: 'Confirm acceptance' })).toBeVisible();
  expect(requests.filter((item) => item.path === acceptPath)).toHaveLength(0);
  await page.getByRole('button', { name: 'Confirm acceptance', exact: true }).click();
  await expect(page.getByText('The server accepted the suggestion.')).toBeVisible();
  expect(requests.filter((item) => item.path === acceptPath)).toHaveLength(1);
  expect(unmocked).toEqual([]);
});
