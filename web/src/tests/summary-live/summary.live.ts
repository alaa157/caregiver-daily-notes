import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';

const backend = new URL(process.env.CAREGIVER_TEST_API_URL || 'http://127.0.0.1:8080').origin;
test.afterEach(async ({ page }) => {
  await page.unrouteAll({ behavior: 'wait' });
});

for (const language of ['en', 'ar'] as const) {
  test(`real authenticated summary, evidence, inclusive periods and safe provider fallback (${language})`, async ({ page, request }) => {
    const ar = language === 'ar'; const password = `Synthetic-${randomUUID()}`; const email = `summary-${randomUUID()}@example.com`;
    const registration = await request.post(`${backend}/api/auth/register`, { data: { email, password } }); expect(registration.status()).toBe(201);
    const headers = { Authorization: `Bearer ${(await registration.json()).token}` };
    const recipient = await request.post(`${backend}/api/recipients`, { headers, data: { name: 'Synthetic ملخص' } }); expect(recipient.status()).toBe(201);
    const recipientId = (await recipient.json()).id;
    const note = await request.post(`${backend}/api/notes`, { headers, data: { recipientId, date: '2026-10-26', freeText: 'Live source note عربي.', fallReported: true } }); expect(note.status()).toBe(201);
    const noteId = (await note.json()).id;
    const otherRegistration = await request.post(`${backend}/api/auth/register`, { data: { email: `summary-other-${randomUUID()}@example.com`, password } }); expect(otherRegistration.status()).toBe(201);
    const other = { Authorization: `Bearer ${(await otherRegistration.json()).token}` };
    expect((await request.post(`${backend}/api/summaries`, { headers: other, data: { recipientId, periodDays: 7, periodEndInclusive: '2026-10-26' } })).status()).toBe(404);
    await page.addInitScript((locale) => localStorage.setItem('caregiver-language', locale), language);
    await page.route('**/api/**', async (route) => { const url = new URL(route.request().url()); await route.fulfill({ response: await route.fetch({ url: `${backend}${url.pathname}${url.search}`, maxRedirects: 0 }) }); });
    await page.route('**/v3/api-docs', async (route) => { await route.fulfill({ response: await route.fetch({ url: `${backend}/v3/api-docs`, maxRedirects: 0 }) }); });
    await page.goto('/app/notes/summary'); await expect(page).toHaveURL(/\/login$/);
    await page.getByLabel(ar ? 'البريد الإلكتروني' : 'Email address', { exact: true }).fill(email);
    await page.getByLabel(ar ? 'كلمة المرور' : 'Password', { exact: true }).fill(password);
    await page.getByRole('button', { name: ar ? 'تسجيل الدخول' : 'Sign in', exact: true }).click();
    await page.getByLabel(ar ? 'المستفيد من الرعاية' : 'Care recipient', { exact: true }).selectOption(recipientId);
    await page.getByLabel(ar ? 'تاريخ النهاية (شامل)' : 'End date (inclusive)', { exact: true }).fill('2026-10-26');
    async function generate(name: string) {
      const completed = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/summaries' && response.request().method() === 'POST');
      await page.getByRole('button', { name, exact: true }).click();
      const response = await completed;
      expect(response.status()).toBe(200);
      await response.finished();
      await expect(page.getByRole('button', { name: ar ? 'إعادة إنشاء الملخص' : 'Regenerate summary', exact: true })).toBeEnabled();
    }
    for (const days of [7, 14, 30]) {
      const count = new Intl.NumberFormat(ar ? 'ar-EG' : 'en').format(days);
      await page.getByRole('radio', { name: `${count} ${ar ? 'أيام' : 'days'}`, exact: true }).check();
      await generate(days === 7 ? (ar ? 'إنشاء ملخص' : 'Generate summary') : (ar ? 'إعادة إنشاء الملخص' : 'Regenerate summary'));
      await expect(page.locator('blockquote')).toHaveText('Live source note عربي.');
      await expect(page.locator('.summary-evidence a')).toHaveAttribute('href', `/app/notes/${noteId}`);
      await expect(page.locator('.summary-safety')).toContainText('HIGH');
      await expect(page.getByRole('heading', { name: ar ? 'مراجعة مختص مطلوبة' : 'Clinical review requested' })).toBeVisible();
    }
    const correction = await request.post(`${backend}/api/notes/${noteId}/addenda`, { headers, data: { freeText: 'PROVIDER_UNAVAILABLE_FIXTURE' } }); expect(correction.status()).toBe(201);
    await generate(ar ? 'إعادة إنشاء الملخص' : 'Regenerate summary');
    await expect(page.locator('.summary-result')).toContainText(ar ? 'الذكاء الاصطناعي غير متاح.' : 'AI is unavailable.');
    await expect(page.locator('.summary-safety')).toContainText('HIGH'); await expect(page.locator('blockquote')).toHaveCount(0);
    await expect(page.locator('.summary-disclaimer')).toBeVisible(); expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  });
}
