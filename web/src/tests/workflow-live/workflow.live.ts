import { randomUUID } from 'node:crypto';
import { createRequire } from 'node:module';
import { expect, test, type Page } from '@playwright/test';
import { messages, type Language } from '../../i18n/translations';

const require = createRequire(import.meta.url);
const backend = new URL(process.env.CAREGIVER_TEST_API_URL || 'http://127.0.0.1:8080').origin;
const date = '2026-10-26';

async function quality(page: Page, language: Language) {
  await expect(page.locator('html')).toHaveAttribute('lang', language);
  await expect(page.locator('html')).toHaveAttribute('dir', language === 'ar' ? 'rtl' : 'ltr');
  await expect(page.locator('.footer-note')).toHaveText(messages[language].note);
  await page.evaluate(async () => {
    await document.fonts.ready;
    await Promise.all(document.getAnimations().map((animation) => animation.finished.catch(() => {})));
  });
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  await page.addScriptTag({ path: require.resolve('axe-core/axe.min.js') });
  const violations = await page.evaluate(async () => {
    const axe = (window as unknown as Window & { axe: typeof import('axe-core') }).axe;
    const result = await axe.run(document, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
    });
    return result.violations.map(({ id, nodes }) => ({ id, targets: nodes.map(({ target }) => target) }));
  });
  expect(violations).toEqual([]);
}

test.afterEach(async ({ page }) => { await page.unrouteAll({ behavior: 'wait' }); });

