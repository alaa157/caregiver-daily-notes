import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';

const backend = new URL(process.env.CAREGIVER_TEST_API_URL || 'http://127.0.0.1:8080').origin;
for (const language of ['en', 'ar'] as const) {
  test(`real history filtering, pagination, corrections and caregiver isolation (${language})`, async ({ page, request }) => {
    const ar = language === 'ar';
    const email = `history-${randomUUID()}@example.com`;
    const password = `Synthetic-${randomUUID()}`;
    const registration = await request.post(`${backend}/api/auth/register`, { data: { email, password } });
    expect(registration.status()).toBe(201);
    const owner = { Authorization: `Bearer ${(await registration.json()).token}` };
    const otherRegistration = await request.post(`${backend}/api/auth/register`, { data: { email: `history-other-${randomUUID()}@example.com`, password } });
    expect(otherRegistration.status()).toBe(201);
    const other = { Authorization: `Bearer ${(await otherRegistration.json()).token}` };
    async function recipient(name: string, headers = owner) {
      const response = await request.post(`${backend}/api/recipients`, { headers, data: { name, relationship: null } });
      expect(response.status()).toBe(201);
      return (await response.json()).id as string;
    }
    const first = await recipient('Synthetic مستفيد A');
    const second = await recipient('Synthetic مستفيد B');
    const foreign = await recipient('Other caregiver private name', other);
    const text = '  ملاحظة أصلية\nEnglish + عربي <b>plain text</b>  ';
    let originalId = '';
    for (let day = 1; day <= 22; day++) {
      const response = await request.post(`${backend}/api/notes`, { headers: owner, data: {
        recipientId: first, date: `2026-10-${String(day).padStart(2, '0')}`,
        freeText: day === 18 ? text : `Synthetic observation ${day}`, medicationStatus: 'UNKNOWN', medicationVerified: false, painScore: 0, fallReported: false,
      } });
      expect(response.status()).toBe(201);
      if (day === 18) originalId = (await response.json()).id;
    }
    const secondNote = await request.post(`${backend}/api/notes`, { headers: owner, data: { recipientId: second, date: '2026-10-18', freeText: 'Second recipient observation' } });
    expect(secondNote.status()).toBe(201);
    const privateNote = await request.post(`${backend}/api/notes`, { headers: other, data: { recipientId: foreign, date: '2026-10-18', freeText: 'Other caregiver private text' } });
    expect(privateNote.status()).toBe(201);
    const addition = await request.post(`${backend}/api/notes/${originalId}/addenda`, { headers: owner, data: { medicationStatus: 'MISSED', freeText: 'تصحيح مستقل — original preserved.' } });
    expect(addition.status()).toBe(201);
    const inactive = await request.patch(`${backend}/api/recipients/${second}/deactivate`, { headers: owner });
    expect(inactive.status()).toBe(200);

    await page.addInitScript((locale) => localStorage.setItem('caregiver-language', locale), language);
    await page.route('**/api/**', async (route) => {
      const incoming = route.request(); const url = new URL(incoming.url());
      const response = await route.fetch({ url: `${backend}${url.pathname}${url.search}`, maxRedirects: 0 });
      await route.fulfill({ response });
    });
    await page.goto('/app/notes?from=2026-10-01&to=2026-10-22');
    await expect(page).toHaveURL(/\/login$/);
    await page.getByLabel(ar ? 'البريد الإلكتروني' : 'Email address', { exact: true }).fill(email);
    await page.getByLabel(ar ? 'كلمة المرور' : 'Password', { exact: true }).fill(password);
    await page.getByRole('button', { name: ar ? 'تسجيل الدخول' : 'Sign in', exact: true }).click();
    await expect(page.locator('.history-note')).toHaveCount(20);
    await expect(page.getByText('Other caregiver private name', { exact: true })).toHaveCount(0);
    await page.getByRole('button', { name: ar ? 'الصفحة التالية' : 'Next page', exact: true }).click();
    await expect(page.locator('.history-note')).toHaveCount(3);
    await expect(page.getByRole('button', { name: ar ? 'الصفحة التالية' : 'Next page', exact: true })).toBeDisabled();

    await page.getByLabel(ar ? 'المستفيد من الرعاية' : 'Care recipient', { exact: true }).selectOption(first);
    await page.getByRole('radio', { name: ar ? 'يوم واحد' : 'Single date', exact: true }).check();
    await page.getByLabel(ar ? 'التاريخ' : 'Date', { exact: true }).fill('2026-10-18');
    await page.getByRole('button', { name: ar ? 'تطبيق التصفية' : 'Apply filters', exact: true }).click();
    await expect(page.locator('.history-note')).toHaveCount(1);
    expect(new URL(page.url()).searchParams.has('page')).toBe(false);
    const card = page.locator('.history-note');
    await card.getByText(ar ? 'عرض المشاهدات والنص الأصلي' : 'View original observations and text', { exact: true }).click();
    await expect(card.locator('details .note-verbatim').last()).toHaveJSProperty('textContent', text);
    await card.getByRole('button', { name: ar ? 'عرض التصحيحات (١)' : 'Show corrections (1)', exact: true }).click();
    await expect(card.getByRole('article', { name: ar ? 'تصحيح ١' : 'Correction 1', exact: true })).toBeVisible();
    await expect(card.getByText(ar ? 'لم يتم تناوله' : 'Missed', { exact: true })).toBeVisible();
    await expect(card.locator('details .note-verbatim').last()).toHaveJSProperty('textContent', text);

    await page.getByLabel(ar ? 'المستفيد من الرعاية' : 'Care recipient', { exact: true }).selectOption(second);
    await page.getByRole('button', { name: ar ? 'تطبيق التصفية' : 'Apply filters', exact: true }).click();
    await expect(page.getByRole('article', { name: 'Synthetic مستفيد B', exact: true })).toBeVisible();
    await page.getByLabel(ar ? 'التاريخ' : 'Date', { exact: true }).fill('2026-10-19');
    await page.getByRole('button', { name: ar ? 'تطبيق التصفية' : 'Apply filters', exact: true }).click();
    await expect(page.getByText(ar ? 'لا توجد ملاحظات تطابق التصفية.' : 'No notes match these filters.', { exact: true })).toBeVisible();

    const unchanged = await request.get(`${backend}/api/notes/${originalId}`, { headers: owner });
    expect(await unchanged.json()).toMatchObject({ freeText: text, medicationStatus: 'UNKNOWN', addendumCount: 1 });
    expect((await request.get(`${backend}/api/notes/${originalId}/addenda`, { headers: other })).status()).toBe(404);
    const isolated = await request.get(`${backend}/api/notes?from=2026-10-01&to=2026-10-22&recipientId=${foreign}`, { headers: owner });
    expect(await isolated.json()).toMatchObject({ content: [], totalElements: 0 });
  });
}
