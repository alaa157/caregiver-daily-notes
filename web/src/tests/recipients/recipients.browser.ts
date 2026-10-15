import { createRequire } from 'node:module';
import { expect, test, type Page } from '@playwright/test';
import { recipientFixture } from '../recipientFixtures';

const require = createRequire(import.meta.url);
const encode = (value: unknown) => Buffer.from(JSON.stringify(value)).toString('base64url');
const token = () => [encode({ alg: 'HS256' }), encode({ sub: '12345678-1234-1234-1234-123456789abc', exp: Math.floor(Date.now() / 1000) + 3600 }), 'synthetic-signature'].join('.');
const root = '/app/recipients';
const detail = `${root}/${recipientFixture.id}`;
const syntheticId = 'c3333333-3333-4333-8333-333333333333';

async function accessibility(page: Page) {
  await page.evaluate(async () => { await document.fonts.ready; });
  await page.addScriptTag({ path: require.resolve('axe-core/axe.min.js') });
  const violations = await page.evaluate(async () => {
    const result = await (window as unknown as Window & { axe: typeof import('axe-core') }).axe.run(document,
      { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } });
    return result.violations.map(({ id, nodes }) => ({ id, targets: nodes.map(({ target }) => target) }));
  });
  expect(violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
}

for (const language of ['en', 'ar'] as const) {
  const ar = language === 'ar';
  const labels = {
    list: ar ? 'المستفيدون من الرعاية' : 'Care recipients',
    add: ar ? 'إضافة مستفيد من الرعاية' : 'Add care recipient',
    name: ar ? 'الاسم' : 'Name',
    relationship: ar ? 'صلة القرابة أو العلاقة (اختياري)' : 'Relationship (optional)',
    edit: ar ? 'تعديل بيانات المستفيد' : 'Edit recipient',
    save: ar ? 'حفظ التغييرات' : 'Save changes',
    deactivate: ar ? 'إلغاء تنشيط المستفيد' : 'Deactivate recipient',
    confirm: ar ? 'تأكيد إلغاء التنشيط' : 'Confirm deactivation',
    cancel: ar ? 'إلغاء' : 'Cancel',
    inactive: ar ? 'غير نشط' : 'Inactive',
    back: ar ? 'العودة إلى المستفيدين' : 'Back to care recipients',
    retry: ar ? 'إعادة المحاولة' : 'Retry',
    reload: ar ? 'إعادة تحميل بيانات المستفيد' : 'Reload recipient details',
  };

  test.describe('care recipients ' + language, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript(({ locale, jwt }) => {
        localStorage.setItem('caregiver-language', locale);
        sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: jwt, baseUrl: '' }));
      }, { locale: language, jwt: token() });
    });

    test('list, create, edit and confirm deactivation work with keyboard and accessible layouts', async ({ page }, info) => {
      const records = [{ ...recipientFixture }];
      const mutations: string[] = [];
      await page.route('**/api/recipients**', async (route) => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        expect(request.headers()['authorization']).toMatch(/^Bearer /);
        const method = request.method();
        if (method === 'POST' || method === 'PUT') {
          const input = request.postDataJSON();
          expect(Object.keys(input).sort()).toEqual(['name', 'relationship']);
          mutations.push(method);
          if (method === 'POST') {
            const record = { ...recipientFixture, ...input, id: syntheticId };
            records.push(record);
            return route.fulfill({ status: 201, json: record });
          }
          Object.assign(records.find(({ id }) => path.endsWith(id))!, input);
        }
        if (method === 'PATCH') {
          expect(request.postData()).toBeNull();
          mutations.push(method);
          records.find(({ id }) => path.endsWith(`${id}/deactivate`))!.active = false;
        }
        const record = records.find(({ id }) => path.includes(id));
        return route.fulfill({ json: path === '/api/recipients' ? records : record });
      });
      await page.goto(root);
      await expect(page.getByRole('link', { name: recipientFixture.name })).toBeVisible();
      await expect(page.locator('html')).toHaveAttribute('dir', ar ? 'rtl' : 'ltr');
      await accessibility(page);
      await page.getByRole('link', { name: labels.add }).click();
      await page.getByRole('button', { name: labels.add }).click();
      await expect(page.getByLabel(labels.name, { exact: true })).toBeFocused();
      await accessibility(page);
      await page.getByLabel(labels.name, { exact: true }).fill(ar ? 'منى حسن' : 'Mona Hassan');
      await page.keyboard.press('Tab');
      await expect(page.getByLabel(labels.relationship, { exact: true })).toBeFocused();
      await page.keyboard.type('Mother');
      await page.keyboard.press('Enter');
      await expect(page).toHaveURL(new RegExp(`${syntheticId}$`));
      await expect(page.locator('.recipient-success')).toBeFocused();
      await page.getByRole('link', { name: labels.edit }).click();
      await page.getByLabel(labels.name, { exact: true }).fill(ar ? 'منى حسن محمد' : 'Mona Hassan Updated');
      await page.getByLabel(labels.relationship, { exact: true }).fill('');
      await page.getByRole('button', { name: labels.save }).click();
      await expect(page.locator('.recipient-success')).toBeVisible();
      await expect(page.locator('.recipient-details')).toContainText(ar ? 'غير محددة' : 'Not specified');
      await accessibility(page);
      await page.getByRole('button', { name: labels.deactivate, exact: true }).click();
      const dialog = page.getByRole('dialog');
      await expect(dialog.getByRole('button', { name: labels.cancel, exact: true })).toBeFocused();
      await page.keyboard.press('Shift+Tab');
      await expect(dialog.getByRole('button', { name: labels.confirm })).toBeFocused();
      await page.keyboard.press('Tab');
      await expect(dialog.getByRole('button', { name: labels.cancel, exact: true })).toBeFocused();
      await accessibility(page);
      await page.keyboard.press('Escape');
      await expect(dialog).toHaveCount(0);
      await expect(page.getByRole('button', { name: labels.deactivate, exact: true })).toBeFocused();
      expect(mutations).toEqual(['POST', 'PUT']);
      await page.getByRole('button', { name: labels.deactivate, exact: true }).click();
      await dialog.getByRole('button', { name: labels.cancel, exact: true }).click();
      await expect(dialog).toHaveCount(0);
      await page.getByRole('button', { name: labels.deactivate, exact: true }).click();
      await dialog.getByRole('button', { name: labels.confirm }).click();
      await expect(page.getByText(labels.inactive, { exact: true })).toBeVisible();
      await expect(page.getByRole('link', { name: labels.edit })).toHaveCount(0);
      await expect(page.locator('.recipient-success')).toBeFocused();
      await accessibility(page);
      await page.evaluate(() => window.scrollTo(0, 0));
      await page.screenshot({ path: info.outputPath('recipient-details.png'), fullPage: true });
      await page.getByRole('link', { name: labels.back }).click();
      await expect(page.getByRole('list', { name: labels.list }).getByRole('listitem')).toHaveCount(2);
      await expect(page.getByText(labels.inactive, { exact: true })).toBeVisible();
      expect(mutations).toEqual(['POST', 'PUT', 'PATCH']);
    });

    test('loading, network errors, retry and empty states are accessible', async ({ page }) => {
      let release!: () => void;
      const gate = new Promise<void>((resolve) => { release = resolve; });
      let requests = 0;
      await page.route('**/api/recipients', async (route) => {
        requests += 1;
        if (requests === 1) { await gate; await route.fulfill({ status: 500, json: { message: 'private diagnostics' } }); }
        else await route.fulfill({ json: [] });
      });
      await page.goto(root);
      await expect(page.getByRole('status')).toContainText(ar ? 'جارٍ تحميل' : 'Loading');
      release();
      await expect(page.getByRole('alert')).toBeVisible();
      await expect(page.getByText('private diagnostics')).toHaveCount(0);
      await accessibility(page);
      await page.getByRole('button', { name: labels.retry }).click();
      await expect(page.getByRole('status')).toContainText(ar ? 'لا يوجد مستفيدون' : 'No care recipients yet');
      await accessibility(page);
    });

    test('unavailable recipients, inactive conflicts and rejected sessions stay safe', async ({ page }) => {
      let active = true;
      await page.route('**/api/recipients**', async (route) => {
        const request = route.request();
        if (request.url().includes(syntheticId)) return route.fulfill({ status: 404, json: { message: 'private ownership details' } });
        if (request.method() === 'PUT') { active = false; return route.fulfill({ status: 409, json: { code: 'INACTIVE_RECIPIENT' } }); }
        return route.fulfill({ json: { ...recipientFixture, active } });
      });
      await page.goto(`${root}/${syntheticId}`);
      await expect(page.getByRole('alert')).toContainText(ar ? 'هذا المستفيد غير متاح' : 'This recipient is unavailable');
      await expect(page.getByText('private ownership details')).toHaveCount(0);
      await accessibility(page);
      await page.goto(`${detail}/edit`);
      await page.getByLabel(labels.name, { exact: true }).fill('Unsaved input');
      await page.getByRole('button', { name: labels.save }).click();
      await expect(page.getByRole('alert')).toBeFocused();
      await expect(page.getByLabel(labels.name, { exact: true })).toHaveValue('Unsaved input');
      await accessibility(page);
      await page.getByRole('button', { name: labels.reload }).click();
      await expect(page.getByText(labels.inactive, { exact: true })).toBeVisible();
      await expect(page.getByRole('textbox')).toHaveCount(0);
      await accessibility(page);
      await page.unroute('**/api/recipients**');
      await page.route('**/api/recipients**', (route) => route.fulfill({ status: 401, json: { code: 'UNAUTHORIZED' } }));
      await page.getByRole('link', { name: labels.back }).click();
      await expect(page).toHaveURL(/\/login$/);
      await expect(page.getByText(recipientFixture.name, { exact: true })).toHaveCount(0);
      expect(await page.evaluate(() => sessionStorage.getItem('caregiver-auth-session-v1'))).toBeNull();
    });

    test('deactivation failure keeps an accessible modal open for retry', async ({ page }) => {
      let attempts = 0;
      await page.route('**/api/recipients**', async (route) => {
        if (route.request().method() === 'PATCH') {
          attempts += 1;
          if (attempts === 1) return route.fulfill({ status: 503, json: { message: 'private diagnostics' } });
          return route.fulfill({ json: { ...recipientFixture, active: false } });
        }
        return route.fulfill({ json: { ...recipientFixture, name: 'اسم طويل للاختبار Long synthetic recipient name '.repeat(2) } });
      });
      await page.goto(detail);
      await accessibility(page);
      await page.getByRole('button', { name: labels.deactivate, exact: true }).click();
      await page.getByRole('button', { name: labels.confirm }).click();
      await expect(page.getByRole('dialog').getByRole('alert')).toBeFocused();
      await accessibility(page);
      await page.keyboard.press('Shift+Tab');
      await expect(page.getByRole('button', { name: labels.confirm })).toBeFocused();
      await page.keyboard.press('Tab');
      await expect(page.getByRole('button', { name: labels.cancel, exact: true })).toBeFocused();
      await page.getByRole('button', { name: labels.confirm }).click();
      await expect(page.getByText(labels.inactive, { exact: true })).toBeVisible();
      expect(attempts).toBe(2);
    });
  });
}
