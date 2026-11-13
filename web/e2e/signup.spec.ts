import { createRequire } from 'node:module';
import { expect, test, type Page } from '@playwright/test';

const require = createRequire(import.meta.url);
const encode = (value: unknown) => Buffer.from(JSON.stringify(value)).toString('base64url');
// Test-only contract response, not a signed backend token or a production login.
const token = () => [encode({ alg: 'HS256' }), encode({ sub: '12345678-1234-1234-1234-123456789abc', exp: Math.floor(Date.now() / 1000) + 3600 }), 'test-signature'].join('.');

async function checkAccessibility(page: Page) {
  await page.evaluate(async () => {
    await document.fonts.ready;
    await Promise.all(document.getAnimations().map((animation) => animation.finished.catch(() => {})));
  });
  await page.addScriptTag({ path: require.resolve('axe-core/axe.min.js') });
  const violations = await page.evaluate(async () => {
    const axe = (window as unknown as Window & { axe: typeof import('axe-core') }).axe;
    const result = await axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } });
    return result.violations.map(({ id, nodes }) => ({ id, targets: nodes.map(({ target }) => target) }));
  });
  expect(violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
}

for (const language of ['en', 'ar'] as const) {
  const ar = language === 'ar';
  const labels = {
    title: ar ? 'إنشاء حساب مقدّم رعاية' : 'Create a caregiver account',
    email: ar ? 'البريد الإلكتروني' : 'Email address',
    password: ar ? 'كلمة المرور' : 'Password',
    submit: ar ? 'إنشاء الحساب' : 'Create account',
    createAccount: ar ? 'إنشاء حساب' : 'Create an account',
    workspace: ar ? 'مساحة عمل مقدّم الرعاية' : 'Caregiver workspace',
    duplicate: ar ? 'يوجد حساب بهذا البريد الإلكتروني بالفعل' : 'An account with this email already exists',
  };

  test.describe('signup ' + language, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript((locale) => { localStorage.setItem('caregiver-language', locale); }, language);
    });

    test('creates an account through the form and lands in the workspace', async ({ page }) => {
      const jwt = token();
      await page.route('**/api/**', (route) => {
        const url = new URL(route.request().url());
        if (url.pathname === '/api/recipients') return route.fulfill({ json: [] });
        if (url.pathname === '/api/activity') return route.fulfill({ json: [] });
        if (url.pathname === '/api/notes') {
          const pageNumber = Number(url.searchParams.get('page') ?? 0);
          const size = Number(url.searchParams.get('size') ?? 20);
          return route.fulfill({ json: { content: [], number: pageNumber, size, totalElements: 0, totalPages: 0, first: pageNumber === 0, last: true } });
        }
        return route.fulfill({ status: 404, json: { code: 'NOT_FOUND' } });
      });
      await page.route('**/api/auth/register', async (route) => {
        expect(route.request().method()).toBe('POST');
        expect(route.request().postDataJSON()).toEqual({ email: 'synthetic@example.com', password: 'synthetic password' });
        expect(route.request().headers()['authorization']).toBeUndefined();
        await route.fulfill({ status: 201, json: { token: jwt } });
      });
      await page.goto('/signup');
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(labels.title);
      await checkAccessibility(page);
      await page.getByLabel(labels.email, { exact: true }).fill('SYNTHETIC@EXAMPLE.COM');
      await page.getByLabel(labels.password, { exact: true }).fill('synthetic password');
      await page.getByRole('button', { name: labels.submit, exact: true }).click();
      await expect(page).toHaveURL(/\/app$/);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(labels.workspace);
      expect(await page.evaluate(() => localStorage.getItem('caregiver-auth-session-v1'))).toBeNull();
      expect(await page.evaluate(() => sessionStorage.getItem('caregiver-auth-session-v1'))).toContain(jwt);
    });

    test('shows the duplicate-email message without leaking server text', async ({ page }) => {
      await page.route('**/api/auth/register', (route) =>
        route.fulfill({ status: 422, json: { code: 'DUPLICATE_EMAIL', message: 'internal diagnostics must never be displayed' } }));
      await page.goto('/signup');
      await page.getByLabel(labels.email, { exact: true }).fill('taken@example.com');
      await page.getByLabel(labels.password, { exact: true }).fill('synthetic password');
      await page.getByRole('button', { name: labels.submit, exact: true }).click();
      const alert = page.getByRole('alert');
      await expect(alert).toContainText(labels.duplicate);
      await expect(alert).not.toContainText('internal diagnostics must never be displayed');
      await expect(page.getByLabel(labels.password, { exact: true })).toHaveValue('');
      await checkAccessibility(page);
    });

    test('is keyboard operable and reachable from the sign-in page', async ({ page }) => {
      await page.goto('/login');
      await page.getByRole('link', { name: labels.createAccount, exact: true }).click();
      await expect(page).toHaveURL(/\/signup$/);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(labels.title);
      await page.getByRole('button', { name: labels.submit, exact: true }).click();
      await expect(page.getByLabel(labels.email, { exact: true })).toBeFocused();
      await expect(page.getByRole('alert')).toHaveCount(2);
      await checkAccessibility(page);
      await expect(page.locator('html')).toHaveAttribute('dir', ar ? 'rtl' : 'ltr');
    });
  });
}
