import { expect, test, type Page } from '@playwright/test';
import { testToken } from '../src/tests/authFixtures';
import { activePlanFixture, suggestedPlanFixture } from '../src/tests/planFixtures';
import { noteListFixture } from '../src/tests/noteFixtures';
import { recipientFixture } from '../src/tests/recipientFixtures';

const recipientId = recipientFixture.id;
const activePlan = { ...activePlanFixture, recipientId };
const suggestedPlan = { ...suggestedPlanFixture, recipientId, sourcePlanId: activePlan.id, content: 'PLAN-123' };

type Locale = 'en' | 'ar';

async function setup(page: Page, locale: Locale, width = 1280) {
  await page.setViewportSize({ width, height: 900 });
  await page.addInitScript(({ initialLocale, token }) => {
    localStorage.setItem('caregiver-language', initialLocale);
    sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token, baseUrl: '' }));
  }, { initialLocale: locale, token: testToken() });

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (request.method() === 'GET') {
      if (path === '/api/recipients') return route.fulfill({ json: [recipientFixture] });
      if (path === `/api/recipients/${recipientId}`) return route.fulfill({ json: recipientFixture });
      if (path === `/api/recipients/${recipientId}/plans/active`) return route.fulfill({ json: activePlan });
      if (path === `/api/recipients/${recipientId}/plans/history`) return route.fulfill({ json: [suggestedPlan, activePlan] });
      if (path === '/api/notes') return route.fulfill({ json: {
        content: [{ ...noteListFixture, recipientId }], number: 0, size: 100,
        totalElements: 1, totalPages: 1, first: true, last: true,
      } });
    }
    return route.fulfill({ status: 501, json: { code: 'UNEXPECTED_TEST_REQUEST' } });
  });
}

async function openPlans(page: Page, locale: Locale, width = 1280) {
  await setup(page, locale, width);
  await page.goto(`/app/notes/plans?recipientId=${recipientId}`);
  await expect(page.getByRole('region', { name: locale === 'ar' ? 'الخطة المقبولة النشطة' : 'Active accepted plan' })).toBeVisible();
}

async function checkNoHorizontalOverflow(page: Page) {
  const widths = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }));
  expect(widths.scrollWidth).toBe(widths.clientWidth);
}

test('renders plan actions in Arabic logical order', async ({ page }) => {
  await openPlans(page, 'ar');
  const actions = page.getByRole('region', { name: 'الخطط المقترحة' }).locator('.note-actions').first();
  const accept = actions.getByRole('button', { name: 'قبول الاقتراح' });
  const dismiss = actions.getByRole('button', { name: 'رفض الاقتراح' });
  const edit = actions.getByRole('button', { name: 'التعديل والقبول' });
  await expect(accept).toBeVisible();
  await expect(dismiss).toBeVisible();
  await expect(edit).toBeVisible();
  const [acceptBox, dismissBox, editBox] = await Promise.all([accept.boundingBox(), dismiss.boundingBox(), edit.boundingBox()]);
  expect(acceptBox).not.toBeNull();
  expect(dismissBox).not.toBeNull();
  expect(editBox).not.toBeNull();
  expect(acceptBox!.x).toBeGreaterThan(dismissBox!.x);
  expect(dismissBox!.x).toBeGreaterThan(editBox!.x);
});

test('preserves mixed Arabic and Latin plan identifiers', async ({ page }) => {
  await openPlans(page, 'ar');
  const id = page.locator('.plan-content').filter({ hasText: 'PLAN-123' }).first();
  await expect(id).toBeVisible();
  await expect(id).toHaveJSProperty('textContent', 'PLAN-123');
  await expect(id).toHaveAttribute('dir', 'auto');
});

test('renders settings and AI dialog in Arabic RTL', async ({ page }) => {
  await setup(page, 'ar');
  await page.goto('/app/settings');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await expect(page.getByRole('heading', { name: 'الإعدادات' })).toBeVisible();
  const openDialog = page.getByRole('button', { name: 'إعدادات الذكاء الاصطناعي' });
  await expect(openDialog).toBeVisible();
  await openDialog.click();
  const dialog = page.getByRole('dialog', { name: 'إعدادات مزود الذكاء الاصطناعي' });
  await expect(dialog).toBeVisible();
  expect(await dialog.evaluate((element) => getComputedStyle(element).direction)).toBe('rtl');
  await expect(dialog.getByLabel('المزود')).toBeVisible();
  await expect(dialog.getByLabel('النموذج')).toBeVisible();
  await expect(dialog.getByLabel('مفتاح API')).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'اختبار الاتصال' })).toBeVisible();
  await expect(dialog.locator('form .auth-field label')).toHaveText(['المزود', 'النموذج', 'مفتاح API']);
});

test('does not overflow plans or settings at 320 pixels', async ({ page }) => {
  await openPlans(page, 'ar', 1280);
  await checkNoHorizontalOverflow(page);
  await page.setViewportSize({ width: 320, height: 740 });
  await checkNoHorizontalOverflow(page);
  await expect(page.getByRole('region', { name: 'الخطط المقترحة' })).toBeVisible();

  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto('/app/settings');
  await checkNoHorizontalOverflow(page);
  await expect(page.getByRole('heading', { name: 'الإعدادات' })).toBeVisible();
  await page.setViewportSize({ width: 320, height: 740 });
  await checkNoHorizontalOverflow(page);
  await expect(page.getByRole('button', { name: 'إعدادات الذكاء الاصطناعي' })).toBeVisible();

  await page.setViewportSize({ width: 1280, height: 900 });
  await page.getByRole('button', { name: 'Switch to English' }).click();
  await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
  await checkNoHorizontalOverflow(page);
  await page.goto('/app/settings');
  await checkNoHorizontalOverflow(page);
});

test('restores plan LTR layout in English', async ({ page }) => {
  await openPlans(page, 'ar');
  await page.getByRole('button', { name: 'Switch to English' }).click();
  await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
  await expect(page.getByRole('heading', { name: 'Care plans' })).toBeVisible();
});
