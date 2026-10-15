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
    const axe = (window as Window & { axe: import('axe-core').AxeStatic }).axe;
    const result = await axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] } });
    return result.violations.map(({ id, nodes }) => ({ id, targets: nodes.map(({ target }) => target) }));
  });
  expect(violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
}

for (const language of ['en', 'ar'] as const) {
  const ar = language === 'ar';
  const labels = {
    email: ar ? 'البريد الإلكتروني' : 'Email address',
    password: ar ? 'كلمة المرور' : 'Password',
    signIn: ar ? 'تسجيل الدخول' : 'Sign in',
    signOut: ar ? 'تسجيل الخروج' : 'Sign out',
    workspace: ar ? 'مساحة عمل مقدّم الرعاية' : 'Caregiver workspace',
  };

  test.describe('authentication ' + language, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript((locale) => { localStorage.setItem('caregiver-language', locale); }, language);
    });

    test('keyboard form, validation and server errors are accessible', async ({ page }) => {
      await page.route('**/api/auth/login', (route) => route.fulfill({ status: 401, json: { code: 'INVALID_CREDENTIALS', message: 'internal diagnostics must never be displayed' } }));
      await page.goto('/login');
      await expect(page.getByRole('form')).toBeVisible();
      await checkAccessibility(page);
      await page.getByRole('button', { name: labels.signIn, exact: true }).click();
      await expect(page.getByLabel(labels.email, { exact: true })).toBeFocused();
      await expect(page.getByRole('alert')).toHaveCount(2);
      await checkAccessibility(page);
      await page.getByLabel(labels.email, { exact: true }).fill('synthetic@example.com');
      await page.keyboard.press('Tab');
      await expect(page.getByLabel(labels.password, { exact: true })).toBeFocused();
      await page.keyboard.type('synthetic password');
      await page.keyboard.press('Enter');
      await expect(page.locator('.auth-error')).toBeFocused();
      await expect(page.locator('.auth-error')).toContainText(ar ? 'البريد الإلكتروني أو كلمة المرور غير صحيحة.' : 'The email or password is incorrect.');
      await expect(page.getByLabel(labels.password, { exact: true })).toHaveValue('');
      await expect(page.getByText('internal diagnostics must never be displayed')).toHaveCount(0);
      await expect(page.locator('html')).toHaveAttribute('dir', ar ? 'rtl' : 'ltr');
      await checkAccessibility(page);
    });

    test('login persists in the tab, logout clears it and browser history stays protected', async ({ page }) => {
      const jwt = token();
      let requests = 0;
      await page.route('**/api/auth/login', async (route) => {
        requests += 1;
        expect(route.request().method()).toBe('POST');
        expect(route.request().postDataJSON()).toEqual({ email: 'synthetic@example.com', password: 'synthetic password' });
        expect(route.request().headers()['authorization']).toBeUndefined();
        await route.fulfill({ json: { token: jwt } });
      });
      await page.goto('/app?view=recent#notes');
      await expect(page).toHaveURL(/\/login$/);
      await page.getByLabel(labels.email, { exact: true }).fill('SYNTHETIC@EXAMPLE.COM');
      await page.getByLabel(labels.password, { exact: true }).fill('synthetic password');
      await page.getByRole('button', { name: labels.signIn, exact: true }).click();
      await expect(page).toHaveURL(/\/app\?view=recent#notes$/);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(labels.workspace);
      await checkAccessibility(page);
      expect(await page.evaluate(() => localStorage.getItem('caregiver-auth-session-v1'))).toBeNull();
      await page.reload();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(labels.workspace);
      expect(requests).toBe(1);
      await page.getByRole('navigation').getByRole('link', { name: ar ? 'الرئيسية' : 'Home', exact: true }).click();
      await page.getByRole('navigation').getByRole('link', { name: ar ? 'لوحة التحكم' : 'Dashboard', exact: true }).click();
      await page.getByRole('button', { name: labels.signOut, exact: true }).click();
      await expect(page).toHaveURL(/\/login$/);
      expect(await page.evaluate(() => sessionStorage.getItem('caregiver-auth-session-v1'))).toBeNull();
      await page.goBack();
      await page.goto('/app');
      await expect(page).toHaveURL(/\/login$/);
      await expect(page.getByRole('button', { name: labels.signOut, exact: true })).toHaveCount(0);
    });

    test('expired stored credentials are removed with an accessible sign-in prompt', async ({ page }) => {
      const expired = [encode({ alg: 'HS256' }), encode({ sub: '12345678-1234-1234-1234-123456789abc', exp: 1 }), 'test-signature'].join('.');
      await page.addInitScript((jwt) => { sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: jwt, baseUrl: '' })); }, expired);
      await page.goto('/app');
      await expect(page).toHaveURL(/\/login$/);
      await expect(page.getByRole('alert')).toContainText(ar ? 'انتهت صلاحية جلستك' : 'Your session has expired');
      expect(await page.evaluate(() => sessionStorage.getItem('caregiver-auth-session-v1'))).toBeNull();
      await checkAccessibility(page);
    });
  });
}
