import { createRequire } from 'node:module';
import { expect, test, type Page } from '@playwright/test';
import { recipientFixture } from '../recipientFixtures';

const require = createRequire(import.meta.url);
const root = '/app/recipients';
const detail = `${root}/${recipientFixture.id}`;
const token = () => {
  const encode = (value: unknown) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return [encode({ alg: 'HS256' }), encode({ sub: '12345678-1234-1234-1234-123456789abc', exp: Math.floor(Date.now() / 1000) + 3600 }), 'synthetic-signature'].join('.');
};
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
    activeFilter: ar ? 'نشط' : 'Active',
    archivedFilter: ar ? 'مؤرشف' : 'Archived',
    archived: ar ? 'مؤرشف' : 'Archived',
    active: ar ? 'نشط' : 'Active',
    archive: ar ? 'أرشفة المستفيد' : 'Archive recipient',
    archiveTitle: ar ? 'هل تريد أرشفة هذا المستفيد؟' : 'Archive this recipient?',
    restore: ar ? 'استعادة المستفيد' : 'Restore recipient',
    restoreTitle: ar ? 'هل تريد استعادة هذا المستفيد؟' : 'Restore this recipient?',
    cancel: ar ? 'إلغاء' : 'Cancel',
    back: ar ? 'العودة إلى المستفيدين' : 'Back to care recipients',
    edit: ar ? 'تعديل بيانات المستفيد' : 'Edit recipient',
    review: ar ? 'يتطلب المراجعة' : 'Needs review',
    readOnly: ar ? 'هذا المستفيد مؤرشف وبياناته للقراءة فقط' : 'This recipient is archived and read-only',
  };

  test(`reversible recipient archive lifecycle — ${language}`, async ({ page }) => {
    let record = { ...recipientFixture };
    const archivedAt = '2026-10-15T10:00:00Z';
    const actor = 'b1111111-1111-4111-8111-111111111111';
    await page.addInitScript(({ locale, jwt }) => {
      localStorage.setItem('caregiver-language', locale);
      sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: jwt, baseUrl: '' }));
    }, { locale: language, jwt: token() });
    await page.route('**/api/recipients**', async (route) => {
      const request = route.request();
      const url = new URL(request.url());
      const method = request.method();
      if (url.pathname === '/api/recipients') {
        const status = url.searchParams.get('status');
        const include = status === 'ACTIVE' ? record.lifecycleStatus === 'ACTIVE'
          : status === 'ARCHIVED' ? record.lifecycleStatus === 'ARCHIVED'
            : status === 'LEGACY_INACTIVE_REVIEW' ? record.lifecycleStatus === 'LEGACY_INACTIVE_REVIEW' : true;
        return route.fulfill({ json: include ? [record] : [] });
      }
      if (url.pathname === `/api/recipients/${recipientFixture.id}` && method === 'GET') return route.fulfill({ json: record });
      if (url.pathname.endsWith('/archive') && method === 'PATCH') {
        record = { ...record, active: false, lifecycleStatus: 'ARCHIVED', archivedAt, archivedBy: actor };
        return route.fulfill({ json: record });
      }
      if (url.pathname.endsWith('/restore') && method === 'PATCH') {
        record = { ...record, active: true, lifecycleStatus: 'ACTIVE', restoredAt: '2026-10-16T10:00:00Z', restoredBy: actor };
        return route.fulfill({ json: record });
      }
      return route.fulfill({ status: 404, json: { code: 'NOT_FOUND' } });
    });

    await page.goto(root);
    await expect(page.getByRole('link', { name: recipientFixture.name })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('dir', ar ? 'rtl' : 'ltr');
    await accessibility(page);
    await page.getByRole('link', { name: recipientFixture.name }).click();
    await expect(page.getByRole('button', { name: labels.archive, exact: true })).toBeVisible();
    await page.getByRole('button', { name: labels.archive, exact: true }).click();
    const archiveDialog = page.getByRole('dialog', { name: labels.archiveTitle });
    await expect(archiveDialog.getByRole('button', { name: labels.cancel, exact: true })).toBeFocused();
    await accessibility(page);
    await archiveDialog.getByRole('button', { name: labels.archive, exact: true }).click();
    await expect(page.getByText(labels.archived, { exact: true })).toBeVisible();
    await expect(page.getByText(labels.readOnly, { exact: false })).toBeVisible();
    await expect(page.getByRole('link', { name: labels.edit, exact: true })).toHaveCount(0);
    await page.getByRole('link', { name: labels.back, exact: true }).click();
    await expect(page.getByText(ar ? 'لا يوجد مستفيدون مؤرشفون' : 'No active care recipients')).toBeVisible();
    await page.getByRole('button', { name: labels.archivedFilter, exact: true }).click();
    await expect(page.getByRole('link', { name: recipientFixture.name })).toBeVisible();
    await page.getByRole('link', { name: recipientFixture.name }).click();
    await page.getByRole('button', { name: labels.restore, exact: true }).click();
    const restoreDialog = page.getByRole('dialog', { name: labels.restoreTitle });
    await expect(restoreDialog).toBeVisible();
    await restoreDialog.getByRole('button', { name: labels.restore, exact: true }).click();
    await expect(page.getByText(labels.active, { exact: true })).toBeVisible();
    await page.getByRole('link', { name: labels.back, exact: true }).click();
    await page.getByRole('button', { name: labels.activeFilter, exact: true }).click();
    await expect(page.getByRole('link', { name: recipientFixture.name })).toBeVisible();
    await accessibility(page);
  });
}
