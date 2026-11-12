import { expect, test, type Page } from '@playwright/test';
import { testToken } from '../src/tests/authFixtures';
import { correctionHistoryFixture, historyNote, historyPage } from '../src/tests/historyFixtures';
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
      if (path === `/api/notes/${note.id}`) {
        const addenda = note.addendumCount > 0 ? [{ ...addendumFixture, noteId: note.id }] : [];
        return route.fulfill({ json: { ...note, addenda } });
      }
      if (path === `/api/notes/${note.id}/addenda`) {
        return route.fulfill({ json: [{ ...addendumFixture, noteId: note.id }] });
      }
      if (path === `/api/notes/${note.id}/corrections`) {
        return route.fulfill({ json: { ...correctionHistoryFixture(), originalNote: { ...note, correctionCount: 0 } } });
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

async function expectNoHorizontalOverflow(page: Page, expectedWidth: number) {
  const layout = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
    viewportWidth: window.innerWidth,
    outside: [...document.querySelectorAll<HTMLElement>(
      'main input, main textarea, main select, main button, main a[href], main article, main h1, main h2, main p, main time, main .note-verbatim, main .history-pagination',
    )]
      .filter((element) => element.getClientRects().length && getComputedStyle(element).visibility !== 'hidden')
      .filter((element) => {
        const box = element.getBoundingClientRect();
        return box.left < -1 || box.right > innerWidth + 1;
      })
      .map((element) => element.id || element.textContent?.trim() || element.tagName),
  }));
  expect(layout.viewportWidth).toBe(expectedWidth);
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

  const sourceId = page.locator('.note-timeline-row').first().locator('.note-timeline-row__provenance bdi');
  await expect(sourceId).toHaveText(mixedNote.id);
  await expect(sourceId).toHaveJSProperty('textContent', mixedNote.id);
  const recipientName = page.locator('.note-timeline-row__metadata bdi');
  await expect(recipientName).toHaveText(recipientFixture.name);
  await expect(page.getByText('تصحيح مستقل — original stays unchanged.', { exact: true })).toHaveAttribute('dir', 'auto');
  await expect(page.locator('.note-timeline-row').first().locator('.note-timeline-row__date')).toHaveAttribute('dir', 'auto');
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
  await page.getByRole('group', {
    name: `${labels.ar.text} — حالة الملاحظة`,
    exact: true,
  }).getByText(labels.ar.recorded, { exact: true }).click();
  const text = page.getByRole('textbox', { name: labels.ar.text, exact: true });
  await expect(text).toBeVisible();
  await text.fill('ملاحظة عربية ضمن العرض المكتبي');
  await expectNoHorizontalOverflow(page, 320);

  await page.goto('/app/notes?from=2026-10-01&to=2026-10-22');
  await expect(page.getByLabel('من تاريخ', { exact: true })).toBeVisible();
  await expect(page.getByLabel('إلى تاريخ', { exact: true })).toBeVisible();
  const nextPage = page.getByRole('button', { name: 'الصفحة التالية', exact: true });
  await expect(nextPage).toBeVisible();
  await expectNoHorizontalOverflow(page, 320);
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

test('renders the Arabic note editor within a 1280px RTL viewport', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await installFixtures(page, 'ar');
  await page.goto('/app/notes/new');

  await expect(page.locator('html')).toHaveAttribute('lang', 'ar');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  expect(await page.locator('.note-form').evaluate((element) => getComputedStyle(element).direction)).toBe('rtl');
  await expect(page.getByLabel(labels.ar.recipient, { exact: true })).toBeVisible();
  await expect(page.getByLabel(labels.ar.date, { exact: true })).toBeVisible();
  await page.getByRole('group', {
    name: `${labels.ar.text} — حالة الملاحظة`,
    exact: true,
  }).getByText(labels.ar.recorded, { exact: true }).click();
  const text = page.getByRole('textbox', { name: labels.ar.text, exact: true });
  await expect(text).toBeVisible();
  await text.fill('ملاحظة عربية ضمن العرض المكتبي');
  await expect(text).toHaveValue('ملاحظة عربية ضمن العرض المكتبي');
  await expectNoHorizontalOverflow(page, 1280);
});

