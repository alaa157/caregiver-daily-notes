import { expect, test, type Page } from '@playwright/test';
import { testToken } from '../src/tests/authFixtures';
import { historyNote, historyPage } from '../src/tests/historyFixtures';
import { addendumFixture, noteFixture } from '../src/tests/noteFixtures';
import { recipientFixture } from '../src/tests/recipientFixtures';

type Locale = 'en' | 'ar';

const labels = {
  en: {
    recipient: 'Care recipient',
    date: 'Observation date',
    text: 'Free-text observations',
    recorded: 'Recorded',
    history: 'Notes history',
    switch: 'التغيير إلى العربية',
  },
  ar: {
    recipient: 'مستفيد الرعاية',
    date: 'تاريخ الملاحظة',
    text: 'ملاحظات نصية حرة',
    recorded: 'مسجّل',
    history: 'سجل الملاحظات',
    switch: 'Switch to English',
  },
};

async function installFixtures(page: Page, locale: Locale, note = historyNote, totalElements = 1) {
  const token = testToken();
  await page.addInitScript(({ initialLocale, sessionToken }) => {
    if (!localStorage.getItem('caregiver-language')) localStorage.setItem('caregiver-language', initialLocale);
    sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: sessionToken, baseUrl: '' }));
  }, { initialLocale: locale, sessionToken: token });

  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    expect(request.headers().authorization).toBe(`Bearer ${token}`);

    if (request.method() === 'GET') {
      if (path === '/api/recipients') return route.fulfill({ json: [recipientFixture] });
      if (path === `/api/recipients/${recipientFixture.id}`) return route.fulfill({ json: recipientFixture });
      if (path === `/api/notes/${note.id}`) return route.fulfill({ json: note });
      if (path === `/api/notes/${note.id}/addenda`) {
        return route.fulfill({ json: [{ ...addendumFixture, noteId: note.id }] });
      }
      if (path === `/api/notes/${note.id}/corrections`) {
        return route.fulfill({
          json: {
            originalNote: note,
            events: [],
            effectiveObservations: {},
          },
        });
      }
      if (path === '/api/notes') {
        const pageNumber = Number(url.searchParams.get('page') ?? 0);
        const size = Number(url.searchParams.get('size') ?? 20);
        return route.fulfill({ json: historyPage([note], pageNumber, size, totalElements) });
      }
    }

    return route.fulfill({ status: 501, json: { code: 'UNEXPECTED_TEST_REQUEST' } });
  });
}

async function expectNoHorizontalOverflow(page: Page) {
  const layout = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
    outside: [...document.querySelectorAll<HTMLElement>('main input, main textarea, main select, main button, main a[href]')]
      .filter((element) => element.getClientRects().length && getComputedStyle(element).visibility !== 'hidden')
      .filter((element) => {
        const box = element.getBoundingClientRect();
        return box.left < -1 || box.right > innerWidth + 1;
      })
      .map((element) => element.id || element.textContent?.trim() || element.tagName),
  }));
  expect(layout.scrollWidth).toBe(layout.clientWidth);
  expect(layout.outside).toEqual([]);
}

test('renders note editor fields in Arabic RTL', async ({ page }) => {
  await installFixtures(page, 'ar');
  await page.goto('/app/notes/new');

  await expect(page.locator('html')).toHaveAttribute('lang', 'ar');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  const recipient = page.getByLabel(labels.ar.recipient, { exact: true });
  const date = page.getByLabel(labels.ar.date, { exact: true });
  await expect(recipient).toBeVisible();
  await expect(recipient).toHaveAttribute('dir', 'auto');
  await expect(date).toBeVisible();
  await expect(date).toHaveAttribute('dir', 'ltr');
  await recipient.selectOption(recipientFixture.id);
  await date.fill(noteFixture.date);

  const textGroup = page.getByRole('group', {
    name: `${labels.ar.text} — حالة الملاحظة`,
    exact: true,
  });
  await textGroup.getByText(labels.ar.recorded, { exact: true }).click();
  const text = page.getByRole('textbox', { name: labels.ar.text, exact: true });
  await text.fill('ملاحظة عربية قابلة للتحرير');
  await expect(text).toHaveValue('ملاحظة عربية قابلة للتحرير');
});

