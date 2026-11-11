import { createRequire } from 'node:module';
import { expect, test, type Page } from '@playwright/test';
import { noteFixture, noteListFixture } from '../noteFixtures';
import { recipientFixture } from '../recipientFixtures';

const require = createRequire(import.meta.url);
const encode = (value: unknown) => Buffer.from(JSON.stringify(value)).toString('base64url');
const token = () => [encode({ alg: 'HS256' }), encode({ sub: '12345678-1234-1234-1234-123456789abc', exp: Math.floor(Date.now() / 1000) + 3600 }), 'synthetic-signature'].join('.');
const secondRecipient = { ...recipientFixture, id: 'c3333333-3333-4333-8333-333333333333', name: 'No note recipient' };
const responsiveViewportWidths = [320, 375, 430, 768, 1024, 1280, 1440];

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
      const recorded = url.searchParams.get('from') === noteFixture.date ? [noteListFixture] : [];
      return route.fulfill({ json: { content: recorded, number: 0, size: 100, totalElements: recorded.length,
        totalPages: recorded.length ? 1 : 0, first: true, last: true } });
    });

    await page.goto('/app?date=2026-10-18');
    await expect(page.getByLabel(labels.date)).toHaveValue('2026-10-18');
    await expect(page.getByRole('link', { name: language === 'ar' ? 'فتح الملاحظة' : 'Open note' })).toHaveAttribute('href', `/app/notes/${noteFixture.id}`);
    await expect(page.getByText(labels.missing, { exact: true })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('dir', language === 'ar' ? 'rtl' : 'ltr');
    await accessibility(page);

    const originalViewport = page.viewportSize();
    if (!originalViewport) throw new Error('Overview viewport is unavailable.');
    for (const width of responsiveViewportWidths) {
      await page.setViewportSize({ width, height: originalViewport.height });
      const layout = await page.locator('.overview-kpi-list').evaluate((list) => {
        const cards = Array.from(list.querySelectorAll('.overview-kpi-card'));
        const firstCardTop = cards[0]?.getBoundingClientRect().top ?? 0;
        const routineCards = Array.from(document.querySelectorAll('.overview-routine .routine-link'));
        const firstRoutineCardTop = routineCards[0]?.getBoundingClientRect().top ?? 0;
        const recipientCards = Array.from(document.querySelectorAll('.overview-card'));
        const recipientCardText = Array.from(document.querySelectorAll(
          '.overview-card h3, .overview-card .overview-secondary, .overview-card .overview-status, .overview-card .overview-note-status > a'
        ));
        return {
          columns: cards.filter((card) => Math.abs(card.getBoundingClientRect().top - firstCardTop) < 1).length,
          routineColumns: routineCards.filter((card) => Math.abs(card.getBoundingClientRect().top - firstRoutineCardTop) < 1).length,
          hasHorizontalOverflow: document.documentElement.scrollWidth > window.innerWidth,
          kpiCardsFitViewport: cards.every((card) => {
            const bounds = card.getBoundingClientRect();
            return bounds.left >= 0 && bounds.right <= window.innerWidth;
          }),
          recipientCardsFitList: recipientCards.every((card) => {
            const list = card.parentElement;
            if (!list) return false;
            const bounds = card.getBoundingClientRect();
            const listBounds = list.getBoundingClientRect();
            return bounds.left >= listBounds.left && bounds.right <= listBounds.right;
          }),
          textFits: recipientCardText.every((element) => element.scrollWidth <= element.clientWidth),
        };
      });

      expect(layout.columns).toBe(width <= 540 ? 1 : width <= 1000 ? 2 : 3);
      expect(layout.routineColumns).toBe(width <= 540 ? 1 : width <= 1000 ? 2 : 4);
      expect(layout.hasHorizontalOverflow).toBe(false);
      expect(layout.kpiCardsFitViewport).toBe(true);
      expect(layout.recipientCardsFitList).toBe(true);
      expect(layout.textFits).toBe(true);
      await expect(page.getByLabel(labels.date)).toBeVisible();
      await expect(page.getByRole('link', { name: labels.record, exact: true })).toBeVisible();
    }
    await page.setViewportSize(originalViewport);

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
