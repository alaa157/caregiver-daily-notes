import { createRequire } from 'node:module';
import { expect, test, type Page } from '@playwright/test';
import { testToken } from '../src/tests/authFixtures';
import { correctionHistoryFixture, historyPage } from '../src/tests/historyFixtures';
import { noteFixture, noteListFixture } from '../src/tests/noteFixtures';
import { activePlanFixture, suggestedPlanFixture } from '../src/tests/planFixtures';
import { recipientFixture } from '../src/tests/recipientFixtures';
import { summaryFixture, summaryOpenApi, summaryTestPath } from '../src/tests/summaryFixtures';

const require = createRequire(import.meta.url);

async function inspect(page: Page) {
  if (await page.getByRole('dialog').count()) await expect(page.getByRole('dialog')).toBeVisible();
  else await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await expect(page.locator('.spinner')).toHaveCount(0);
  await page.evaluate(async () => {
    await document.fonts.ready;
    await Promise.all(document.getAnimations().map((animation) => animation.finished.catch(() => {})));
  });
  await page.addScriptTag({ path: require.resolve('axe-core/axe.min.js') });
  const violations = await page.evaluate(async () => {
    const { violations } = await (window as unknown as Window & { axe: typeof import('axe-core') }).axe.run(document, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
    });
    return violations.map(({ id, nodes }) => ({ id, nodes: nodes.map(({ target, failureSummary }) => ({ target, failureSummary })) }));
  });
  expect(violations, page.url()).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), page.url()).toBe(false);
}

async function serveSyntheticRecords(page: Page) {
  await page.route('**/v3/api-docs', (route) => route.fulfill({ json: summaryOpenApi() }));
  await page.route('**/api/**', async (route) => {
    const { pathname, searchParams } = new URL(route.request().url());
    if (pathname === '/api/recipients') return route.fulfill({ json: [recipientFixture] });
    if (pathname === `/api/recipients/${recipientFixture.id}`) return route.fulfill({ json: recipientFixture });
    if (pathname === '/api/notes') return route.fulfill({ json: historyPage([noteListFixture], 0, Number(searchParams.get('size') || 20)) });
    if (pathname.endsWith('/plans/active')) return route.fulfill({ json: activePlanFixture });
    if (pathname.endsWith('/plans/history')) return route.fulfill({ json: [suggestedPlanFixture, activePlanFixture] });
    if (pathname.endsWith('/corrections')) return route.fulfill({ json: correctionHistoryFixture() });
    if (pathname.endsWith('/addenda')) return route.fulfill({ json: [] });
    if (pathname === `/api/notes/${noteFixture.id}`) return route.fulfill({ json: noteFixture });
    if (pathname === summaryTestPath) return route.fulfill({ json: summaryFixture() });
    return route.fulfill({ status: 404, json: { code: 'NOT_FOUND' } });
  });
}

