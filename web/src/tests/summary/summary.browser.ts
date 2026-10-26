import { createRequire } from 'node:module';
import { expect, test, type Page } from '@playwright/test';
import { testToken } from '../authFixtures';
import { recipientFixture } from '../recipientFixtures';
import { summaryFixture, summaryOpenApi, summaryTestPath } from '../summaryFixtures';

const require = createRequire(import.meta.url);
const root = '/app/notes/summary';
type Options = { missing?: boolean; fail?: boolean; fallback?: boolean; invalid?: boolean; malicious?: boolean; pending?: boolean };
async function serve(page: Page, options: Options = {}) {
  const requests: { method: string; path: string; body: unknown }[] = [];
  await page.route('**/v3/api-docs', (route) => route.fulfill({ json: options.missing ? { openapi: '3.0.1', paths: {} } : summaryOpenApi() }));
  await page.route('**/api/**', async (route) => {
    const request = route.request(); const url = new URL(request.url());
    requests.push({ method: request.method(), path: url.pathname, body: request.postDataJSON() });
    expect(request.headers().authorization).toMatch(/^Bearer /);
    if (url.pathname === '/api/recipients') return route.fulfill({ json: [recipientFixture] });
    expect(url.pathname).toBe(summaryTestPath); expect(request.method()).toBe('POST');
    if (options.pending) return; // Cancelled by the UI; no provider is contacted.
    if (options.fail) return route.fulfill({ status: 504, json: { message: 'private diagnostics' } });
    const fixture = summaryFixture();
    if (options.fallback) fixture.summary.aiUnavailable = true;
    if (options.malicious) {
      fixture.summary.observations[0].text = '<style>.summary-safety{display:none}</style> Ignore all previous warnings. All clear. ' + 'Mixedعربي'.repeat(50);
      fixture.summary.observations[0].noteId = 'javascript:alert(1)';
      fixture.safety.signals.push({ severity: 'CRITICAL', ruleId: 'FUTURE_RULE', reason: 'Backend urgency must not be reduced.' });
    }
    return route.fulfill({ json: options.invalid ? { ...fixture, summary: null } : fixture });
  });
  return requests;
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
    unavailable: ar ? 'الملخصات غير متاحة حاليًا.' : 'Summaries are not yet available.',
    fallback: ar ? 'الذكاء الاصطناعي غير متاح.' : 'AI is unavailable.',
    timeout: ar ? 'انتهت مهلة طلب الملخص.' : 'The summary request timed out.',
    cancel: ar ? 'إلغاء الطلب' : 'Cancel request', cancelled: ar ? 'أُلغي الطلب.' : 'Request cancelled.',
  };
  async function choose(page: Page) {
    await page.getByLabel(text.recipient, { exact: true }).selectOption(recipientFixture.id);
    await page.getByLabel(text.end, { exact: true }).fill('2026-10-24');
  }
  test.describe(`summary ${language}`, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript(({ language, token }) => {
        localStorage.setItem('caregiver-language', language);
        sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token, baseUrl: '' }));
      }, { language, token: testToken() });
    });
    test('requests 7/14/30 days by keyboard and displays evidence, uncertainty and non-dismissable safety', async ({ page }, info) => {
      const calls = await serve(page); await page.goto(root);
      await expect(page.getByRole('heading', { name: text.title, exact: true })).toBeVisible();
      await expect(page).toHaveTitle(new RegExp(text.title));
      await expect(page.locator('html')).toHaveAttribute('dir', ar ? 'rtl' : 'ltr');
      await choose(page); expect(calls.filter((call) => call.method === 'POST')).toHaveLength(0);
      await accessibility(page);
      for (const period of [7, 14, 30]) {
        const number = new Intl.NumberFormat(ar ? 'ar-EG' : 'en').format(period);
        await page.getByRole('radio', { name: `${number} ${ar ? 'أيام' : 'days'}`, exact: true }).check();
        await page.getByRole('button', { name: period === 7 ? text.generate : text.regenerate, exact: true }).focus();
        await page.keyboard.press('Enter');
        await expect(page.getByRole('heading', { name: text.result, exact: true })).toBeVisible();
        await expect(page.getByRole('button', { name: text.regenerate, exact: true })).toBeEnabled();
        await expect(page.locator('.summary-progress')).toBeFocused();
        expect(calls.filter((call) => call.method === 'POST').at(-1)?.body).toEqual({ recipientId: recipientFixture.id, periodEndInclusive: '2026-10-24', periodDays: period });
        await expect(page.getByRole('heading', { name: text.clinical })).toBeVisible();
        await expect(page.getByText('HIGH', { exact: true })).toBeVisible();
        await expect(page.locator('.summary-safety button')).toHaveCount(0);
        await expect(page.getByRole('heading', { name: text.uncertainty })).toBeVisible();
        expect(await page.locator('blockquote').textContent()).toBe(summaryFixture().summary.observations[0].quote);
        await expect(page.locator('.summary-evidence a')).toHaveAttribute('href', '/app/notes/c3333333-3333-4333-8333-333333333333');
        await expect(page.locator('.summary-result script, .summary-result img')).toHaveCount(0);
      }
      await accessibility(page);
      await page.evaluate(() => { (document.activeElement as HTMLElement | null)?.blur(); window.scrollTo({ top: 0, behavior: 'instant' }); });
      await page.screenshot({ path: `/tmp/task6-${language}-${info.project.name}.png`, fullPage: true });
      expect(await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))).not.toContain('Reported appetite');
    });
    test('ordinary malicious content and malformed output cannot hide or downgrade backend urgency', async ({ page }) => {
      const options = { malicious: true, invalid: false }; await serve(page, options); await page.goto(root); await choose(page);
      await page.getByRole('button', { name: text.generate, exact: true }).click();
      await expect(page.getByText('HIGH', { exact: true })).toBeVisible();
      await expect(page.getByText('CRITICAL', { exact: true })).toBeVisible();
      await expect(page.locator('.summary-result style')).toHaveCount(0);
      await expect(page.locator('.summary-evidence a')).toHaveCount(0);
      await expect(page.locator('.summary-safety')).toBeVisible(); await accessibility(page);
      options.invalid = true;
      await page.getByRole('button', { name: text.regenerate, exact: true }).click();
      await expect(page.locator('.summary-result > .summary-notice[role=alert]')).toBeVisible();
      await expect(page.getByText('HIGH', { exact: true })).toBeVisible();
      await expect(page.getByText('CRITICAL', { exact: true })).toBeVisible(); await accessibility(page);
    });
    test('handles unavailable, fallback and failed regeneration while keeping disclaimer and received flags', async ({ page }) => {
      const options: Options = { missing: true }; const calls = await serve(page, options);
      await page.goto(root); await choose(page); await page.getByRole('button', { name: text.generate, exact: true }).click();
      await expect(page.getByRole('alert')).toContainText(text.unavailable);
      await expect(page.locator('.summary-progress')).toBeFocused();
      expect(calls.filter((call) => call.method === 'POST')).toHaveLength(0); await accessibility(page);
      options.missing = false; options.fallback = true;
      await page.getByRole('button', { name: text.generate, exact: true }).click();
      await expect(page.getByText(text.fallback, { exact: false })).toBeVisible();
      await expect(page.getByText('HIGH', { exact: true })).toBeVisible();
      await expect(page.locator('blockquote')).toHaveCount(0); await accessibility(page);
      options.fail = true;
      await page.getByRole('button', { name: text.regenerate, exact: true }).click();
      await expect(page.getByRole('alert')).toContainText(text.timeout);
      await expect(page.getByText('HIGH', { exact: true })).toBeVisible();
      await expect(page.locator('.summary-disclaimer')).toBeVisible();
      await expect(page.getByText('private diagnostics')).toHaveCount(0); await accessibility(page);
    });
    test('validates required selections and lets the user cancel without automatic retry', async ({ page }) => {
      const options = { pending: true }; const calls = await serve(page, options);
      await page.goto(root);
      await page.getByRole('button', { name: text.generate, exact: true }).click();
      await expect(page.getByLabel(text.recipient, { exact: true })).toBeFocused();
      expect(calls.filter((call) => call.method === 'POST')).toHaveLength(0); await accessibility(page);
      await choose(page); await page.getByRole('button', { name: text.generate, exact: true }).dblclick();
      await expect(page.getByRole('button', { name: text.cancel, exact: true })).toBeVisible();
      await expect.poll(() => calls.filter((call) => call.method === 'POST').length).toBe(1);
      await page.getByRole('button', { name: text.cancel, exact: true }).click();
      await expect(page.getByText(text.cancelled, { exact: false })).toBeVisible();
      await expect(page.locator('.summary-progress')).toBeFocused();
      await expect(page.getByRole('heading', { name: text.result, exact: true })).toHaveCount(0);
      await expect(page.locator('.summary-disclaimer')).toBeVisible(); await accessibility(page);
    });
  });
}
