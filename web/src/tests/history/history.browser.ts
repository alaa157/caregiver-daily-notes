import { createRequire } from 'node:module';
import { expect, test, type Page } from '@playwright/test';
import type { DailyNote } from '../../api/notes';
import { testToken } from '../authFixtures';
import { historyNote, historyPage } from '../historyFixtures';
import { addendumFixture } from '../noteFixtures';
import { recipientFixture } from '../recipientFixtures';

const require = createRequire(import.meta.url);
const root = '/app/notes?from=2026-10-01&to=2026-10-22';
const otherRecipient = { ...recipientFixture, id: 'b2222222-2222-4222-8222-222222222222', name: 'ليلى Smith', active: false };
const secondNote = { ...historyNote, id: 'c3333333-3333-4333-8333-333333333333', date: '2026-10-19', recipientId: otherRecipient.id, addendumCount: 0 };

async function serve(page: Page, options: { notes?: DailyNote[]; fail?: boolean; failCorrections?: boolean } = {}) {
  const calls: URL[] = [];
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    calls.push(url);
    expect(request.method()).toBe('GET');
    expect(request.headers().authorization).toMatch(/^Bearer /);
    expect(url.searchParams.has('caregiverId')).toBe(false);
    if (url.pathname === '/api/recipients') return route.fulfill({ json: [recipientFixture, otherRecipient] });
    if (url.pathname.startsWith('/api/recipients/')) return route.fulfill({ json: recipientFixture });
    if (url.pathname.endsWith('/addenda')) return options.failCorrections ? route.fulfill({ status: 500, json: {} }) : route.fulfill({ json: [addendumFixture] });
    if (url.pathname !== '/api/notes') return route.fulfill({ json: historyNote });
    if (options.fail) return route.fulfill({ status: 500, json: { message: 'private diagnostics' } });
    const recipientId = url.searchParams.get('recipientId');
    const from = url.searchParams.get('from')!; const to = url.searchParams.get('to')!;
    const notes = (options.notes ?? [secondNote, historyNote]).filter((note) => (!recipientId || note.recipientId === recipientId) && note.date >= from && note.date <= to);
    const number = Number(url.searchParams.get('page')); const size = Number(url.searchParams.get('size'));
    return route.fulfill({ json: historyPage(notes.slice(number * size, (number + 1) * size), number, size, notes.length) });
  });
  return calls;
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
  const label = {
    title: ar ? 'سجل الملاحظات' : 'Notes history', recipient: ar ? 'المستفيد من الرعاية' : 'Care recipient',
    from: ar ? 'من تاريخ' : 'From date', to: ar ? 'إلى تاريخ' : 'To date', day: ar ? 'يوم واحد' : 'Single date', date: ar ? 'التاريخ' : 'Date',
    apply: ar ? 'تطبيق التصفية' : 'Apply filters', reset: ar ? 'إعادة ضبط التصفية' : 'Reset filters', results: ar ? 'نتائج السجل' : 'History results',
    fields: ar ? 'عرض المشاهدات والنص الأصلي' : 'View original observations and text', show: ar ? 'عرض التصحيحات (١)' : 'Show corrections (1)',
    correction: ar ? 'تصحيح ١' : 'Correction 1', original: ar ? 'الملاحظة الأصلية' : 'Original note', open: ar ? 'فتح الملاحظة كاملة' : 'Open full note',
    next: ar ? 'الصفحة التالية' : 'Next page', previous: ar ? 'الصفحة السابقة' : 'Previous page', size: ar ? 'عدد الملاحظات في الصفحة' : 'Notes per page',
    retry: ar ? 'إعادة المحاولة' : 'Retry', empty: ar ? 'لا توجد ملاحظات تطابق التصفية.' : 'No notes match these filters.',
  };
  test.describe(`history ${language}`, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript(({ locale, token }) => {
        localStorage.setItem('caregiver-language', locale);
        sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token, baseUrl: '' }));
      }, { locale: language, token: testToken() });
    });
    test('filters by recipient and date, restores the URL after visiting a note, and resets', async ({ page }) => {
      const calls = await serve(page);
      await page.goto(root);
      await expect(page.getByRole('article', { name: recipientFixture.name, exact: true })).toBeVisible();
      await expect(page).toHaveTitle(new RegExp(label.title));
      await expect(page.locator('html')).toHaveAttribute('dir', ar ? 'rtl' : 'ltr');
      await accessibility(page);
      await page.getByLabel(label.recipient, { exact: true }).selectOption(otherRecipient.id);
      await page.getByLabel(label.from, { exact: true }).fill('2026-10-10');
      await page.getByLabel(label.to, { exact: true }).fill('2026-10-20');
      expect(calls.filter((url) => url.pathname === '/api/notes')).toHaveLength(1);
      await page.getByRole('button', { name: label.apply, exact: true }).focus();
      await page.keyboard.press('Enter');
      await expect(page.getByRole('heading', { name: label.results })).toBeFocused();
      await expect(page.locator('.history-note')).toHaveCount(1);
      await expect(page.getByRole('article', { name: otherRecipient.name, exact: true })).toBeVisible();
      const query = new URL(page.url()).searchParams;
      expect(Object.fromEntries(query)).toEqual({ from: '2026-10-10', to: '2026-10-20', recipientId: otherRecipient.id });
      await page.getByRole('radio', { name: label.day, exact: true }).check();
      await page.getByLabel(label.date, { exact: true }).fill('2026-10-19');
      await page.getByRole('button', { name: label.apply, exact: true }).click();
      await expect(page.locator('.history-note')).toHaveCount(1);
      expect(new URL(page.url()).searchParams.get('from')).toBe('2026-10-19');
      expect(new URL(page.url()).searchParams.get('to')).toBe('2026-10-19');
      await page.getByRole('button', { name: label.reset, exact: true }).click();
      await expect(page.locator('.history-note')).toHaveCount(2);
      await page.getByRole('article', { name: recipientFixture.name, exact: true }).getByRole('link', { name: label.open, exact: true }).click();
      await expect(page).toHaveURL(new RegExp(`/app/notes/${historyNote.id}$`));
      await page.goBack();
      await expect(page.getByRole('heading', { name: label.title, exact: true })).toBeVisible();
      await expect(page.locator('.history-note')).toHaveCount(2);
      await accessibility(page);
    });
    test('shows original fields and linked corrections accessibly without rewriting the original', async ({ page }, info) => {
      const calls = await serve(page);
      await page.goto(root);
      const card = page.getByRole('article', { name: recipientFixture.name, exact: true });
      await expect(card).toBeVisible();
      expect(calls.some((url) => url.pathname.endsWith('/addenda'))).toBe(false);
      await card.getByText(label.fields, { exact: true }).focus();
      await page.keyboard.press('Enter');
      await expect(card.locator('details')).toHaveAttribute('open', '');
      const originalText = card.locator('details .note-verbatim').last();
      await expect(originalText).toHaveJSProperty('textContent', historyNote.freeText);
      await expect(originalText).toHaveAttribute('dir', 'auto');
      await card.getByRole('button', { name: label.show, exact: true }).focus();
      await page.keyboard.press('Enter');
      await expect(card.getByRole('article', { name: label.correction, exact: true })).toBeVisible();
      await expect(card.getByText(label.original, { exact: true })).toBeVisible();
      await expect(originalText).toHaveJSProperty('textContent', historyNote.freeText);
      await expect(card.getByRole('article', { name: label.correction, exact: true }).locator('.note-verbatim').last()).toHaveJSProperty('textContent', addendumFixture.freeText);
      expect(calls.filter((url) => url.pathname.endsWith('/addenda'))).toHaveLength(1);
      await accessibility(page);
      await page.evaluate(() => {
        if (document.activeElement instanceof HTMLElement) document.activeElement.blur();
        window.scrollTo({ top: 0, behavior: 'instant' });
      });
      await page.screenshot({ path: info.outputPath(`history-${language}.png`), fullPage: true });
    });
    test('handles large results with server pages and page-size reset', async ({ page }) => {
      const notes = Array.from({ length: 41 }, (_, index) => ({ ...historyNote,
        id: `d4444444-4444-4444-8444-${String(index).padStart(12, '0')}`,
        date: `2026-10-${String(22 - Math.floor(index / 2)).padStart(2, '0')}`,
        recipientId: index % 2 ? otherRecipient.id : recipientFixture.id, addendumCount: 0,
      }));
      await serve(page, { notes });
      await page.goto(root);
      await expect(page.locator('.history-note')).toHaveCount(20);
      await expect(page.getByRole('button', { name: label.previous, exact: true })).toBeDisabled();
      await page.getByRole('button', { name: label.next, exact: true }).click();
      await expect(page.getByRole('heading', { name: label.results })).toBeFocused();
      expect(new URL(page.url()).searchParams.get('page')).toBe('1');
      await page.getByRole('button', { name: label.next, exact: true }).click();
      await expect(page.locator('.history-note')).toHaveCount(1);
      await expect(page.getByRole('button', { name: label.next, exact: true })).toBeDisabled();
      await page.getByLabel(label.size, { exact: true }).selectOption('50');
      await expect(page.locator('.history-note')).toHaveCount(41);
      expect(new URL(page.url()).searchParams.has('page')).toBe(false);
      await expect(page.getByRole('button', { name: label.next, exact: true })).toHaveCount(0);
      await accessibility(page);
    });
    test('validates dates and recovers from errors and empty results with retry/reset', async ({ page }) => {
      const options = { fail: true, failCorrections: true };
      await serve(page, options);
      await page.goto(root);
      await expect(page.getByRole('alert')).toBeVisible();
      await expect(page.getByText('private diagnostics')).toHaveCount(0);
      await accessibility(page);
      options.fail = false;
      await page.getByRole('button', { name: label.retry, exact: true }).click();
      const card = page.getByRole('article', { name: recipientFixture.name, exact: true });
      await expect(card).toBeVisible();
      await card.getByRole('button', { name: label.show, exact: true }).click();
      await expect(card.getByRole('alert')).toBeVisible();
      await accessibility(page);
      options.failCorrections = false;
      await card.getByRole('button', { name: label.retry, exact: true }).click();
      await expect(card.getByRole('article', { name: label.correction, exact: true })).toBeVisible();
      await page.getByLabel(label.from, { exact: true }).fill('2026-10-22');
      await page.getByLabel(label.to, { exact: true }).fill('2026-10-01');
      await page.getByRole('button', { name: label.apply, exact: true }).click();
      await expect(page.getByLabel(label.to, { exact: true })).toBeFocused();
      await accessibility(page);
      await page.getByLabel(label.to, { exact: true }).fill('2026-10-22');
      await page.getByRole('button', { name: label.apply, exact: true }).click();
      await expect(page.getByText(label.empty, { exact: true })).toBeVisible();
      await accessibility(page);
      await page.getByRole('button', { name: label.reset, exact: true }).click();
      await expect(page.locator('.history-note')).toHaveCount(2);
    });
  });
}
