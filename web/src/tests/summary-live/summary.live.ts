import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';
import { liveByokCredentials, seedByokCredentials } from '../live-byok';

const backend = new URL(process.env.CAREGIVER_TEST_API_URL || 'http://127.0.0.1:8080').origin;
const byokCredentials = liveByokCredentials();
test.skip(!byokCredentials, 'Set CAREGIVER_E2E_BYOK_PROVIDER/_MODEL/_KEY to run live BYOK generation.');
test.afterEach(async ({ page }) => {
  await page.unrouteAll({ behavior: 'wait' });
});

function byokBody(recipientId: string) {
  return {
    summary: { recipientId, periodEndInclusive: '2026-10-26', periodDays: 7 },
    credentials: { providerId: byokCredentials!.providerId, model: byokCredentials!.model, apiKey: byokCredentials!.apiKey },
  };
}

for (const language of ['en', 'ar'] as const) {
  test(`real authenticated BYOK summary, evidence and inclusive periods (${language})`, async ({ page, request }) => {
    const ar = language === 'ar'; const password = `Synthetic-${randomUUID()}`; const email = `summary-${randomUUID()}@example.com`;
    const registration = await request.post(`${backend}/api/auth/register`, { data: { email, password } }); expect(registration.status()).toBe(201);
    const headers = { Authorization: `Bearer ${(await registration.json()).token}` };
    const recipient = await request.post(`${backend}/api/recipients`, { headers, data: { name: 'Synthetic ملخص' } }); expect(recipient.status()).toBe(201);
    const recipientId = (await recipient.json()).id;
    const note = await request.post(`${backend}/api/notes`, { headers, data: { recipientId, date: '2026-10-26', freeText: 'Live source note عربي.', fallReported: true } }); expect(note.status()).toBe(201);
    const noteId = (await note.json()).id;
    const otherRegistration = await request.post(`${backend}/api/auth/register`, { data: { email: `summary-other-${randomUUID()}@example.com`, password } }); expect(otherRegistration.status()).toBe(201);
    const other = { Authorization: `Bearer ${(await otherRegistration.json()).token}` };
    expect((await request.post(`${backend}/api/summaries/byok`, { headers: other, data: byokBody(recipientId) })).status()).toBe(404);
    expect((await request.post(`${backend}/api/summaries/byok`, { data: byokBody(recipientId) })).status()).toBe(401);
    await page.addInitScript((locale) => localStorage.setItem('caregiver-language', locale), language);
    await seedByokCredentials(page);
    await page.route('**/api/**', async (route) => { const url = new URL(route.request().url()); await route.fulfill({ response: await route.fetch({ url: `${backend}${url.pathname}${url.search}`, maxRedirects: 0 }) }); });
    await page.goto('/app/notes/summary'); await expect(page).toHaveURL(/\/login$/);
    await page.getByLabel(ar ? 'البريد الإلكتروني' : 'Email address', { exact: true }).fill(email);
    await page.getByLabel(ar ? 'كلمة المرور' : 'Password', { exact: true }).fill(password);
    await page.getByRole('button', { name: ar ? 'تسجيل الدخول' : 'Sign in', exact: true }).click();
    await page.getByLabel(ar ? 'المستفيد من الرعاية' : 'Care recipient', { exact: true }).selectOption(recipientId);
    await page.getByLabel(ar ? 'تاريخ النهاية (شامل)' : 'End date (inclusive)', { exact: true }).fill('2026-10-26');
    async function generate(name: string) {
      const completed = page.waitForResponse((response) => new URL(response.url()).pathname === '/api/summaries/byok' && response.request().method() === 'POST');
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
      await expect(page.locator('blockquote').first()).not.toBeEmpty();
      await expect(page.locator('.summary-evidence a').first()).toHaveAttribute('href', `/app/notes/${noteId}`);
      await expect(page.locator('.summary-safety')).toBeVisible();
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  });
}
