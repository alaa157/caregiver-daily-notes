import { createRequire } from 'node:module';
import { expect, test, type Page } from '@playwright/test';
import { testToken } from '../authFixtures';
import { activePlanFixture, suggestedPlanFixture } from '../planFixtures';
import { recipientFixture } from '../recipientFixtures';

const require = createRequire(import.meta.url);
const root = `/app/notes/plans?recipientId=${recipientFixture.id}`;
async function serve(page: Page, options: { failure?: boolean; conflict?: boolean; uncertain?: boolean; inactive?: boolean; empty?: boolean } = {}) {
  let active = { ...activePlanFixture };
  let history = [{ ...suggestedPlanFixture }, { ...active }];
  const writes: { path: string; body: string | null }[] = [];
  await page.route('**/api/**', async (route) => {
    const request = route.request(); const url = new URL(request.url());
    expect(request.headers().authorization).toMatch(/^Bearer /);
    if (url.pathname === '/api/recipients') return route.fulfill({ json: [{ ...recipientFixture, active: !options.inactive }] });
    if (url.pathname.endsWith(`/recipients/${recipientFixture.id}`)) return route.fulfill({ json: { ...recipientFixture, active: !options.inactive } });
    if (request.method() === 'POST') {
      writes.push({ path: url.pathname, body: request.postData() });
      if (options.conflict) return route.fulfill({ status: 409, json: { code: 'INVALID_PLAN_TRANSITION', message: 'private diagnostics' } });
      if (options.uncertain) return route.abort('connectionreset');
      const dismiss = url.pathname.endsWith('/dismiss');
      history = history.map((plan) => plan.id === suggestedPlanFixture.id ? { ...plan, status: dismiss ? 'DISMISSED' : 'ACCEPTED' }
        : dismiss ? plan : { ...plan, status: 'ARCHIVED' });
      if (!dismiss) active = history[0];
      return route.fulfill({ json: { ...active, createdAt: null } });
    }
    expect(request.method()).toBe('GET');
    if (options.failure) return route.fulfill({ status: 503, json: { message: 'private diagnostics' } });
    if (url.pathname.endsWith('/history')) return route.fulfill({ json: options.empty ? [] : history });
    return options.empty ? route.fulfill({ status: 404, json: { code: 'NOT_FOUND' } }) : route.fulfill({ json: active });
  });
  return { writes, options };
}
async function accessibility(page: Page) {
  await page.evaluate(async () => { await document.fonts.ready; });
  await page.evaluate(async () => { await Promise.allSettled(document.getAnimations().filter((animation) => animation.effect?.getTiming().iterations !== Infinity).map((animation) => animation.finished)); });
  await page.addScriptTag({ path: require.resolve('axe-core/axe.min.js') });
  const violations = await page.evaluate(async () => {
    const result = await (window as unknown as Window & { axe: typeof import('axe-core') }).axe.run(document,
      { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } });
    return result.violations.map(({ id, nodes }) => ({ id, targets: nodes.map(({ target }) => target) }));
  });
  expect(violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
}
for (const language of ['en', 'ar'] as const) {
  const ar = language === 'ar';
  const labels = {
    title: ar ? 'خطط الرعاية' : 'Care plans', active: ar ? 'الخطة المقبولة النشطة' : 'Active accepted plan',
    suggested: ar ? 'الخطط المقترحة' : 'Suggested plans', history: ar ? 'سجل الخطط' : 'Plan history',
    accept: ar ? 'قبول الاقتراح' : 'Accept suggestion', dismiss: ar ? 'رفض الاقتراح' : 'Dismiss suggestion',
    confirmAccept: ar ? 'تأكيد القبول' : 'Confirm acceptance', confirmDismiss: ar ? 'تأكيد الرفض' : 'Confirm dismissal',
    cancel: ar ? 'إلغاء' : 'Cancel', edit: ar ? 'التعديل والقبول — غير متاح' : 'Edit and accept — unavailable',
    reload: ar ? 'إعادة تحميل الخطط المحفوظة' : 'Reload saved plans', retry: ar ? 'إعادة المحاولة' : 'Retry',
    accepted: ar ? 'قبل الخادم الاقتراح.' : 'The server accepted the suggestion.', dismissed: ar ? 'رفض الخادم الاقتراح.' : 'The server dismissed the suggestion.',
    empty: ar ? 'لا توجد خطة مقبولة بعد' : 'No accepted plan yet',
  };
  test.describe(`care plans ${language}`, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript(({ locale, token }) => {
        localStorage.setItem('caregiver-language', locale);
        sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token, baseUrl: '' }));
      }, { locale: language, token: testToken() });
    });
    test('reviews separate saved states, exact mixed content, sources and history accessibly', async ({ page }, info) => {
      const { writes } = await serve(page); await page.goto(root);
      await expect(page.getByRole('region', { name: labels.active, exact: true })).toBeVisible();
      await expect(page).toHaveTitle(new RegExp(labels.title));
      await expect(page.locator('html')).toHaveAttribute('dir', ar ? 'rtl' : 'ltr');
      const suggestions = page.getByRole('region', { name: labels.suggested, exact: true });
      await expect(suggestions.locator('.plan-content')).toHaveJSProperty('textContent', suggestedPlanFixture.content);
      await expect(suggestions.locator('.plan-content')).toHaveAttribute('dir', 'auto');
      await expect(suggestions.getByRole('button', { name: labels.edit, exact: true })).toBeDisabled();
      expect(await page.getByRole('textbox').count()).toBe(0);
      await expect(suggestions.getByRole('link', { name: 'e5555555-5555-4555-8555-555555555555' })).toHaveAttribute('href', '/app/notes/e5555555-5555-4555-8555-555555555555');
      expect(await suggestions.locator('script').count()).toBe(0);
      const original = page.locator(`#plan-history-${activePlanFixture.id}`);
      await original.locator('summary').focus(); await page.keyboard.press('Enter');
      await expect(original).toHaveAttribute('open', '');
      await expect(original.locator('.plan-content')).toHaveJSProperty('textContent', activePlanFixture.content);
      await accessibility(page); expect(writes).toHaveLength(0);
      if (['desktop', 'compact-mobile'].includes(info.project.name)) await page.screenshot({ path: info.outputPath(`plans-${language}.png`), fullPage: true });
    });
    test('confirms acceptance by keyboard, archives the prior version and reloads saved data', async ({ page }) => {
      const { writes } = await serve(page); await page.goto(root);
      const suggestions = page.getByRole('region', { name: labels.suggested, exact: true });
      const accept = suggestions.getByRole('button', { name: labels.accept, exact: true });
      await accept.focus(); await page.keyboard.press('Enter');
      await expect(page.getByRole('button', { name: labels.cancel, exact: true })).toBeFocused(); expect(writes).toHaveLength(0);
      await accessibility(page);
      await page.getByRole('button', { name: labels.cancel, exact: true }).click(); await expect(accept).toBeFocused();
      await page.keyboard.press('Enter'); await page.getByRole('button', { name: labels.confirmAccept, exact: true }).focus(); await page.keyboard.press('Enter');
      const status = page.getByRole('status').filter({ hasText: labels.accepted }); await expect(status).toBeVisible(); await expect(status).toBeFocused();
      expect(writes).toEqual([{ path: `/api/recipients/${recipientFixture.id}/plans/${suggestedPlanFixture.id}/accept`, body: null }]);
      await expect(page.getByRole('region', { name: labels.active, exact: true }).locator('.plan-content')).toHaveJSProperty('textContent', suggestedPlanFixture.content);
      await expect(page.locator(`#plan-history-${activePlanFixture.id} summary .plan-status`)).toHaveText(ar ? 'مؤرشف' : 'Archived');
      await accessibility(page);
    });
    test('dismisses a suggestion and preserves the active plan and original content in history', async ({ page }) => {
      const { writes } = await serve(page); await page.goto(root);
      await page.getByRole('region', { name: labels.suggested, exact: true }).getByRole('button', { name: labels.dismiss, exact: true }).click();
      await page.getByRole('button', { name: labels.confirmDismiss, exact: true }).click();
      await expect(page.getByRole('status').filter({ hasText: labels.dismissed })).toBeFocused();
      expect(writes).toEqual([{ path: `/api/recipients/${recipientFixture.id}/plans/${suggestedPlanFixture.id}/dismiss`, body: null }]);
      await expect(page.getByRole('region', { name: labels.active, exact: true }).locator('.plan-content')).toHaveJSProperty('textContent', activePlanFixture.content);
      const dismissed = page.locator(`#plan-history-${suggestedPlanFixture.id}`); await dismissed.locator('summary').click();
      await expect(dismissed.locator('.plan-content')).toHaveJSProperty('textContent', suggestedPlanFixture.content);
      await expect(dismissed.locator('summary .plan-status')).toHaveText(ar ? 'مرفوض' : 'Dismissed');
      await accessibility(page);
    });
    test('recovers safely from read/transition/network errors and reviews inactive/empty plans', async ({ page }) => {
      const state = await serve(page, { failure: true, conflict: true }); await page.goto(root);
      await expect(page.getByRole('alert')).toBeVisible(); await expect(page.getByRole('alert')).not.toContainText('private diagnostics'); await accessibility(page);
      state.options.failure = false; await page.getByRole('button', { name: labels.retry, exact: true }).click();
      const accept = page.getByRole('region', { name: labels.suggested, exact: true }).getByRole('button', { name: labels.accept, exact: true });
      await accept.click(); await page.getByRole('button', { name: labels.confirmAccept, exact: true }).click();
      await expect(page.getByRole('alert')).toBeFocused(); await expect(accept).toBeDisabled(); expect(state.writes).toHaveLength(1); await accessibility(page);
      state.options.conflict = false; state.options.uncertain = true;
      await page.getByRole('button', { name: labels.reload, exact: true }).click(); await accept.click();
      await page.getByRole('button', { name: labels.confirmAccept, exact: true }).click(); await expect(page.getByRole('alert')).toBeFocused(); await expect(accept).toBeDisabled(); expect(state.writes).toHaveLength(2);
      state.options.inactive = true; await page.getByRole('button', { name: labels.reload, exact: true }).click();
      await expect(accept).toBeDisabled(); await expect(page.locator('.plan-workspace')).toContainText(ar ? 'هذا المستفيد غير نشط' : 'This recipient is inactive'); await accessibility(page);
      state.options.empty = true; await page.getByRole('button', { name: labels.reload, exact: true }).click(); await expect(page.getByText(labels.empty, { exact: true })).toBeVisible(); await accessibility(page);
    });
  });
}
