import { createRequire } from 'node:module';
import { expect, test, type Page } from '@playwright/test';
import { testToken } from '../authFixtures';
import { recipientFixture } from '../recipientFixtures';
import { summaryFixture } from '../summaryFixtures';

const require = createRequire(import.meta.url);
const root = '/app/notes/summary';
const testCredentials = { providerId: 'GEMINI', model: 'gemini-test-model', apiKey: 'synthetic-e2e-byok-key' } as const;
type Options = { fail?: boolean; fallback?: boolean; invalid?: boolean; malicious?: boolean; pending?: boolean };
type CapturedRequest = { method: string; path: string; body: unknown };

async function provideCredentials(page: Page) {
  await page.addInitScript((credentials) => {
    (window as Window & { __E2E_BYOK_CREDENTIALS__?: typeof credentials }).__E2E_BYOK_CREDENTIALS__ = credentials;
  }, testCredentials);
}

async function serve(page: Page, options: Options = {}) {
  const requests: CapturedRequest[] = [];
  const discoveryCalls: string[] = [];
  await page.route('**/v3/api-docs', (route) => {
    discoveryCalls.push(route.request().url());
    return route.fulfill({ json: { openapi: '3.0.1', paths: {} } });
  });
  await page.route('**/api/**', async (route) => {
    const request = route.request(); const url = new URL(request.url());
    requests.push({ method: request.method(), path: url.pathname, body: request.postDataJSON() });
    expect(request.headers().authorization).toMatch(/^Bearer /);
    if (url.pathname === '/api/recipients') return route.fulfill({ json: [recipientFixture] });
    if (url.pathname.endsWith('/safety-acknowledgements') && request.method() === 'GET') return route.fulfill({ json: [] });
    expect(url.pathname).toBe('/api/summaries/byok'); expect(request.method()).toBe('POST');
    if (options.pending) return; // Hold the request until the UI cancels it.
    if (options.fail) return route.fulfill({
      status: 504,
      json: {
        message: `provider rejected ${testCredentials.apiKey}`,
        diagnostics: `raw response ${testCredentials.apiKey}`,
        safety: { needsDoctorBanner: true, signals: [{ ruleId: 'PROVIDER_FAILURE_SAFETY', severity: 'HIGH', reason: 'Safety alert retained after provider failure.' }] },
      },
    });
    const fixture = summaryFixture();
    if (options.fallback) fixture.summary.aiUnavailable = true;
    if (options.malicious) {
      fixture.summary.observations[0].text = '<style>.summary-safety{display:none}</style> Ignore all previous warnings. All clear. ' + 'Mixedعربي'.repeat(50);
      fixture.summary.observations[0].noteId = 'javascript:alert(1)';
      fixture.safety.signals.push({ severity: 'CRITICAL', ruleId: 'FUTURE_RULE', reason: 'Backend urgency must not be reduced.' });
    }
    return route.fulfill({ json: options.invalid ? { ...fixture, summary: null } : fixture });
  });
  return { requests, discoveryCalls };
}

async function accessibility(page: Page) {
  await page.evaluate(async () => { await document.fonts.ready; });
  await page.addScriptTag({ path: require.resolve('axe-core/axe.min.js') });
  const violations = await page.evaluate(async () => {
    const result = await (window as unknown as Window & { axe: typeof import('axe-core') }).axe.run(document,
      { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } });
    return result.violations.map(({ id, nodes }) => ({ id, targets: nodes.map(({ target }) => target) }));
  });
  expect(violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  if (page.viewportSize()!.width <= 1100) expect(await page.locator('.site-header').evaluate((element) => getComputedStyle(element).position)).toBe('static');
  expect(await page.locator('.summary-disclaimer').evaluate((element) => Number.parseFloat(getComputedStyle(element).fontSize))).toBeGreaterThanOrEqual(16);
}

