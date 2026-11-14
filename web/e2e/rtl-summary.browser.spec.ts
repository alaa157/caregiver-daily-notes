import { expect, test, type Page, type TestInfo } from '@playwright/test';
import { testToken } from '../src/tests/authFixtures';
import { recipientFixture } from '../src/tests/recipientFixtures';
import { summaryFixture } from '../src/tests/summaryFixtures';

const noteId = 'c3333333-3333-4333-8333-333333333333';
const observationText = 'تم تسجيل سقوط في الساعة 10:30 — NOTE-123 (review pending)';
const evidenceQuote = 'تمت المراجعة الساعة 10:30 — NOTE-123 (review pending)';
const safetyReason = 'تنبيه FALL_DETECTED — review 2 notes.';
const credentials = { providerId: 'GEMINI' as const, model: 'gemini-e2e-model', apiKey: 'synthetic-e2e-summary-key' };

type Locale = 'en' | 'ar';

function rtlFixture() {
  const fixture = summaryFixture();
  fixture.summary.observations = [{ text: observationText, noteId, quote: evidenceQuote }];
  fixture.summary.uncertainties = [{
    topic: 'موعد / FOLLOW_UP',
    detail: 'التفاصيل غير مؤكدة؛ review pending for ID ABC-9 at 10:30.',
  }];
  fixture.safety.signals = [{ ruleId: 'FALL_DETECTED', severity: 'HIGH', reason: safetyReason }];
  Object.assign(fixture.trends[0], {
    previousCount: 3,
    previousSampleSize: 4,
    currentCount: 1,
    currentSampleSize: 2,
    delta: -2,
  });
  return fixture;
}

async function renderSummary(page: Page, locale: Locale) {
  const unexpectedRequests: string[] = [];
  const pageErrors: string[] = [];
  page.on('pageerror', (error) => pageErrors.push(error.message));
  await page.addInitScript(({ language, token, byok }) => {
    localStorage.setItem('caregiver-language', language);
    sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token, baseUrl: '' }));
    window.__E2E_BYOK_CREDENTIALS__ = byok;
  }, { language: locale, token: testToken(), byok: credentials });

  await page.route('**/v3/api-docs', async (route) => {
    unexpectedRequests.push(`${route.request().method()} /v3/api-docs`);
    await route.fulfill({ status: 501, json: { code: 'UNEXPECTED_TEST_REQUEST' } });
  });
  await page.route('**/api/**', async (route) => {
    const request = route.request();
    const { pathname } = new URL(request.url());
    if (!request.headers().authorization?.startsWith('Bearer ')) {
      unexpectedRequests.push(`${request.method()} ${pathname} (missing auth)`);
      await route.fulfill({ status: 401, json: { code: 'UNEXPECTED_TEST_REQUEST' } });
      return;
    }
    if (request.method() === 'GET' && pathname === '/api/recipients') {
      await route.fulfill({ json: [recipientFixture] });
      return;
    }
    if (request.method() === 'GET' && pathname === `/api/recipients/${recipientFixture.id}/safety-acknowledgements`) {
      await route.fulfill({ json: [] });
      return;
    }
    if (request.method() === 'POST' && pathname === '/api/summaries/byok') {
      await route.fulfill({ json: rtlFixture() });
      return;
    }
    unexpectedRequests.push(`${request.method()} ${pathname}`);
    await route.fulfill({ status: 501, json: { code: 'UNEXPECTED_TEST_REQUEST' } });
  });

  await page.goto('/app/notes/summary');
  await expect(page.locator('html')).toHaveAttribute('lang', locale);
  await expect(page.locator('html')).toHaveAttribute('dir', locale === 'ar' ? 'rtl' : 'ltr');
  await page.getByLabel(locale === 'ar' ? 'المستفيد من الرعاية' : 'Care recipient', { exact: true }).selectOption(recipientFixture.id);
  await page.getByLabel(locale === 'ar' ? 'تاريخ النهاية (شامل)' : 'End date (inclusive)', { exact: true }).fill('2026-10-24');
  await page.getByRole('button', { name: locale === 'ar' ? 'إنشاء ملخص' : 'Generate summary', exact: true }).click();
  await expect(page.getByRole('region', { name: locale === 'ar' ? 'الملخص المطلوب' : 'Requested summary' })).toBeVisible();
  await expect(page.getByRole('region', { name: locale === 'ar' ? 'معلومات السلامة' : 'Safety information' })).toBeVisible();
  return { unexpectedRequests, pageErrors };
}

async function expectFitsViewport(page: Page) {
  const layout = await page.evaluate(() => {
    const width = document.documentElement.clientWidth;
    const selectors = [
      '.summary-result', '.summary-ai', '.summary-evidence', '.summary-uncertainties',
      '.summary-trends', '.summary-safety',
    ];
    const elements = selectors.flatMap((selector) => Array.from(document.querySelectorAll<HTMLElement>(selector)))
      .map((element) => {
        const bounds = element.getBoundingClientRect();
        return {
          selector: element.className,
          left: bounds.left,
          right: bounds.right,
          scrollWidth: element.scrollWidth,
          clientWidth: element.clientWidth,
        };
      });
    return { width, documentWidth: document.documentElement.scrollWidth, elements };
  });
  expect(layout.documentWidth).toBeLessThanOrEqual(layout.width);
  for (const element of layout.elements) {
    expect(element.left, element.selector).toBeGreaterThanOrEqual(-1);
    expect(element.right, element.selector).toBeLessThanOrEqual(layout.width + 1);
    expect(element.scrollWidth, element.selector).toBeLessThanOrEqual(element.clientWidth + 1);
  }
}

