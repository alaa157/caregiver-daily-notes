import { createRequire } from 'node:module';
import { expect, test, type Page } from '@playwright/test';
import { noteFixture } from '../noteFixtures';
import { recipientFixture } from '../recipientFixtures';

const require = createRequire(import.meta.url);
const encode = (value: unknown) => Buffer.from(JSON.stringify(value)).toString('base64url');
const token = () => [encode({ alg: 'HS256' }), encode({ sub: '12345678-1234-1234-1234-123456789abc', exp: Math.floor(Date.now() / 1000) + 3600 }), 'synthetic-signature'].join('.');
const secondRecipient = { ...recipientFixture, id: 'c3333333-3333-4333-8333-333333333333', name: 'No note recipient' };

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
}

for (const language of ['en', 'ar'] as const) {
  const labels = language === 'ar'
    ? { date: 'التاريخ الميلادي', missing: 'لم تُسجل ملاحظة', record: 'تسجيل ملاحظة', recipient: 'مستفيد الرعاية', noteDate: 'تاريخ الملاحظة' }
    : { date: 'Calendar date', missing: 'No note recorded', record: 'Record a note', recipient: 'Care recipient', noteDate: 'Observation date' };

  test(`workspace overview preserves date and recipient context (${language})`, async ({ page }) => {
    const pageErrors: string[] = [];
    page.on('pageerror', (error) => pageErrors.push(error.message));
    await page.addInitScript(({ locale, jwt }) => {
      localStorage.setItem('caregiver-language', locale);
      sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: jwt, baseUrl: '' }));
    }, { locale: language, jwt: token() });
    await page.route('**/api/recipients**', (route) => {
      expect(route.request().headers().authorization).toMatch(/^Bearer /);
      return route.fulfill({ json: [recipientFixture, secondRecipient] });
    });
    await page.route('**/api/notes?*', async (route) => {
      expect(route.request().headers().authorization).toMatch(/^Bearer /);
      const url = new URL(route.request().url());
      const recorded = url.searchParams.get('from') === noteFixture.date ? [noteFixture] : [];
      return route.fulfill({ json: { content: recorded, number: 0, size: 100, totalElements: recorded.length,
        totalPages: recorded.length ? 1 : 0, first: true, last: true } });
    });

    await page.goto('/app?date=2026-10-18');
    await expect(page.getByLabel(labels.date)).toHaveValue('2026-10-18');
    await expect(page.getByRole('link', { name: language === 'ar' ? 'فتح الملاحظة' : 'Open note' })).toHaveAttribute('href', `/app/notes/${noteFixture.id}`);
    await expect(page.getByText(labels.missing, { exact: true })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('dir', language === 'ar' ? 'rtl' : 'ltr');
    await accessibility(page);

    await page.getByLabel(labels.date).fill('2026-10-19');
    await expect(page).toHaveURL(/date=2026-10-19/);
    await page.reload();
    await expect(page.getByLabel(labels.date)).toHaveValue('2026-10-19');
    await page.goBack();
    await expect(page.getByLabel(labels.date)).toHaveValue('2026-10-18');

    await page.getByRole('link', { name: labels.record, exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`/app/notes/new\\?recipientId=${secondRecipient.id}\\&date=2026-10-18`));
    await expect(page.locator('#note-recipientId')).toHaveValue(secondRecipient.id);
    await expect(page.locator('#note-date')).toHaveValue('2026-10-18');
    await expect(page.getByLabel(labels.recipient)).toBeVisible();
    await expect(page.getByLabel(labels.noteDate)).toBeVisible();
    await accessibility(page);
    expect(pageErrors).toEqual([]);
  });
}
