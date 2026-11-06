import { expect, test, type Page } from '@playwright/test';
import { activePlanFixture, protectedSuggestionFixture, suggestedPlanFixture } from '../src/tests/planFixtures';
import { testToken } from '../src/tests/authFixtures';
import { noteListFixture } from '../src/tests/noteFixtures';
import { recipientFixture } from '../src/tests/recipientFixtures';

const recipient = {
  ...recipientFixture,
  name: 'مريم Hassan (ID 12345) ' + 'اسمطويلLongName'.repeat(5),
};
const longLegacyText = '  خطة قديمة legacy-ID-42 — “keep exact.”\nMixedالعربية / English ID-987 (10:30)\n' + 'LongTokenعربي'.repeat(25) + '  ';
const legacyPlan = {
  ...suggestedPlanFixture,
  sourceType: 'CAREGIVER_SUBMITTED' as const,
  structuredContent: null,
  editable: false,
  content: longLegacyText,
};
const unknownStatusPlan = {
  ...protectedSuggestionFixture,
  status: 'SYNTHETIC_UNKNOWN_STATUS',
  editable: false,
};

async function setupAuthenticatedPage(page: Page, locale: 'en' | 'ar') {
  await page.addInitScript(({ locale, token }) => {
    localStorage.setItem('caregiver-language', locale);
    sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token, baseUrl: '' }));
  }, { locale, token: testToken() });
}

async function routePlans(page: Page, plans: { active?: typeof activePlanFixture | null; history?: typeof activePlanFixture[] } = {}) {
  const active = plans.active === undefined ? activePlanFixture : plans.active;
  const history = plans.history ?? [protectedSuggestionFixture, activePlanFixture];
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (request.method() === 'GET') {
      if (path === '/api/recipients') return route.fulfill({ json: [recipient] });
      if (path === `/api/recipients/${recipient.id}`) return route.fulfill({ json: recipient });
      if (path === `/api/recipients/${recipient.id}/plans/active`) {
        return active ? route.fulfill({ json: active }) : route.fulfill({ status: 404, json: { code: 'NOT_FOUND' } });
      }
      if (path === `/api/recipients/${recipient.id}/plans/history`) return route.fulfill({ json: history });
      if (path === '/api/notes') return route.fulfill({ json: {
        content: [{ ...noteListFixture, recipientId: recipient.id }], number: 0, size: 100,
        totalElements: 1, totalPages: 1, first: true, last: true,
      } });
    }
    return route.fulfill({ status: 501, json: { code: 'UNEXPECTED_TEST_REQUEST' } });
  });
}

async function checkNoHorizontalOverflow(page: Page) {
  const layout = await page.evaluate(() => ({
    overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
    outside: [...document.querySelectorAll<HTMLElement>('input, textarea, select, button, a[href]')]
      .filter((element) => element.getClientRects().length && getComputedStyle(element).visibility !== 'hidden')
      .filter((element) => {
        const box = element.getBoundingClientRect();
        return box.left < -1 || box.right > document.documentElement.clientWidth + 1;
      })
      .map((element) => element.id || element.textContent?.trim() || element.tagName),
  }));
  expect(layout).toEqual({ overflow: false, outside: [] });
}

async function openPlans(page: Page, locale: 'en' | 'ar' = 'ar') {
  await setupAuthenticatedPage(page, locale);
  await routePlans(page);
  await page.goto(`/app/notes/plans?recipientId=${recipient.id}`);
  await expect(page.getByRole('region', { name: locale === 'ar' ? 'الخطة المقبولة النشطة' : 'Active accepted plan' })).toBeVisible();
}