function onlyProject(info: TestInfo, project: 'desktop' | 'compact-mobile') {
  test.skip(info.project.name !== project, `This viewport check runs in the ${project} project.`);
}

test('renders summary sections in Arabic RTL at mobile width', async ({ page }, info) => {
  onlyProject(info, 'compact-mobile');
  await page.setViewportSize({ width: 320, height: 740 });
  const { unexpectedRequests, pageErrors } = await renderSummary(page, 'ar');

  await expect(page.locator('html')).toHaveAttribute('lang', 'ar');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await expect(page.getByText(observationText, { exact: true })).toBeVisible();
  await expect(page.getByRole('region', { name: 'معلومات غير واضحة أو متعارضة' })).toBeVisible();
  await expect(page.getByRole('region', { name: 'أعداد المشاهدات المسجلة' })).toBeVisible();
  await expect(page.getByRole('region', { name: 'معلومات السلامة' })).toContainText('FALL_DETECTED');
  await expectFitsViewport(page);
  expect(unexpectedRequests).toEqual([]);
  expect(pageErrors).toEqual([]);
});

test('keeps date range and evidence links readable in RTL', async ({ page }, info) => {
  onlyProject(info, 'compact-mobile');
  await page.setViewportSize({ width: 320, height: 740 });
  const { unexpectedRequests, pageErrors } = await renderSummary(page, 'ar');
  const result = page.getByRole('region', { name: 'الملخص المطلوب' });

  for (const date of ['2026-10-18', '2026-10-24']) {
    const value = result.getByText(date, { exact: true });
    await expect(value).toBeVisible();
    await expect(value).toHaveJSProperty('textContent', date);
    await expect(value).toHaveJSProperty('tagName', 'BDI');
  }
  const quote = page.getByRole('blockquote');
  await expect(quote).toBeVisible();
  await expect(quote).toHaveJSProperty('textContent', evidenceQuote);
  await expect(quote).toHaveAttribute('dir', 'auto');
  const sourceLink = page.getByRole('link', { name: /الملاحظة المصدر/ });
  await expect(sourceLink).toBeVisible();
  await expect(sourceLink).toHaveAttribute('href', `/app/notes/${noteId}`);
  const sourceId = sourceLink.locator('bdi');
  await expect(sourceId).toHaveJSProperty('textContent', noteId);
  await expectFitsViewport(page);
  expect(unexpectedRequests).toEqual([]);
  expect(pageErrors).toEqual([]);
});

test('restores summary LTR layout in English', async ({ page }, info) => {
  onlyProject(info, 'compact-mobile');
  await page.setViewportSize({ width: 320, height: 740 });
  const { unexpectedRequests, pageErrors } = await renderSummary(page, 'ar');
  await page.getByRole('button', { name: 'Switch to English', exact: true }).click();

  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
  await expect(page.locator('.summary-result')).toHaveCSS('direction', 'ltr');
  await expect(page.getByRole('region', { name: 'Summary observations' })).toBeVisible();
  await expect(page.getByText(observationText, { exact: true })).toBeVisible();
  await expectFitsViewport(page);
  expect(unexpectedRequests).toEqual([]);
  expect(pageErrors).toEqual([]);
});

test('keeps Arabic summary layout readable on desktop', async ({ page }, info) => {
  onlyProject(info, 'desktop');
  await page.setViewportSize({ width: 1280, height: 900 });
  const { unexpectedRequests, pageErrors } = await renderSummary(page, 'ar');

  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await expect(page.locator('.summary-result')).toHaveCSS('direction', 'rtl');
  await expect(page.getByText(observationText, { exact: true })).toBeVisible();
  const trends = page.getByRole('region', { name: 'أعداد المشاهدات المسجلة' });
  const values = await trends.locator('dl > div').evaluateAll((periods) =>
    periods.map((period) => Array.from(period.querySelectorAll('dd bdi'), (value) => value.textContent)));
  const formatted = await page.evaluate(() => [3, 4, 1, 2, -2].map((value) => new Intl.NumberFormat('ar-EG').format(value)));
  expect(values).toEqual([[formatted[0], formatted[1]], [formatted[2], formatted[3]], [formatted[4]]]);
  const severity = page.locator('.summary-safety .summary-severity bdi');
  await expect(severity).toHaveText('HIGH');
  await expect(page.locator('.summary-safety .summary-untrusted')).toHaveJSProperty('textContent', safetyReason);
  await expectFitsViewport(page);
  expect(unexpectedRequests).toEqual([]);
  expect(pageErrors).toEqual([]);
});

test('keeps English summary readable at mobile width', async ({ page }, info) => {
  onlyProject(info, 'compact-mobile');
  await page.setViewportSize({ width: 320, height: 740 });
  const { unexpectedRequests, pageErrors } = await renderSummary(page, 'en');

  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
  await expect(page.getByText(observationText, { exact: true })).toBeVisible();
  await expect(page.getByRole('blockquote')).toHaveJSProperty('textContent', evidenceQuote);
  await expect(page.getByRole('region', { name: 'Recorded observation counts' })).toBeVisible();
  await expectFitsViewport(page);
  expect(unexpectedRequests).toEqual([]);
  expect(pageErrors).toEqual([]);
});