test('preserves mixed Arabic and Latin note text order', async ({ page }) => {
  const mixedText = 'ملاحظة الرعاية — NOTE-123 — English details.';
  const mixedNote = {
    ...historyNote,
    freeText: mixedText,
    observations: {
      ...noteFixture.observations,
      freeText: { state: 'RECORDED' as const, value: mixedText },
    },
  };
  await installFixtures(page, 'ar', mixedNote);
  await page.goto(`/app/notes/${mixedNote.id}`);

  const renderedText = page.getByText(mixedText, { exact: true });
  await expect(renderedText).toHaveAttribute('dir', 'auto');
  await expect(renderedText).toHaveJSProperty('textContent', mixedText);
  expect(await renderedText.evaluate((element) => getComputedStyle(element).direction)).toBe('rtl');

  const sourceId = page.locator('.note-timeline-row__provenance bdi');
  await expect(sourceId).toHaveText(mixedNote.id);
  await expect(sourceId).toHaveJSProperty('textContent', mixedNote.id);
  const recipientName = page.locator('.note-timeline-row__metadata bdi');
  await expect(recipientName).toHaveText(recipientFixture.name);
  await expect(page.getByText('تصحيح مستقل — original stays unchanged.', { exact: true })).toHaveAttribute('dir', 'auto');
  await expect(page.locator('.note-timeline-row__date')).toHaveAttribute('dir', 'auto');
});

test('keeps history date query values unchanged in Arabic', async ({ page }) => {
  await installFixtures(page, 'en');
  await page.goto('/app/notes?from=2026-10-01&to=2026-10-22');
  await expect(page.getByRole('article', { name: recipientFixture.name })).toBeVisible();
  await page.getByRole('button', { name: labels.en.switch, exact: true }).click();

  await expect(page.locator('html')).toHaveAttribute('lang', 'ar');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  const url = new URL(page.url());
  expect(url.searchParams.get('from')).toBe('2026-10-01');
  expect(url.searchParams.get('to')).toBe('2026-10-22');
  await expect(page.getByRole('heading', { name: labels.ar.history })).toBeVisible();
  await expect(page.getByRole('article', { name: recipientFixture.name })).toBeVisible();
  await expect(page.locator('.history-note time')).toHaveAttribute('dir', 'auto');
});

test('does not horizontally overflow notes or history at 320 pixels', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 900 });
  await installFixtures(page, 'ar', historyNote, 41);
  await page.goto('/app/notes/new');
  await expect(page.getByLabel(labels.ar.recipient, { exact: true })).toBeVisible();
  await expect(page.getByLabel(labels.ar.date, { exact: true })).toBeVisible();
  await expect(page.getByRole('textbox', { name: labels.ar.text, exact: true })).toBeVisible();
  await expectNoHorizontalOverflow(page);

  await page.goto('/app/notes?from=2026-10-01&to=2026-10-22');
  await expect(page.getByLabel('من تاريخ', { exact: true })).toBeVisible();
  await expect(page.getByLabel('إلى تاريخ', { exact: true })).toBeVisible();
  const nextPage = page.getByRole('button', { name: 'الصفحة التالية', exact: true });
  await expect(nextPage).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await nextPage.click();
  const url = new URL(page.url());
  expect(url.searchParams.get('from')).toBe('2026-10-01');
  expect(url.searchParams.get('to')).toBe('2026-10-22');
  expect(url.searchParams.get('page')).toBe('1');
});

test('restores LTR layout after switching back to English', async ({ page }) => {
  await installFixtures(page, 'ar');
  await page.goto('/app/notes/new');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await page.getByRole('button', { name: labels.ar.switch, exact: true }).click();

  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
  expect(await page.locator('.note-form').evaluate((element) => getComputedStyle(element).direction)).toBe('ltr');

  await page.goto('/app/notes?from=2026-10-01&to=2026-10-22');
  await expect(page.getByRole('heading', { name: labels.en.history })).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
  expect(await page.locator('.history-filters').evaluate((element) => getComputedStyle(element).direction)).toBe('ltr');
});
