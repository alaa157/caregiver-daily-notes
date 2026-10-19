import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';

const backend = new URL(process.env.CAREGIVER_TEST_API_URL || 'http://127.0.0.1:8080').origin;
for (const language of ['en', 'ar'] as const) {
  test(`real append-only notes and safe response-loss replay (${language})`, async ({ page, request }) => {
    const ar = language === 'ar';
    const email = `notes-${randomUUID()}@example.com`;
    const password = `Synthetic-${randomUUID()}`;
    const register = await request.post(`${backend}/api/auth/register`, { data: { email, password } });
    expect(register.status()).toBe(201);
    const ownerHeaders = { Authorization: `Bearer ${(await register.json()).token}` };
    const other = await request.post(`${backend}/api/auth/register`, { data: { email: `notes-other-${randomUUID()}@example.com`, password } });
    expect(other.status()).toBe(201);
    const otherHeaders = { Authorization: `Bearer ${(await other.json()).token}` };
    const createdRecipient = await request.post(`${backend}/api/recipients`, { headers: ownerHeaders, data: { name: 'Synthetic مستفيد', relationship: null } });
    expect(createdRecipient.status()).toBe(201);
    const recipient = await createdRecipient.json();
    const text = '  غير متأكد من الدواء.\nEnglish + عربي، التشكيل: مُلاحَظة <b>text only</b>  ';
    const correction = '  تصحيح مستقل\nDo not change original.  ';
    const writes = new Map<string, { key: string; body: unknown; id: string }[]>();
    await page.addInitScript((locale) => localStorage.setItem('caregiver-language', locale), language);
    await page.route('**/api/**', async (route) => {
      const incoming = route.request();
      const url = new URL(incoming.url());
      // Only the transport is bridged. Every response is produced by Spring Boot.
      const response = await route.fetch({ url: `${backend}${url.pathname}${url.search}`, maxRedirects: 0 });
      if (incoming.method() === 'POST' && url.pathname.startsWith('/api/notes')) {
        const list = writes.get(url.pathname) || [];
        const body = incoming.postDataJSON();
        expect(body).not.toHaveProperty('caregiverId');
        expect(response.status()).toBe(201);
        list.push({ key: incoming.headers()['idempotency-key'], body, id: (await response.json()).id });
        writes.set(url.pathname, list);
        // Deliberately lose the first response AFTER the server commits.
        if (list.length === 1) return route.abort('failed');
      }
      await route.fulfill({ response });
    });
    await page.goto('/app/notes/new');
    await expect(page).toHaveURL(/\/login$/);
    await page.getByLabel(ar ? 'البريد الإلكتروني' : 'Email address', { exact: true }).fill(email);
    await page.getByLabel(ar ? 'كلمة المرور' : 'Password', { exact: true }).fill(password);
    await page.getByRole('button', { name: ar ? 'تسجيل الدخول' : 'Sign in', exact: true }).click();
    await expect(page).toHaveURL(/\/app\/notes\/new$/);
    await expect(page.locator('html')).toHaveAttribute('dir', ar ? 'rtl' : 'ltr');
    await page.getByLabel(ar ? 'المستفيد من الرعاية' : 'Care recipient', { exact: true }).selectOption(recipient.id);
    await page.getByLabel(ar ? 'تاريخ الملاحظة' : 'Observation date', { exact: true }).fill('2026-10-18');
    await page.getByLabel(ar ? 'تناول الدواء' : 'Medication taken', { exact: true }).selectOption('UNKNOWN');
    await page.getByLabel(ar ? 'تم التحقق من تقرير الدواء' : 'Medication report verified', { exact: true }).selectOption('false');
    await page.getByLabel(ar ? 'درجة الألم (٠–١٠)' : 'Pain score (0–10)', { exact: true }).fill('0');
    await page.getByLabel(ar ? 'الإبلاغ عن سقوط' : 'Fall reported', { exact: true }).selectOption('false');
    await page.getByLabel(ar ? 'الملاحظات النصية' : 'Free-text observations', { exact: true }).fill(text);
    await page.getByRole('button', { name: ar ? 'حفظ الملاحظة الأصلية' : 'Save original note', exact: true }).click();
    await expect(page.getByRole('alert')).toBeFocused();
    await expect(page.getByLabel(ar ? 'الملاحظات النصية' : 'Free-text observations', { exact: true })).toBeDisabled();
    await page.getByRole('button', { name: ar ? 'إعادة محاولة الحفظ نفسه' : 'Retry the same save', exact: true }).click();
    await expect(page).toHaveURL(/\/app\/notes\/[0-9a-f-]{36}$/);
    const id = page.url().split('/').at(-1)!;
    const originalResponse = await request.get(`${backend}/api/notes/${id}`, { headers: ownerHeaders });
    expect(originalResponse.status()).toBe(200);
    const original = await originalResponse.json();
    expect(original).toMatchObject({ recipientId: recipient.id, date: '2026-10-18', freeText: text, medicationStatus: 'UNKNOWN', medicationVerified: false, painScore: 0, fallReported: false, mood: null });
    const creations = writes.get('/api/notes')!;
    expect(creations).toHaveLength(2);
    expect(creations[1]).toEqual(creations[0]);
    expect((await request.get(`${backend}/api/notes/${id}`, { headers: otherHeaders })).status()).toBe(404);
    expect((await request.get(`${backend}/api/notes/${id}/addenda`, { headers: otherHeaders })).status()).toBe(404);
    expect((await request.post(`${backend}/api/notes/${id}/addenda`, { headers: otherHeaders, data: { freeText: 'Unauthorized' } })).status()).toBe(404);

    await page.getByRole('link', { name: ar ? 'إضافة تصحيح' : 'Add a correction', exact: true }).click();
    await page.getByRole('checkbox', { name: ar ? 'تصحيح هذا الحقل: تناول الدواء' : 'Correct this field: Medication taken', exact: true }).check();
    await page.getByLabel(ar ? 'تناول الدواء' : 'Medication taken', { exact: true }).selectOption('MISSED');
    await page.getByRole('checkbox', { name: ar ? 'تصحيح هذا الحقل: الملاحظات النصية' : 'Correct this field: Free-text observations', exact: true }).check();
    await page.getByLabel(ar ? 'الملاحظات النصية' : 'Free-text observations', { exact: true }).fill(correction);
    await page.getByRole('button', { name: ar ? 'حفظ الإضافة' : 'Save addendum', exact: true }).click();
    await expect(page.getByRole('alert')).toBeFocused();
    await page.getByRole('button', { name: ar ? 'إعادة محاولة الحفظ نفسه' : 'Retry the same save', exact: true }).click();
    await expect(page.getByText(ar ? 'تم حفظ الإضافة. لم تتغير الملاحظة الأصلية.' : 'Addendum saved. The original note is unchanged.', { exact: true })).toBeVisible();
    const corrections = writes.get(`/api/notes/${id}/addenda`)!;
    expect(corrections).toHaveLength(2);
    expect(corrections[1]).toEqual(corrections[0]);
    expect(corrections[0].body).toEqual({ medicationStatus: 'MISSED', freeText: correction });
    const after = await request.get(`${backend}/api/notes/${id}`, { headers: ownerHeaders });
    expect(await after.json()).toEqual({ ...original, addendumCount: 1 });
    const savedAddenda = await request.get(`${backend}/api/notes/${id}/addenda`, { headers: ownerHeaders });
    expect(savedAddenda.status()).toBe(200);
    const additions = await savedAddenda.json();
    expect(additions).toHaveLength(1);
    expect(additions[0]).toMatchObject({ noteId: id, freeText: correction, medicationStatus: 'MISSED', medicationVerified: null });
    await expect(page.getByRole('article', { name: ar ? 'الملاحظة الأصلية — للقراءة فقط' : 'Original note — read only', exact: true }).locator('.note-verbatim').last()).toHaveJSProperty('textContent', text);
    const history = await request.get(`${backend}/api/notes?recipientId=${recipient.id}&from=2026-10-18&to=2026-10-18`, { headers: ownerHeaders });
    expect((await history.json()).totalElements).toBe(1);
    const duplicate = await request.post(`${backend}/api/notes`, { headers: ownerHeaders, data: { recipientId: recipient.id, date: '2026-10-18' } });
    expect(duplicate.status()).toBe(409);
  });
}
