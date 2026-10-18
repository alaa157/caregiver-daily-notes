import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';

const backend = new URL(process.env.CAREGIVER_TEST_API_URL || 'http://127.0.0.1:8080').origin;

for (const language of ['en', 'ar'] as const) {
  test(`real backend authentication and recipient lifecycle (${language})`, async ({ page, request }) => {
    const ar = language === 'ar';
    const email = `web-smoke-${randomUUID()}@example.com`;
    const password = `Synthetic-${randomUUID()}`;
    const register = await request.post(`${backend}/api/auth/register`, { data: { email, password } });
    expect(register.status()).toBe(201);
    const { token: ownerToken } = await register.json();
    expect(typeof ownerToken).toBe('string');
    const ownerHeaders = { Authorization: `Bearer ${ownerToken}` };
    const other = await request.post(`${backend}/api/auth/register`, {
      data: { email: `web-other-${randomUUID()}@example.com`, password },
    });
    expect(other.status()).toBe(201);
    const { token: otherToken } = await other.json();
    const otherHeaders = { Authorization: `Bearer ${otherToken}` };

    await page.addInitScript((locale) => localStorage.setItem('caregiver-language', locale), language);
    // Transport bridge only: every API response comes from the running backend.
    // This models the documented same-origin /api proxy; it does not prove that
    // a deployed proxy or backend CORS has been configured by the owner.
    await page.route('**/api/**', async (route) => {
      const url = new URL(route.request().url());
      const response = await route.fetch({ url: `${backend}${url.pathname}${url.search}`, maxRedirects: 0 });
      await route.fulfill({ response });
    });

    await page.goto('/app/recipients');
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.locator('html')).toHaveAttribute('dir', ar ? 'rtl' : 'ltr');
    await page.getByLabel(ar ? 'البريد الإلكتروني' : 'Email address', { exact: true }).fill(email);
    await page.getByLabel(ar ? 'كلمة المرور' : 'Password', { exact: true }).fill('incorrect-password');
    await page.getByRole('button', { name: ar ? 'تسجيل الدخول' : 'Sign in', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText(ar ? 'البريد الإلكتروني أو كلمة المرور' : 'email or password');
    await page.getByLabel(ar ? 'كلمة المرور' : 'Password', { exact: true }).fill(password);
    await page.getByRole('button', { name: ar ? 'تسجيل الدخول' : 'Sign in', exact: true }).click();
    await expect(page).toHaveURL(/\/app\/recipients$/);
    await expect(page.getByText(ar ? 'لا يوجد مستفيدون من الرعاية بعد' : 'No care recipients yet', { exact: true })).toBeVisible();
    await page.reload();
    await expect(page).toHaveURL(/\/app\/recipients$/);
    await page.getByRole('link', { name: ar ? 'إضافة مستفيد من الرعاية' : 'Add care recipient' }).click();
    const name = ar ? 'مستفيد تجريبي' : 'Synthetic recipient';
    const updatedName = `${name} 2`;
    await page.getByLabel(ar ? 'الاسم' : 'Name', { exact: true }).fill(name);
    await page.getByLabel(ar ? 'صلة القرابة أو العلاقة (اختياري)' : 'Relationship (optional)', { exact: true }).fill(ar ? 'الأب' : 'Father');
    await page.getByRole('button', { name: ar ? 'إضافة مستفيد من الرعاية' : 'Add care recipient', exact: true }).click();
    await expect(page).toHaveURL(/\/app\/recipients\/[0-9a-f-]{36}$/);
    const id = page.url().split('/').at(-1)!;
    await expect(page.getByRole('heading', { name, exact: true })).toBeVisible();

    // These requests exercise real server authorization, not frontend guards.
    for (const method of ['GET', 'PUT', 'PATCH']) {
      const response = await request.fetch(`${backend}/api/recipients/${id}${method === 'PATCH' ? '/deactivate' : ''}`, {
        method, headers: otherHeaders, ...(method === 'PUT' ? { data: { name: 'Unauthorized change' } } : {}),
      });
      expect(response.status()).toBe(404);
    }
    const otherList = await request.get(`${backend}/api/recipients`, { headers: otherHeaders });
    expect(otherList.status()).toBe(200);
    expect(await otherList.json()).toEqual([]);

    await page.getByRole('link', { name: ar ? 'تعديل بيانات المستفيد' : 'Edit recipient', exact: true }).click();
    await page.getByLabel(ar ? 'الاسم' : 'Name', { exact: true }).fill(updatedName);
    await page.getByRole('button', { name: ar ? 'حفظ التغييرات' : 'Save changes', exact: true }).click();
    await expect(page.getByRole('heading', { name: updatedName, exact: true })).toBeVisible();

    // Seed history through its existing API to verify deactivation preserves it.
    const note = await request.post(`${backend}/api/notes`, {
      headers: ownerHeaders,
      data: { recipientId: id, date: new Date().toISOString().slice(0, 10), freeText: 'Synthetic history preservation check.' },
    });
    expect(note.status()).toBe(201);
    const noteBefore = await note.json();
    await page.getByRole('button', { name: ar ? 'إلغاء تنشيط المستفيد' : 'Deactivate recipient', exact: true }).click();
    await page.getByRole('button', { name: ar ? 'إلغاء' : 'Cancel', exact: true }).click();
    await expect(page.getByRole('link', { name: ar ? 'تعديل بيانات المستفيد' : 'Edit recipient', exact: true })).toBeVisible();
    await page.getByRole('button', { name: ar ? 'إلغاء تنشيط المستفيد' : 'Deactivate recipient', exact: true }).click();
    await page.getByRole('button', { name: ar ? 'تأكيد إلغاء التنشيط' : 'Confirm deactivation', exact: true }).click();
    await expect(page.getByText(ar ? 'غير نشط' : 'Inactive', { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: ar ? 'تعديل بيانات المستفيد' : 'Edit recipient', exact: true })).toHaveCount(0);
    const persisted = await request.get(`${backend}/api/recipients/${id}`, { headers: ownerHeaders });
    expect(persisted.status()).toBe(200);
    expect(await persisted.json()).toMatchObject({ id, name: updatedName, active: false });
    const history = await request.get(`${backend}/api/notes/${noteBefore.id}`, { headers: ownerHeaders });
    expect(history.status()).toBe(200);
    expect(await history.json()).toEqual(noteBefore);
    const inactiveUpdate = await request.put(`${backend}/api/recipients/${id}`, { headers: ownerHeaders, data: { name: 'Blocked update' } });
    expect(inactiveUpdate.status()).toBe(409);

    await page.getByRole('button', { name: ar ? 'تسجيل الخروج' : 'Sign out', exact: true }).click();
    await expect(page).toHaveURL(/\/login$/);
    expect(await page.evaluate(() => sessionStorage.getItem('caregiver-auth-session-v1'))).toBeNull();
    await page.goto(`/app/recipients/${id}`);
    await expect(page).toHaveURL(/\/login$/);
    expect((await request.get(`${backend}/api/recipients`)).status()).toBe(401);
  });
}