for (const initial of ['en', 'ar'] as const) {
  test(`complete caregiver journey from login to preserved plan history (${initial})`, async ({ page, request }) => {
    const password = `Synthetic-${randomUUID()}`;
    const email = `workflow-${randomUUID()}@example.com`;
    // Registration is fixture setup: the product intentionally has no signup UI.
    const registered = await request.post(`${backend}/api/auth/register`, { data: { email, password } });
    expect(registered.status()).toBe(201);
    const owner = { Authorization: `Bearer ${(await registered.json()).token}` };
    const name = 'مريم / Mary (Synthetic 123)';
    const originalText = '  تقرير عربي / English observation.\n<b>literal data</b>  ';
    const correction = 'تصحيح اليوم / English correction.';
    const pageErrors: string[] = [];
    const writes: string[] = [];
    page.on('pageerror', (error) => pageErrors.push(error.message));
    await page.addInitScript((locale) => localStorage.setItem('caregiver-language', locale), initial);
    // Forward unchanged traffic, including public OpenAPI discovery. No mocked
    // DTOs, login tokens, successful writes or safety results are supplied here.
    await page.route(/\/(?:api\/|v3\/api-docs(?:\?|$))/, async (route) => {
      const incoming = route.request();
      const url = new URL(incoming.url());
      if (incoming.method() !== 'GET') writes.push(`${incoming.method()} ${url.pathname}`);
      if (url.pathname === '/v3/api-docs' || url.pathname === '/api/auth/login') {
        expect(Boolean(incoming.headers().authorization)).toBe(false);
      } else {
        expect(Boolean(incoming.headers().authorization?.startsWith('Bearer '))).toBe(true);
      }
      await route.fulfill({ response: await route.fetch({ url: `${backend}${url.pathname}${url.search}`, maxRedirects: 0 }) });
    });

    let language: Language = initial;
    let t = messages[language];
    const navigation = () => page.getByRole('navigation', { name: t.mainNavigation, exact: true });
    async function switchLanguage() {
      await page.getByRole('button', { name: language === 'ar' ? 'Switch to English' : 'التغيير إلى العربية', exact: true }).click();
      language = language === 'ar' ? 'en' : 'ar';
      t = messages[language];
      await expect(page.locator('html')).toHaveAttribute('lang', language);
    }

    await page.goto('/app');
    await expect(page).toHaveURL(/\/login$/);
    await quality(page, language);
    await page.getByLabel(t.authEmail, { exact: true }).fill(email);
    await page.getByLabel(t.authPassword, { exact: true }).fill(password);
    await page.getByRole('button', { name: t.signIn, exact: true }).click();
    await expect(page.getByRole('heading', { name: t.dashboardTitle, exact: true })).toBeVisible();
    await navigation().getByRole('link', { name: t.recipientsTitle, exact: true }).click();
    await expect(page.getByRole('status')).toContainText(t.recipientsEmpty);
    await page.getByRole('link', { name: t.recipientCreate, exact: true }).click();
    await page.getByLabel(t.recipientName, { exact: true }).fill(name);
    await page.getByLabel(t.recipientRelationship, { exact: true }).fill('Family / الأسرة');
    await page.getByRole('button', { name: t.recipientCreate, exact: true }).click();
    await expect(page.locator('.recipient-success')).toHaveText(t.recipientCreated);
    const recipientId = new URL(page.url()).pathname.split('/').pop()!;
    await quality(page, language);
    await page.getByRole('link', { name: t.recipientEdit, exact: true }).click();
    await page.getByLabel(t.recipientRelationship, { exact: true }).fill('Caregiver / مقدّم الرعاية');
    await page.getByRole('button', { name: t.recipientSave, exact: true }).click();
    await expect(page.locator('.recipient-success')).toHaveText(t.recipientUpdated);
    await navigation().getByRole('link', { name: t.dashboard, exact: true }).click();
    await page.getByLabel(t.overviewDateLabel, { exact: true }).fill(date);
    await expect(page.getByText(t.overviewNoNote, { exact: true })).toBeVisible();
    await page.getByRole('link', { name: t.overviewRecordNote, exact: true }).click();
    await expect(page.getByLabel(t.notesRecipient, { exact: true })).toHaveValue(recipientId);
    await expect(page.getByLabel(t.notesDate, { exact: true })).toHaveValue(date);
    await page.getByLabel(`${t.notesFallReported} — ${t.obsStateLabel}`, { exact: true }).selectOption('RECORDED');
    await page.getByLabel(t.notesFallReported, { exact: true }).selectOption('true');
    await page.getByLabel(`${t.notesFreeText} — ${t.obsStateLabel}`, { exact: true }).selectOption('RECORDED');
    await page.getByRole('textbox', { name: t.notesFreeText, exact: true }).fill(originalText);
    await page.getByRole('button', { name: t.notesSave, exact: true }).click();
    await expect(page.getByRole('status')).toHaveText(t.notesSaved);
    const noteId = new URL(page.url()).pathname.split('/').pop()!;
    const original = () => page.getByRole('article', { name: t.notesImmutable, exact: true }).locator('.note-verbatim').last();
    await expect(original()).toHaveJSProperty('textContent', originalText);
    await page.getByRole('button', { name: t.notesAddStructuredCorrection, exact: true }).click();
    await page.locator('#correction-operation-mood').selectOption('SET');
    await page.locator('#correction-value-mood').fill('Calm / هادئ');
    await page.getByRole('button', { name: t.notesCorrectionSubmit, exact: true }).click();
    await expect(page.getByRole('status')).toHaveText(t.notesCorrectionSaved);
    await expect(page.locator('.structured-correction-event .note-verbatim').last()).toContainText('Calm / هادئ');
    await page.getByRole('link', { name: t.notesAppend, exact: true }).click();
    await page.getByRole('checkbox', { name: `${t.notesCorrect}: ${t.notesFreeText}`, exact: true }).check();
    await page.getByLabel(`${t.notesFreeText} — ${t.obsStateLabel}`, { exact: true }).selectOption('RECORDED');
    await page.getByRole('textbox', { name: t.notesFreeText, exact: true }).fill(correction);
    await page.getByRole('button', { name: t.notesSaveAppend, exact: true }).click();
    await expect(page.getByRole('status')).toHaveText(t.notesAppendSaved);
    await expect(original()).toHaveJSProperty('textContent', originalText);
    await quality(page, language);
    await switchLanguage();
    await expect(original()).toHaveJSProperty('textContent', originalText);

    await navigation().getByRole('link', { name: t.notesTitle, exact: true }).click();
    await page.getByLabel(t.notesRecipient, { exact: true }).selectOption(recipientId);
    await page.getByLabel(t.historyFrom, { exact: true }).fill('2026-10-20');
    await page.getByLabel(t.historyTo, { exact: true }).fill(date);
    await page.getByRole('button', { name: t.historyApply, exact: true }).click();
    const card = page.getByRole('article', { name, exact: true });
    await expect(card).toBeVisible();
    await card.getByRole('button', { name: new RegExp(`^${t.historyShowCorrections}`) }).click();
    await expect(card.locator('.history-correction .note-verbatim').last()).toHaveJSProperty('textContent', correction);
    await quality(page, language);

    await navigation().getByRole('link', { name: t.summaryTitle, exact: true }).click();
    await page.getByLabel(t.summaryRecipient, { exact: true }).selectOption(recipientId);
    await page.getByLabel(t.summaryEndDate, { exact: true }).fill(date);
    await page.getByRole('button', { name: t.summaryGenerate, exact: true }).click();
    // The provider fixture quotes the original's first line. Corrections remain
    // separately linked evidence rather than rewriting the saved original.
    await expect(page.locator('blockquote')).toHaveText(originalText.split('\n')[0].trim());
    await expect(page.locator('.summary-evidence a')).toHaveAttribute('href', `/app/notes/${noteId}`);
    await expect(page.locator('.summary-safety')).toContainText('HIGH');
    await expect(page.getByRole('heading', { name: t.summaryClinicalReview, exact: true })).toBeVisible();
    await quality(page, language);
    await page.locator('.summary-evidence a').click();
    await expect(original()).toHaveJSProperty('textContent', originalText);
    await switchLanguage();

    await navigation().getByRole('link', { name: t.plansTitle, exact: true }).click();
    await page.getByLabel(t.plansRecipient, { exact: true }).selectOption(recipientId);
    await page.getByLabel(t.summaryEndDate, { exact: true }).fill(date);
    await page.getByRole('button', { name: t.plansGenerate, exact: true }).click();
    const suggested = page.getByRole('region', { name: t.plansSuggested, exact: true });
    await expect(suggested.getByRole('button', { name: t.plansAccept, exact: true })).toBeEnabled();
    await expect(page.locator('.plan-generation-safety .summary-safety')).toContainText('HIGH');
    await suggested.getByRole('button', { name: t.plansAccept, exact: true }).click();
    await page.getByRole('button', { name: t.plansConfirmAccept, exact: true }).click();
    const active = () => page.getByRole('region', { name: t.plansActive, exact: true });
    await expect(active().locator('.plan-status')).toHaveText(t.plansStatusAccepted);
    await quality(page, language);
    await page.reload();
    await expect(active().locator('.plan-status')).toHaveText(t.plansStatusAccepted);

    // Deactivation preserves the note/addendum and the accepted plan.
    await navigation().getByRole('link', { name: t.recipientsTitle, exact: true }).click();
    await page.getByRole('link', { name, exact: true }).click();
    await page.getByRole('button', { name: t.recipientDeactivate, exact: true }).click();
    await page.getByRole('button', { name: t.recipientConfirmDeactivate, exact: true }).click();
    await expect(page.locator('.recipient-success')).toHaveText(t.recipientDeactivated);
    const note = await request.get(`${backend}/api/notes/${noteId}`, { headers: owner });
    expect(note.status()).toBe(200);
    const savedNote = await note.json();
    expect(savedNote.freeText).toBe(originalText);
    expect(savedNote.addenda.map((item: { freeText: string }) => item.freeText)).toEqual([correction]);
    const plans = await request.get(`${backend}/api/recipients/${recipientId}/plans/history`, { headers: owner });
    expect(plans.status()).toBe(200);
    expect((await plans.json()).map((plan: { status: string }) => plan.status)).toEqual(['ACCEPTED']);
    expect(writes.filter((path) => path === 'POST /api/notes')).toHaveLength(1);
    expect(writes.filter((path) => path === `POST /api/notes/${noteId}/corrections`)).toHaveLength(1);
    expect(writes.filter((path) => path === `POST /api/notes/${noteId}/addenda`)).toHaveLength(1);
    const storage = await page.evaluate(() => JSON.stringify({ local: { ...localStorage }, session: { ...sessionStorage } }));
    expect([name, originalText, correction, password].some((value) => storage.includes(value))).toBe(false);
    await navigation().getByRole('button', { name: t.authSignOut, exact: true }).click();
    await page.goto('/app/notes/plans');
    await expect(page).toHaveURL(/\/login$/);
    expect(pageErrors).toEqual([]);
  });
}