for (const language of ['en', 'ar'] as const) {
  test(`appearance preference persists through navigation and reload (${language})`, async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' });
    await page.addInitScript((locale) => localStorage.setItem('caregiver-language', locale), language);
    await page.goto('/');
    const appearance = page.getByRole('combobox', { name: language === 'ar' ? 'المظهر' : 'Appearance' });
    await expect(appearance).toHaveValue('system');
    const systemBackground = await page.locator('.site-shell').evaluate((element) => getComputedStyle(element).backgroundColor);
    await appearance.selectOption('light');
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
    expect(await page.locator('html').evaluate((element) => getComputedStyle(element).colorScheme)).toBe('light');
    expect(await page.locator('.site-shell').evaluate((element) => getComputedStyle(element).backgroundColor)).not.toBe(systemBackground);
    await page.getByRole('navigation', { name: language === 'ar' ? 'القائمة الرئيسية' : 'Main navigation' })
      .getByRole('link', { name: language === 'ar' ? 'تسجيل الدخول' : 'Sign in', exact: true }).click();
    await expect(appearance).toHaveValue('light');
    await page.reload();
    await expect(appearance).toHaveValue('light');
    expect(await page.evaluate(() => localStorage.getItem('caregiver-theme-mode'))).toBe('light');
    await appearance.selectOption('dark');
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
    await inspect(page);
    await appearance.selectOption('system');
    await expect(page.locator('html')).toHaveAttribute('data-theme', 'system');
    expect(await page.locator('.site-shell').evaluate((element) => getComputedStyle(element).backgroundColor)).toBe(systemBackground);
  });

  for (const mode of ['light', 'dark'] as const) {
    test(`all main screens remain readable and fit the viewport (${language}, ${mode})`, async ({ page }, info) => {
      test.skip(!['desktop', 'compact-mobile'].includes(info.project.name), 'The full screen audit covers desktop and 320px; existing workflows also cover tablet and mobile.');
      test.setTimeout(120_000);
      await page.emulateMedia({ reducedMotion: 'reduce' });
      await page.addInitScript(({ locale, theme }) => {
        localStorage.setItem('caregiver-language', locale);
        localStorage.setItem('caregiver-theme-mode', theme);
      }, { locale: language, theme: mode });
      const errors: string[] = [];
      page.on('pageerror', (error) => errors.push(error.message));
      for (const path of ['/', '/login', '/missing-page']) {
        await page.goto(path);
        await inspect(page);
        if (path === '/login') {
          await page.getByRole('button', { name: language === 'ar' ? 'تسجيل الدخول' : 'Sign in', exact: true }).click();
          await expect(page.locator('.field-error').first()).toBeVisible();
          await inspect(page);
        }
      }

      await serveSyntheticRecords(page);
      await page.evaluate((token) => sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token, baseUrl: '' })), testToken());
      for (const path of [
        '/app?date=2026-10-18', '/app/recipients', '/app/recipients/new',
        `/app/recipients/${recipientFixture.id}`, `/app/recipients/${recipientFixture.id}/edit`,
        '/app/notes/new', `/app/notes/${noteFixture.id}`, `/app/notes/${noteFixture.id}/addenda/new`,
        '/app/notes?from=2026-10-01&to=2026-10-31',
        `/app/notes/plans?recipientId=${recipientFixture.id}`,
      ]) {
        await page.goto(path);
        await inspect(page);
        if (path === `/app/recipients/${recipientFixture.id}`) {
          await page.getByRole('button', { name: language === 'ar' ? 'أرشفة المستفيد' : 'Archive recipient', exact: true }).click();
          await expect(page.getByRole('dialog')).toBeVisible();
          await inspect(page);
          await page.getByRole('dialog').getByRole('button', { name: language === 'ar' ? 'إلغاء' : 'Cancel', exact: true }).click();
        }
        if (path.includes('/notes/plans')) {
          await page.locator('.plan-suggestions').getByRole('button', { name: language === 'ar' ? 'قبول الاقتراح' : 'Accept suggestion', exact: true }).click();
          await expect(page.locator('.plan-confirmation')).toBeVisible();
          await inspect(page);
        }
      }

      await page.goto('/app/notes/summary');
      await page.getByLabel(language === 'ar' ? 'المستفيد من الرعاية' : 'Care recipient', { exact: true }).selectOption(recipientFixture.id);
      await page.getByRole('button', { name: language === 'ar' ? 'إنشاء ملخص' : 'Generate summary', exact: true }).click();
      await expect(page.locator('.summary-safety')).toBeVisible();
      await inspect(page);
      expect(errors).toEqual([]);
    });
  }
}

test('system appearance follows live OS changes while explicit themes remain selected', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'light' });
  await page.goto('/');

  const appearance = page.getByRole('combobox', { name: 'Appearance' });
  const root = page.locator('html');
  await expect(appearance).toHaveValue('system');
  await expect(root).toHaveAttribute('data-theme', 'system');
  await expect.poll(() => root.evaluate((element) => getComputedStyle(element).colorScheme)).toBe('light');
  const consoleErrors: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });

  await page.emulateMedia({ colorScheme: 'dark' });
  await expect.poll(() => root.evaluate((element) => getComputedStyle(element).colorScheme)).toBe('dark');
  await expect(appearance).toHaveValue('system');
  await expect(root).toHaveAttribute('data-theme', 'system');

  await page.emulateMedia({ colorScheme: 'light' });
  await expect.poll(() => root.evaluate((element) => getComputedStyle(element).colorScheme)).toBe('light');
  await expect(appearance).toHaveValue('system');

  await appearance.selectOption('light');
  await page.emulateMedia({ colorScheme: 'dark' });
  await expect.poll(() => root.evaluate((element) => getComputedStyle(element).colorScheme)).toBe('light');
  await expect(appearance).toHaveValue('light');

  await appearance.selectOption('dark');
  await page.emulateMedia({ colorScheme: 'light' });
  await expect.poll(() => root.evaluate((element) => getComputedStyle(element).colorScheme)).toBe('dark');
  await expect(appearance).toHaveValue('dark');

  await appearance.selectOption('system');
  await expect.poll(() => root.evaluate((element) => getComputedStyle(element).colorScheme)).toBe('light');
  await page.emulateMedia({ colorScheme: 'dark' });
  await expect.poll(() => root.evaluate((element) => getComputedStyle(element).colorScheme)).toBe('dark');
  await expect(appearance).toHaveValue('system');
  expect(await page.evaluate(() => localStorage.getItem('caregiver-theme-mode'))).toBe('system');
  expect(consoleErrors).toEqual([]);
});
