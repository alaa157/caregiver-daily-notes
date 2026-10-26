import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';

const backend = new URL(process.env.CAREGIVER_TEST_API_URL || 'http://127.0.0.1:8080').origin;
test.afterEach(async ({ page }) => {
  await page.unrouteAll({ behavior: 'wait' });
});

for (const language of ['en', 'ar'] as const) {
  test(`real AI-plan generation, protected edit, preserved history and backend transitions (${language})`, async ({ page, request }) => {
    const ar = language === 'ar'; const password = `Synthetic-${randomUUID()}`; const email = `plans-${randomUUID()}@example.com`;
    const registered = await request.post(`${backend}/api/auth/register`, { data: { email, password } }); expect(registered.status()).toBe(201);
    const owner = { Authorization: `Bearer ${(await registered.json()).token}` };
    const otherRegistered = await request.post(`${backend}/api/auth/register`, { data: { email: `other-${randomUUID()}@example.com`, password } }); expect(otherRegistered.status()).toBe(201);
    const other = { Authorization: `Bearer ${(await otherRegistered.json()).token}` };
    const recipient = await request.post(`${backend}/api/recipients`, { headers: owner, data: { name: 'Synthetic خطط' } }); expect(recipient.status()).toBe(201);
    const recipientId = (await recipient.json()).id;
    const base = `${backend}/api/recipients/${recipientId}/plans`;
    const note = await request.post(`${backend}/api/notes`, { headers: owner, data: { recipientId, date: '2026-10-26', freeText: 'Live plan source عربي.', fallReported: true } }); expect(note.status()).toBe(201);
    await page.addInitScript((locale) => localStorage.setItem('caregiver-language', locale), language);
    await page.route('**/api/**', async (route) => { const url = new URL(route.request().url()); await route.fulfill({ response: await route.fetch({ url: `${backend}${url.pathname}${url.search}`, maxRedirects: 0 }) }); });
    await page.goto(`/app/notes/plans?recipientId=${recipientId}`); await expect(page).toHaveURL(/\/login$/);
    await page.getByLabel(ar ? 'البريد الإلكتروني' : 'Email address', { exact: true }).fill(email);
    await page.getByLabel(ar ? 'كلمة المرور' : 'Password', { exact: true }).fill(password);
    await page.getByRole('button', { name: ar ? 'تسجيل الدخول' : 'Sign in', exact: true }).click();
    const suggestions = page.getByRole('region', { name: ar ? 'الخطط المقترحة' : 'Suggested plans', exact: true });
    const active = page.getByRole('region', { name: ar ? 'الخطة المقبولة النشطة' : 'Active accepted plan', exact: true });
    const label = {
      generate: ar ? 'طلب اقتراح خطة' : 'Request plan suggestion', edit: ar ? 'التعديل والقبول' : 'Edit and accept',
      review: ar ? 'مراجعة التغييرات' : 'Review changes', confirmEdit: ar ? 'تأكيد التعديل والقبول' : 'Confirm edit and acceptance',
      accept: ar ? 'قبول الاقتراح' : 'Accept suggestion', confirmAccept: ar ? 'تأكيد القبول' : 'Confirm acceptance',
      dismiss: ar ? 'رفض الاقتراح' : 'Dismiss suggestion', confirmDismiss: ar ? 'تأكيد الرفض' : 'Confirm dismissal',
      reload: ar ? 'إعادة تحميل الخطط المحفوظة' : 'Reload saved plans',
    };
    async function generate() {
      await page.getByLabel(ar ? 'تاريخ النهاية (شامل)' : 'End date (inclusive)', { exact: true }).fill('2026-10-26');
      await page.getByRole('button', { name: label.generate, exact: true }).click();
      await expect(suggestions.getByRole('button', { name: label.edit, exact: true })).toBeEnabled();
      const history = await request.get(`${base}/history`, { headers: owner }); expect(history.status()).toBe(200);
      return (await history.json())[0];
    }
    const original = await generate();
    await expect(page.locator('.plan-generation-safety .summary-safety')).toContainText('HIGH');
    expect(original.sourceType).toBe('AI_GENERATED'); expect(original.sourceSummaryId).toMatch(/^summary-sha256:/);
    await suggestions.getByRole('button', { name: label.edit, exact: true }).click();
    const days = page.getByLabel(ar ? 'خلال عدد أيام' : 'Within days', { exact: true }); await expect(days).toBeFocused(); await days.fill('10');
    await page.getByRole('button', { name: label.review, exact: true }).click(); await page.getByRole('button', { name: label.confirmEdit, exact: true }).click();
    await expect(active).toContainText(ar ? 'إصدار عدّله مقدّم الرعاية' : 'Caregiver-edited version');
    await active.getByRole('link', { name: original.id, exact: true }).click();
    const preserved = page.locator(`#plan-history-${original.id}`); await expect(preserved).toHaveAttribute('open', '');
    await expect(preserved).toContainText(ar ? 'الأصل المنشأ بالذكاء الاصطناعي' : 'AI-generated original');
    await expect(preserved.locator('summary .plan-status')).toHaveText(ar ? 'مؤرشف' : 'Archived');
    const afterEdit = await (await request.get(`${base}/history`, { headers: owner })).json();
    expect(afterEdit[0].structuredContent.medications).toEqual(original.structuredContent.medications);
    expect(afterEdit[0].structuredContent.changes[0].params).toEqual({ withinDays: '10' });
    expect(afterEdit.find((item: { id: string }) => item.id === original.id).content).toBe(original.content);
    const next = await generate();
    await suggestions.getByRole('button', { name: label.accept, exact: true }).click(); await page.getByRole('button', { name: label.confirmAccept, exact: true }).click();
    await expect(active.locator('.plan-status')).toHaveText(ar ? 'مقبول' : 'Accepted');
    const dismissed = await generate(); await suggestions.getByRole('button', { name: label.dismiss, exact: true }).click(); await page.getByRole('button', { name: label.confirmDismiss, exact: true }).click();
    await expect(page.locator(`#plan-history-${dismissed.id} summary .plan-status`)).toHaveText(ar ? 'مرفوض' : 'Dismissed');
    const stale = await generate(); expect((await request.post(`${base}/${stale.id}/accept`, { headers: owner })).status()).toBe(200);
    await suggestions.getByRole('button', { name: label.accept, exact: true }).click(); await page.getByRole('button', { name: label.confirmAccept, exact: true }).click();
    await expect(page.getByRole('alert')).toContainText(ar ? 'لم يعد هذا الإجراء' : 'can no longer use that action');
    await expect(suggestions.getByRole('button', { name: label.accept, exact: true })).toBeDisabled();
    await page.getByRole('button', { name: label.reload, exact: true }).click();
    const pending = await generate();
    const editPayload = { expectedVersion: pending.version, changes: [{ index: 0, params: { withinDays: '12' } }] };
    expect((await request.post(`${base}/${pending.id}/edit-and-accept`, { headers: owner, data: { ...editPayload, medications: [] } })).status()).toBe(422);
    expect((await request.post(`${base}/${pending.id}/edit-and-accept`, { headers: other, data: editPayload })).status()).toBe(404);
    expect((await request.get(`${base}/history`, { headers: other })).status()).toBe(404);
    expect((await request.post(`${base}/${next.id}/accept`, { headers: other })).status()).toBe(404);
    expect((await request.patch(`${backend}/api/recipients/${recipientId}/deactivate`, { headers: owner })).status()).toBe(200);
    await page.getByRole('button', { name: label.reload, exact: true }).click(); await expect(suggestions.getByRole('button', { name: label.edit, exact: true })).toBeDisabled();
    await expect(page.getByRole('button', { name: label.generate, exact: true })).toBeDisabled();
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  });
}