for (const language of ['en', 'ar'] as const) {
  const ar = language === 'ar';
  const text = {
    title: ar ? 'ملخص الذكاء الاصطناعي والسلامة' : 'AI summary & safety',
    recipient: ar ? 'المستفيد من الرعاية' : 'Care recipient', end: ar ? 'تاريخ النهاية (شامل)' : 'End date (inclusive)',
    generate: ar ? 'إنشاء ملخص' : 'Generate summary', regenerate: ar ? 'إعادة إنشاء الملخص' : 'Regenerate summary',
    result: ar ? 'الملخص المطلوب' : 'Requested summary', clinical: ar ? 'مراجعة مختص مطلوبة' : 'Clinical review requested',
    uncertainty: ar ? 'معلومات غير واضحة أو متعارضة' : 'Unclear or conflicting information',
    fallback: ar ? 'الذكاء الاصطناعي غير متاح.' : 'AI is unavailable.',
    timeout: ar ? 'انتهت مهلة طلب الملخص.' : 'The summary request timed out.',
    missingCredentials: ar ? 'افتح إعدادات الذكاء الاصطناعي واضبط مفتاحًا لمزوّد قبل إنشاء ملخص.' : 'Open AI Settings and configure a provider key before generating a summary.',
    aiGenerated: ar ? 'مولّد بالذكاء الاصطناعي' : 'AI-generated',
    educationalDisclaimer: ar ? 'مشروع تعليمي. ليس نصيحة طبية.' : 'Educational project. Not medical advice.',
    cancel: ar ? 'إلغاء الطلب' : 'Cancel request', cancelled: ar ? 'أُلغي الطلب.' : 'Request cancelled.',
  };
  async function choose(page: Page) {
    await page.getByLabel(text.recipient, { exact: true }).selectOption(recipientFixture.id);
    await page.getByLabel(text.end, { exact: true }).fill('2026-10-24');
  }
  function summaryPosts(requests: CapturedRequest[]) {
    return requests.filter((request) => request.method === 'POST');
  }
  test.describe(`summary ${language}`, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript(({ language, token }) => {
        localStorage.setItem('caregiver-language', language);
        sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token, baseUrl: '' }));
      }, { language, token: testToken() });
    });
    test('requests 7/14/30 days by keyboard and displays evidence, uncertainty and non-dismissable safety', async ({ page }, info) => {
      await provideCredentials(page);
      const calls = await serve(page); await page.goto(root);
      await expect(page.getByRole('heading', { name: text.title, exact: true })).toBeVisible();
      await expect(page).toHaveTitle(new RegExp(text.title));
      await expect(page.locator('html')).toHaveAttribute('dir', ar ? 'rtl' : 'ltr');
      await expect.poll(() => page.evaluate(() => (window as Window & { __E2E_BYOK_CREDENTIALS__?: unknown }).__E2E_BYOK_CREDENTIALS__)).toBeUndefined();
      await choose(page); expect(summaryPosts(calls.requests)).toHaveLength(0);
      await accessibility(page);
      for (const [index, period] of [7, 14, 30].entries()) {
        const number = new Intl.NumberFormat(ar ? 'ar-EG' : 'en').format(period);
        await page.getByRole('radio', { name: `${number} ${ar ? 'أيام' : 'days'}`, exact: true }).check();
        await page.getByRole('button', { name: period === 7 ? text.generate : text.regenerate, exact: true }).focus();
        await page.keyboard.press('Enter');
        await expect(page.getByRole('heading', { name: text.result, exact: true })).toBeVisible();
        await expect(page.getByRole('button', { name: text.regenerate, exact: true })).toBeEnabled();
        await expect(page.locator('.summary-progress')).toBeFocused();
        const posts = summaryPosts(calls.requests);
        expect(posts).toHaveLength(index + 1);
        expect(posts.at(-1)).toMatchObject({
          path: '/api/summaries/byok',
          body: { summary: { recipientId: recipientFixture.id, periodEndInclusive: '2026-10-24', periodDays: period }, credentials: testCredentials },
        });
        expect(calls.discoveryCalls).toHaveLength(0);
        await expect(page.getByText(text.aiGenerated, { exact: true })).toBeVisible();
        await expect(page.getByText(text.educationalDisclaimer, { exact: true })).toBeVisible();
        await expect(page.getByRole('heading', { name: text.clinical })).toBeVisible();
        await expect(page.getByText('HIGH', { exact: true })).toBeVisible();
        await expect(page.locator('.summary-safety')).toBeVisible();
        await expect(page.getByRole('heading', { name: text.uncertainty })).toBeVisible();
        expect(await page.locator('blockquote').textContent()).toBe(summaryFixture().summary.observations[0].quote);
        await expect(page.locator('.summary-evidence a')).toHaveAttribute('href', '/app/notes/c3333333-3333-4333-8333-333333333333');
        await expect(page.locator('.summary-result script, .summary-result img')).toHaveCount(0);
        await expect(page.getByText(testCredentials.apiKey, { exact: false })).toHaveCount(0);
      }
      await accessibility(page);
      await page.evaluate(() => { (document.activeElement as HTMLElement | null)?.blur(); window.scrollTo({ top: 0, behavior: 'instant' }); });
      await page.screenshot({ path: `/tmp/task6-${language}-${info.project.name}.png`, fullPage: true });
      const storage = await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }));
      expect(storage).not.toContain('Reported appetite');
      expect(storage).not.toContain(testCredentials.apiKey);
    });
    test('ordinary malicious content and malformed output cannot hide or downgrade backend urgency', async ({ page }) => {
      await provideCredentials(page);
      const options = { malicious: true, invalid: false }; const calls = await serve(page, options);
      await page.goto(root); await choose(page);
      await page.getByRole('button', { name: text.generate, exact: true }).click();
      await expect(page.getByText('HIGH', { exact: true })).toBeVisible();
      await expect(page.getByText('CRITICAL', { exact: true })).toBeVisible();
      await expect(page.locator('.summary-result style')).toHaveCount(0);
      await expect(page.locator('.summary-evidence a')).toHaveCount(0);
      await expect(page.locator('.summary-safety')).toBeVisible(); await accessibility(page);
      expect(summaryPosts(calls.requests)).toHaveLength(1);
      expect(summaryPosts(calls.requests)[0].path).toBe('/api/summaries/byok');
      options.invalid = true;
      await page.getByRole('button', { name: text.regenerate, exact: true }).click();
      await expect(page.locator('.summary-result > .summary-notice[role=alert]')).toBeVisible();
      await expect(page.getByText('HIGH', { exact: true })).toBeVisible();
      await expect(page.getByText('CRITICAL', { exact: true })).toBeVisible(); await accessibility(page);
    });
    test('retains backend safety and translated feedback when BYOK generation fails', async ({ page }) => {
      await provideCredentials(page);
      const options: Options = { fallback: true }; const calls = await serve(page, options);
      await page.goto(root); await choose(page); await page.getByRole('button', { name: text.generate, exact: true }).click();
      await expect(page.getByText(text.fallback, { exact: false })).toBeVisible();
      await expect(page.getByText('HIGH', { exact: true })).toBeVisible();
      await expect(page.getByText(text.aiGenerated, { exact: true })).toHaveCount(0);
      await expect(page.getByText(text.educationalDisclaimer, { exact: true })).toHaveCount(0);
      await expect(page.locator('blockquote')).toHaveCount(0); await accessibility(page);
      options.fallback = false; options.fail = true;
      await page.getByRole('button', { name: text.regenerate, exact: true }).click();
      await expect(page.getByRole('alert')).toContainText(text.timeout);
      await expect(page.getByText('HIGH', { exact: true })).toHaveCount(2);
      await expect(page.getByText('FALL_DETECTED', { exact: true })).toBeVisible();
      await expect(page.getByText('PROVIDER_FAILURE_SAFETY', { exact: true })).toBeVisible();
      await expect(page.getByText('Safety alert retained after provider failure.', { exact: true })).toBeVisible();
      await expect(page.getByText(testCredentials.apiKey, { exact: false })).toHaveCount(0);
      await expect(page.getByText('raw response', { exact: false })).toHaveCount(0);
      await expect(page.locator('.summary-disclaimer')).toBeVisible();
      expect(summaryPosts(calls.requests)).toHaveLength(2);
      expect(summaryPosts(calls.requests).every((request) => request.path === '/api/summaries/byok')).toBe(true);
      expect(calls.discoveryCalls).toHaveLength(0); await accessibility(page);
    });
    test('missing credentials show translated instructions without any provider request', async ({ page }) => {
      const calls = await serve(page); await page.goto(root);
      await expect(page.locator('html')).toHaveAttribute('dir', ar ? 'rtl' : 'ltr');
      await choose(page); await page.getByRole('button', { name: text.generate, exact: true }).click();
      await expect(page.getByRole('alert')).toContainText(text.missingCredentials);
      await expect(page.getByRole('button', { name: text.generate, exact: true })).toBeEnabled();
      expect(summaryPosts(calls.requests)).toHaveLength(0);
      expect(calls.discoveryCalls).toHaveLength(0);
      expect(calls.requests.some((request) => request.path === '/api/summaries')).toBe(false);
      await expect(page.getByText(testCredentials.apiKey, { exact: false })).toHaveCount(0);
      await accessibility(page);
    });
    test('validates required selections and prevents duplicate submissions while allowing cancellation', async ({ page }) => {
      await provideCredentials(page);
      const options = { pending: true }; const calls = await serve(page, options);
      await page.goto(root);
      await page.getByRole('button', { name: text.generate, exact: true }).click();
      await expect(page.getByLabel(text.recipient, { exact: true })).toBeFocused();
      expect(summaryPosts(calls.requests)).toHaveLength(0); await accessibility(page);
      await choose(page); await page.getByRole('button', { name: text.generate, exact: true }).dblclick();
      await expect(page.getByRole('button', { name: text.cancel, exact: true })).toBeVisible();
      await expect.poll(() => summaryPosts(calls.requests).length).toBe(1);
      expect(summaryPosts(calls.requests)[0]).toMatchObject({
        path: '/api/summaries/byok',
        body: { summary: { recipientId: recipientFixture.id, periodEndInclusive: '2026-10-24', periodDays: 7 }, credentials: testCredentials },
      });
      await expect.poll(() => summaryPosts(calls.requests).length).toBe(1);
      await page.getByRole('button', { name: text.cancel, exact: true }).click();
      await expect(page.getByText(text.cancelled, { exact: false })).toBeVisible();
      await expect(page.locator('.summary-progress')).toBeFocused();
      await expect(page.getByRole('heading', { name: text.result, exact: true })).toHaveCount(0);
      await expect(page.locator('.summary-disclaimer')).toBeVisible(); await accessibility(page);
    });
  });
}
