import { createRequire } from 'node:module';
import { expect, test, type Page } from '@playwright/test';

const require = createRequire(import.meta.url);

async function settlePage(page: Page) {
  await page.getByRole('heading', { level: 1 }).waitFor();
  await page.evaluate(async () => {
    await document.fonts.ready;
    await Promise.all(document.getAnimations().map((animation) => animation.finished.catch(() => {})));
  });
}

async function checkAccessibility(page: Page) {
  await settlePage(page);
  await page.addScriptTag({ path: require.resolve('axe-core/axe.min.js') });
  const violations = await page.evaluate(async () => {
    const axe = (window as Window & { axe: import('axe-core').AxeStatic }).axe;
    const results = await axe.run(document, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
    });
    return results.violations.map(({ id, nodes }) => ({
      id,
      nodes: nodes.map(({ target, failureSummary }) => ({ target, failureSummary })),
    }));
  });
  expect(violations).toEqual([]);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
  expect(overflow, 'The page should fit the viewport without horizontal scrolling').toBe(false);
}

for (const language of ['en', 'ar'] as const) {
  test.describe(language, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript((locale) => {
        if (localStorage.getItem('caregiver-language') === null) {
          localStorage.setItem('caregiver-language', locale);
        }
      }, language);
    });

    test('landing page is accessible and responsive in both directions', async ({ page }) => {
      const errors: string[] = [];
      page.on('pageerror', (error) => errors.push(error.message));
      await page.goto('/');
      await expect(page.locator('html')).toHaveAttribute('dir', language === 'ar' ? 'rtl' : 'ltr');
      await expect(page.locator('html')).toHaveAttribute('lang', language);
      await expect(page.getByRole('navigation').locator('[aria-current="page"]')).toHaveAttribute('href', '/');
      await checkAccessibility(page);
      await page.getByRole('button', { name: language === 'ar' ? 'Switch to English' : 'التغيير إلى العربية' }).click();
      await expect(page.locator('html')).toHaveAttribute('dir', language === 'ar' ? 'ltr' : 'rtl');
      await checkAccessibility(page);
      await page.reload();
      await expect(page.locator('html')).toHaveAttribute('lang', language === 'ar' ? 'en' : 'ar');
      expect(errors).toEqual([]);
    });

    test('keyboard navigation, protected routes, and missing pages remain usable', async ({ page }) => {
      await page.goto('/');
      await settlePage(page);
      await page.keyboard.press('Tab');
      await expect(page.locator('.skip-link')).toBeFocused();
      await page.keyboard.press('Enter');
      await expect(page.getByRole('main')).toBeFocused();

      await page.getByRole('navigation').getByRole('link', { name: language === 'ar' ? 'تسجيل الدخول' : 'Sign in' }).click();
      await expect(page).toHaveURL(/\/login$/);
      await expect(page.getByRole('main')).toBeFocused();
      await expect(page.getByRole('status')).toHaveText(language === 'ar' ? 'تسجيل الدخول غير متاح بعد.' : 'Sign in is not available yet.');
      await expect(page.locator('input')).toHaveCount(0);
      await checkAccessibility(page);

      await page.goto('/app');
      await expect(page).toHaveURL(/\/login$/);
      await expect(page.locator('html')).toHaveAttribute('lang', language);
      await page.goto('/missing-page');
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(language === 'ar' ? 'الصفحة غير موجودة' : 'Page not found');
      await checkAccessibility(page);
      await page.getByRole('link', { name: language === 'ar' ? 'العودة للرئيسية' : 'Return home' }).click();
      await expect(page).toHaveURL(/\/$/);
      await expect(page.getByRole('main')).toBeFocused();
    });

    test('reduced motion disables entrance animations', async ({ page }) => {
      await page.emulateMedia({ reducedMotion: 'reduce' });
      await page.goto('/');
      await settlePage(page);
      const animations = await page.locator('.hero-copy, .care-illustration').evaluateAll((elements) =>
        elements.map((element) => getComputedStyle(element).animationName));
      expect(animations).toEqual(['none', 'none']);
    });
  });
}