test('preserves mixed-script legacy plan text exactly in Arabic RTL', async ({ page }) => {
  await setupAuthenticatedPage(page, 'ar');
  await routePlans(page, { history: [legacyPlan, activePlanFixture] });
  await page.goto(`/app/notes/plans?recipientId=${recipient.id}`);
  await expect(page.getByRole('region', { name: 'الخطط المقترحة' })).toBeVisible();

  const content = page.locator('.plan-suggestions .plan-content');
  await expect(content).toHaveAttribute('dir', 'auto');
  await expect(content).toHaveJSProperty('textContent', longLegacyText);
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await checkNoHorizontalOverflow(page);
});

test('isolates Arabic plan versions, dates, identifiers, and unknown statuses', async ({ page }) => {
  await setupAuthenticatedPage(page, 'ar');
  await routePlans(page, { active: null, history: [unknownStatusPlan] });
  await page.goto(`/app/notes/plans?recipientId=${recipient.id}`);
  await expect(page.getByRole('region', { name: 'سجل الخطط' })).toBeVisible();

  const history = page.getByRole('region', { name: 'سجل الخطط' });
  const entry = history.locator('details.plan-history-version');
  await expect(entry.locator('summary span:first-child bdi')).toHaveText('٢');
  await expect(entry.locator('summary time bdi')).toBeVisible();
  await expect(entry.locator('summary .plan-status bdi')).toHaveText('SYNTHETIC_UNKNOWN_STATUS');
  await entry.locator('summary').click();
  await expect(entry.locator('.plan-medications bdi').first()).toHaveText('SYNTHETIC_REPORTED_ITEM');
  await expect(entry.locator('.plan-provenance').getByText(activePlanFixture.id, { exact: true })).toHaveJSProperty('tagName', 'BDI');
  await expect(entry.locator('.plan-structured .note-verbatim')).toHaveAttribute('dir', 'auto');
});

test('keeps the Plans workspace within 320px in Arabic and English', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 740 });
  await openPlans(page, 'ar');
  await checkNoHorizontalOverflow(page);
  await page.getByRole('button', { name: 'Switch to English', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
  await checkNoHorizontalOverflow(page);
});

test('keeps Settings and the AI dialog RTL while isolating the saved model identifier', async ({ page }) => {
  await setupAuthenticatedPage(page, 'ar');
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/ai/byok/connection-test') return route.fulfill({ json: { success: true } });
    if (path === '/api/recipients') return route.fulfill({ json: [recipient] });
    return route.fulfill({ status: 501, json: { code: 'UNEXPECTED_TEST_REQUEST' } });
  });
  await page.goto('/app/settings');
  await expect(page.getByRole('heading', { name: 'الإعدادات' })).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await page.getByRole('button', { name: 'إعدادات الذكاء الاصطناعي' }).click();
  const dialog = page.getByRole('dialog', { name: 'إعدادات مزود الذكاء الاصطناعي' });
  await expect(dialog).toBeVisible();
  await expect(dialog).toHaveJSProperty('dir', '');
  await expect(dialog).toHaveCSS('direction', 'rtl');
  await dialog.getByLabel('النموذج', { exact: true }).fill('vendor/Model-X-12:preview');
  await dialog.getByLabel('مفتاح API', { exact: true }).fill('synthetic-settings-key');
  await dialog.getByRole('button', { name: 'اختبار الاتصال' }).click();
  const model = dialog.locator('.byok-active bdi');
  await expect(model).toHaveText('vendor/Model-X-12:preview');
  await expect(model).toHaveJSProperty('textContent', 'vendor/Model-X-12:preview');
});

test('keeps Arabic and English Settings within a 320px viewport', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 740 });
  await setupAuthenticatedPage(page, 'ar');
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/recipients') return route.fulfill({ json: [recipient] });
    return route.fulfill({ status: 501, json: { code: 'UNEXPECTED_TEST_REQUEST' } });
  });
  await page.goto('/app/settings');
  await expect(page.getByRole('heading', { name: 'الإعدادات' })).toBeVisible();
  await checkNoHorizontalOverflow(page);
  await page.getByRole('button', { name: 'Switch to English', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();
  await checkNoHorizontalOverflow(page);
});