test('renders mixed note detail and history within a 1280px Arabic RTL viewport', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  const mixedText = 'ملاحظة الرعاية — NOTE-123 — English details.';
  const mixedNote = {
    ...historyNote,
    freeText: mixedText,
    observations: {
      ...historyNote.observations,
      freeText: { state: 'RECORDED' as const, value: mixedText },
    },
  };
  await installFixtures(page, 'ar', mixedNote);
  await page.goto(`/app/notes/${mixedNote.id}`);

  await expect(page.locator('html')).toHaveAttribute('lang', 'ar');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  const detailText = page.getByText(mixedText, { exact: true });
  await expect(detailText).toHaveAttribute('dir', 'auto');
  await expect(detailText).toHaveJSProperty('textContent', mixedText);
  await expect(page.locator('.note-timeline-row').first().locator('.note-timeline-row__provenance bdi')).toHaveText(mixedNote.id);
  await expectNoHorizontalOverflow(page, 1280);

  await page.goto('/app/notes?from=2026-10-01&to=2026-10-22');
  await expect(page.getByRole('heading', { name: labels.ar.history })).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  const card = page.getByRole('article', { name: recipientFixture.name });
  await expect(card).toBeVisible();
  await card.getByText('عرض المشاهدات والنص الأصلي', { exact: true }).click();
  const historyText = card.getByText(mixedText, { exact: true });
  await expect(historyText).toHaveAttribute('dir', 'auto');
  await expect(historyText).toHaveJSProperty('textContent', mixedText);
  await expect(card.locator('.note-timeline-row__provenance bdi')).toHaveText(mixedNote.id);
  await expectNoHorizontalOverflow(page, 1280);
});

test('shows locked original before corrections across viewport widths', async ({ page }) => {
  const mixedNote = historyNote;
  await installFixtures(page, 'ar', mixedNote);
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.goto(`/app/notes/${mixedNote.id}`);

  const original = page.locator('.note-detail-original');
  const corrections = page.locator('.note-detail-secondary');
  await expect(original.getByText('مقفل — الملاحظة الأصلية للقراءة فقط')).toBeVisible();
  await expect(corrections.getByText('لا توجد تصحيحات منظّمة بعد.')).toBeVisible();
  const desktopBoxes = await Promise.all([
    original.boundingBox(),
    corrections.boundingBox(),
  ]);
  expect(desktopBoxes[0]).not.toBeNull();
  expect(desktopBoxes[1]).not.toBeNull();
  expect(Math.abs((desktopBoxes[0]?.y ?? 0) - (desktopBoxes[1]?.y ?? 0))).toBeLessThan(2);
  expect(
    (desktopBoxes[0]?.x ?? 0) + (desktopBoxes[0]?.width ?? 0) <= (desktopBoxes[1]?.x ?? 0)
      || (desktopBoxes[1]?.x ?? 0) + (desktopBoxes[1]?.width ?? 0) <= (desktopBoxes[0]?.x ?? 0),
  ).toBe(true);

  for (const width of [768, 390, 320]) {
    await page.setViewportSize({ width, height: 900 });
    const mobileBoxes = await Promise.all([
      original.boundingBox(),
      corrections.boundingBox(),
    ]);
    expect(mobileBoxes[0]).not.toBeNull();
    expect(mobileBoxes[1]).not.toBeNull();
    expect((mobileBoxes[0]?.y ?? 0) + (mobileBoxes[0]?.height ?? 0)).toBeLessThanOrEqual(mobileBoxes[1]?.y ?? 0);
    const documentWidth = await page.evaluate(() => ({
      scroll: document.documentElement.scrollWidth,
      client: document.documentElement.clientWidth,
    }));
    expect(documentWidth.scroll).toBe(documentWidth.client);
    for (const box of mobileBoxes) {
      expect(box?.x).toBeGreaterThanOrEqual(0);
      expect((box?.x ?? 0) + (box?.width ?? 0)).toBeLessThanOrEqual(width);
    }
  }
});

test('restores English LTR note and history layouts at 1280px', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await installFixtures(page, 'ar');
  await page.goto('/app/notes/new');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await page.getByRole('button', { name: labels.ar.switch, exact: true }).click();

  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
  expect(await page.locator('.note-form').evaluate((element) => getComputedStyle(element).direction)).toBe('ltr');
  await expectNoHorizontalOverflow(page, 1280);

  await page.goto('/app/notes?from=2026-10-01&to=2026-10-22');
  await expect(page.getByRole('heading', { name: labels.en.history })).toBeVisible();
  await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
  expect(await page.locator('.history-filters').evaluate((element) => getComputedStyle(element).direction)).toBe('ltr');
  await expectNoHorizontalOverflow(page, 1280);
});
