import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';

const backend = new URL(process.env.CAREGIVER_TEST_API_URL || 'http://127.0.0.1:8080').origin;
for (const language of ['en', 'ar'] as const) {
  test(`real plan decisions, preserved version history and caregiver isolation (${language})`, async ({ page, request }) => {
    const ar = language === 'ar';
    const password = `Synthetic-${randomUUID()}`;
    const email = `plans-${randomUUID()}@example.com`;
    const register = await request.post(`${backend}/api/auth/register`, { data: { email, password } }); expect(register.status()).toBe(201);
    const owner = { Authorization: `Bearer ${(await register.json()).token}` };
    const otherRegister = await request.post(`${backend}/api/auth/register`, { data: { email: `plans-other-${randomUUID()}@example.com`, password } }); expect(otherRegister.status()).toBe(201);
    const other = { Authorization: `Bearer ${(await otherRegister.json()).token}` };
    const recipient = await request.post(`${backend}/api/recipients`, { headers: owner, data: { name: 'Synthetic مستفيد', relationship: null } }); expect(recipient.status()).toBe(201);
    const recipientId = (await recipient.json()).id as string;
    const base = `${backend}/api/recipients/${recipientId}/plans`;
    const note = await request.post(`${backend}/api/notes`, { headers: owner, data: { recipientId, date: '2026-10-20', freeText: 'Synthetic supporting note فقط.' } }); expect(note.status()).toBe(201);
    const noteId = (await note.json()).id as string;
    async function suggest(content: string) {
      const response = await request.post(`${base}/suggestions`, { headers: owner, data: { content, sourceSummaryId: 'synthetic-summary-reference', evidenceNoteIds: [noteId] } });
      expect(response.status()).toBe(201); return (await response.json()).id as string;
    }
    const first = await suggest('First saved routine.');
    expect((await request.post(`${base}/${first}/accept`, { headers: owner })).status()).toBe(200);
    const text = '  مُراجعة notes يوميًا.\nKeep <b>verbatim text</b> & spacing.  ';
    const second = await suggest(text);
    await page.addInitScript((locale) => localStorage.setItem('caregiver-language', locale), language);
    await page.route('**/api/**', async (route) => {
      const incoming = route.request(); const url = new URL(incoming.url());
      const response = await route.fetch({ url: `${backend}${url.pathname}${url.search}`, maxRedirects: 0 });
      await route.fulfill({ response });
    });
    await page.goto(`/app/notes/plans?recipientId=${recipientId}`); await expect(page).toHaveURL(/\/login$/);
    await page.getByLabel(ar ? 'البريد الإلكتروني' : 'Email address', { exact: true }).fill(email);
    await page.getByLabel(ar ? 'كلمة المرور' : 'Password', { exact: true }).fill(password);
    await page.getByRole('button', { name: ar ? 'تسجيل الدخول' : 'Sign in', exact: true }).click();
    const suggestions = page.getByRole('region', { name: ar ? 'الخطط المقترحة' : 'Suggested plans', exact: true });
    const active = page.getByRole('region', { name: ar ? 'الخطة المقبولة النشطة' : 'Active accepted plan', exact: true });
    const acceptLabel = ar ? 'قبول الاقتراح' : 'Accept suggestion'; const confirmLabel = ar ? 'تأكيد القبول' : 'Confirm acceptance';
    const reloadLabel = ar ? 'إعادة تحميل الخطط المحفوظة' : 'Reload saved plans';
    await expect(suggestions.locator('.plan-content')).toHaveJSProperty('textContent', text);
    await expect(suggestions.getByRole('button', { name: ar ? 'التعديل والقبول — غير متاح' : 'Edit and accept — unavailable', exact: true })).toBeDisabled();
    await suggestions.getByRole('button', { name: acceptLabel, exact: true }).click();
    await page.getByRole('button', { name: confirmLabel, exact: true }).click();
    await expect(active.locator('.plan-content')).toHaveJSProperty('textContent', text);
    await expect(page.locator(`#plan-history-${first} summary .plan-status`)).toHaveText(ar ? 'مؤرشف' : 'Archived');
    expect((await request.post(`${base}/${second}/accept`, { headers: owner })).status()).toBe(409);

    // Another browser/client changes a suggestion after this UI reads it.
    const stale = await suggest('Stale suggestion.');
    await page.getByRole('button', { name: reloadLabel, exact: true }).click(); await expect(suggestions.locator('.plan-content')).toHaveText('Stale suggestion.');
    expect((await request.post(`${base}/${stale}/dismiss`, { headers: owner })).status()).toBe(200);
    await suggestions.getByRole('button', { name: acceptLabel, exact: true }).click(); await page.getByRole('button', { name: confirmLabel, exact: true }).click();
    await expect(page.getByRole('alert')).toContainText(ar ? 'لم يعد هذا الإجراء' : 'can no longer use that action');
    await expect(suggestions.getByRole('button', { name: acceptLabel, exact: true })).toBeDisabled();
    await page.getByRole('button', { name: reloadLabel, exact: true }).click(); await expect(page.locator(`#plan-history-${stale} summary .plan-status`)).toHaveText(ar ? 'مرفوض' : 'Dismissed');

    // Seed a pre-existing edited version through the real API solely to verify
    // history/provenance rendering. The web UI never invokes this unsafe editor.
    const originalText = 'Original suggested content.\nنص محفوظ.';
    const original = await suggest(originalText);
    const edited = await request.post(`${base}/${original}/edit-and-accept`, { headers: owner, data: { content: 'Synthetic edited saved routine.' } });
    expect(edited.status()).toBe(200);
    await page.getByRole('button', { name: reloadLabel, exact: true }).click(); await expect(active.locator('.plan-content')).toHaveText('Synthetic edited saved routine.');
    await active.getByRole('link', { name: original, exact: true }).click();
    await expect(page.locator(`#plan-history-${original}`)).toHaveAttribute('open', '');
    await expect(page.locator(`#plan-history-${original} .plan-content`)).toHaveJSProperty('textContent', originalText);
    await expect(page.getByRole('textbox')).toHaveCount(0);

    await suggest('Suggestion to dismiss in the UI.'); await page.getByRole('button', { name: reloadLabel, exact: true }).click();
    await suggestions.getByRole('button', { name: ar ? 'رفض الاقتراح' : 'Dismiss suggestion', exact: true }).click();
    await page.getByRole('button', { name: ar ? 'تأكيد الرفض' : 'Confirm dismissal', exact: true }).click();
    await expect(page.getByRole('status').filter({ hasText: ar ? 'رفض الخادم الاقتراح.' : 'The server dismissed the suggestion.' })).toBeVisible();
    const last = await suggest('Inactive recipient review only.');
    expect((await request.patch(`${backend}/api/recipients/${recipientId}/deactivate`, { headers: owner })).status()).toBe(200);
    await page.getByRole('button', { name: reloadLabel, exact: true }).click(); await expect(suggestions.getByRole('button', { name: acceptLabel, exact: true })).toBeDisabled();
    await expect(page.locator('.plan-workspace')).toContainText(ar ? 'هذا المستفيد غير نشط' : 'This recipient is inactive');
    expect((await request.get(`${base}/history`, { headers: other })).status()).toBe(404);
    expect((await request.get(`${base}/active`, { headers: other })).status()).toBe(404);
    for (const action of ['accept', 'dismiss', 'edit-and-accept']) {
      expect((await request.post(`${base}/${last}/${action}`, { headers: other, ...(action === 'edit-and-accept' ? { data: { content: 'Untrusted write' } } : {}) })).status()).toBe(404);
    }
    const history = await request.get(`${base}/history`, { headers: owner }); expect(history.status()).toBe(200);
    const versions = await history.json();
    expect(versions.filter((plan: { status: string }) => ['ACCEPTED', 'EDITED_AND_ACCEPTED'].includes(plan.status))).toHaveLength(1);
    expect(versions.find((plan: { id: string }) => plan.id === original)).toMatchObject({ content: originalText, status: 'ARCHIVED' });
    expect(versions.find((plan: { id: string }) => plan.id === second)).toMatchObject({ content: text, status: 'ARCHIVED' });
  });
}
