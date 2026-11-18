import { expect, test, type Page } from '@playwright/test';
import { expectNoAxeViolations, settlePage } from './accessibility';

async function checkAccessibility(page: Page) {
  await settlePage(page);
  await expectNoAxeViolations(page, page.url());
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
      await expect(page.getByRole('heading', { level: 1 })).toContainText(
        language === 'ar' ? 'تفاصيل صغيرة ترسم صورة أكبر لـ' : 'The little details are the story of',
      );
      await expect(page.getByRole('navigation', { name: language === 'ar' ? 'التنقل الرئيسي' : 'Main navigation' })).toBeVisible();
      await checkAccessibility(page);
      await page.getByRole('button', {
        name: language === 'ar' ? 'التبديل إلى الإنجليزية' : 'Switch to Arabic',
      }).first().click();
      await expect(page.getByRole('heading', { level: 1 })).toContainText(
        language === 'ar' ? 'The little details are the story of' : 'تفاصيل صغيرة ترسم صورة أكبر لـ',
      );
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

      await page.locator('.site-header').getByRole('link', { name: language === 'ar' ? 'تسجيل الدخول' : 'Sign in' }).click();
      await expect(page).toHaveURL(/\/login$/);
      await expect(page.getByRole('main')).toBeFocused();
      await expect(page.getByRole('form')).toBeVisible();
      await expect(page.locator('input')).toHaveCount(2);
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

    test('disables reveal transitions and smooth scrolling for reduced motion', async ({ page }) => {
      await page.emulateMedia({ reducedMotion: 'reduce' });
      await page.goto('/');
      await settlePage(page);
      // The port uses short transitions rather than keyframe animations.
      const durations = await page.locator('.main-nav a, .button').evaluateAll((elements) =>
        elements.map((element) => getComputedStyle(element).transitionDuration));
      expect(durations.length).toBeGreaterThan(0);
      expect(durations.every((value) => parseFloat(value) < 0.001)).toBe(true);
      await expect(page.locator('html')).toHaveCSS('scroll-behavior', 'auto');
    });

    test('keeps loading feedback text visible with reduced motion', async ({ page }) => {
      let releaseConfig!: () => void;
      const configHeld = new Promise<void>((resolve) => { releaseConfig = resolve; });
      let signalConfigRequest!: () => void;
      const configRequested = new Promise<void>((resolve) => { signalConfigRequest = resolve; });
      await page.route('**/config.json', async (route) => {
        signalConfigRequest();
        await configHeld;
        await route.fulfill({ json: { apiBaseUrl: '' } });
      });

      await page.emulateMedia({ reducedMotion: 'reduce' });
      // The public landing page no longer waits on config; the loading state
      // belongs to routes that need the API.
      await page.goto('/login');
      await configRequested;
      try {
        const status = page.getByRole('status');
        const label = status.locator('.feedback-label');
        await expect(status).toBeVisible();
        await expect(status).toHaveAttribute('aria-live', 'polite');
        await expect(label).toBeVisible();
        await expect(label).toHaveText(/\S/);
        await expect(status.locator('.spinner')).toHaveCSS('animation-name', 'none');
      } finally {
        releaseConfig();
      }
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    });

    test('keeps normal motion when reduced motion is not requested', async ({ page }) => {
      await page.emulateMedia({ reducedMotion: 'no-preference' });
      await page.goto('/');
      await settlePage(page);
      const durations = await page.locator('.main-nav a, .button').evaluateAll((elements) =>
        elements.map((element) => getComputedStyle(element).transitionDuration));
      expect(durations.length).toBeGreaterThan(0);
      expect(durations.every((value) => parseFloat(value) >= 0.2)).toBe(true);
    });

    test('keeps route focus behavior with reduced motion', async ({ page }) => {
      await page.emulateMedia({ reducedMotion: 'reduce' });
      await page.addInitScript(() => {
        const nativeScrollTo = window.scrollTo;
        window.scrollTo = new Proxy(nativeScrollTo, {
          apply(target, thisArg, args: unknown[]) {
            const [options] = args;
            if (typeof options === 'object' && options !== null && 'behavior' in options) {
              document.documentElement.dataset.routeScrollBehavior = String(options.behavior);
            }
            return Reflect.apply(target, thisArg, args);
          },
        });
      });
      await page.goto('/');
      await page.locator('.site-header').getByRole('link', {
        name: language === 'ar' ? 'تسجيل الدخول' : 'Sign in',
      }).click();

      await expect(page).toHaveURL(/\/login$/);
      await expect(page.getByRole('main')).toBeFocused();
      await expect(page.locator('html')).toHaveCSS('scroll-behavior', 'auto');
      await expect(page.locator('html')).toHaveAttribute('data-route-scroll-behavior', 'instant');
    });
  });
}
