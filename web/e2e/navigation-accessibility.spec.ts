import { expect, test } from '@playwright/test';
import { testToken } from '../src/tests/authFixtures';
import { expectNoAxeViolations, settlePage } from './accessibility';

for (const language of ['en', 'ar'] as const) {
  const ar = language === 'ar';
  const labels = {
    landmark: ar ? 'قائمة التطبيق' : 'Application navigation',
    overview: ar ? 'نظرة عامة' : 'Overview',
    recipients: ar ? 'المستفيدون' : 'Recipients',
    notes: ar ? 'الملاحظات' : 'Notes',
    plans: ar ? 'الخطط' : 'Plans',
    appearance: ar ? 'المظهر' : 'Appearance',
    switch: ar ? 'Switch to English' : 'التغيير إلى العربية',
    signOut: ar ? 'تسجيل الخروج' : 'Sign out',
    openNav: ar ? 'فتح قائمة التنقل' : 'Open navigation',
    closeNav: ar ? 'إغلاق قائمة التنقل' : 'Close navigation',
    account: ar ? 'الحساب' : 'Account',
  };
  const destinations = [
    { name: labels.overview, href: '/app' },
    { name: labels.recipients, href: '/app/recipients' },
    { name: labels.notes, href: '/app/notes' },
    { name: labels.plans, href: '/app/notes/plans' },
  ];

  test(`landing route axe scan covers all widths (${language})`, async ({ page }) => {
    await page.addInitScript((locale) => localStorage.setItem('caregiver-language', locale), language);
    for (const width of [1280, 1000, 768, 540, 390, 320]) {
      await page.setViewportSize({ width, height: 900 });
      await page.goto('/');
      await expect(page.locator('html')).toHaveAttribute('lang', language);
      await expect(page.locator('html')).toHaveAttribute('dir', ar ? 'rtl' : 'ltr');
      await settlePage(page);
      await expectNoAxeViolations(page, `landing at ${width}px (${language})`);
      const hasHorizontalOverflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
      expect(hasHorizontalOverflow, `horizontal overflow at ${width}px (${language})`).toBe(false);
    }
  });

  test(`authenticated navigation remains semantic, keyboard usable, and responsive (${language})`, async ({ page }) => {
    await page.addInitScript(({ locale, jwt }) => {
      localStorage.setItem('caregiver-language', locale);
      sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: jwt, baseUrl: '' }));
    }, { locale: language, jwt: testToken() });
    await page.route('**/api/**', (route) => route.fulfill({ status: 404, json: { code: 'NOT_FOUND' } }));
    // Start wide so the persistent sidebar (not the mobile drawer) is visible.
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto('/app');

    const navigation = page.getByRole('navigation', { name: labels.landmark, exact: true });
    await expect(navigation).toBeVisible();
    await expect(page.locator('.app-sidebar-brand')).toHaveAttribute('href', '/app');
    for (const destination of destinations) {
      const link = navigation.getByRole('link', { name: destination.name, exact: true });
      await expect(link).toHaveAttribute('href', destination.href);
      await expect(link).toBeVisible();
    }
    await expect(navigation.getByRole('link', { name: labels.overview, exact: true })).toHaveAttribute('aria-current', 'page');

    // Keyboard: focus a destination, activate it, and confirm the active marker.
    for (const destination of destinations) {
      const link = navigation.getByRole('link', { name: destination.name, exact: true });
      await link.focus();
      await expect(link).toBeFocused();
      await expect(link).toHaveCSS('outline-style', 'solid');
      await page.keyboard.press('Enter');
      await expect(page).toHaveURL(new RegExp(`${destination.href.replaceAll('/', '\\/')}$`));
      await expect(navigation.getByRole('link', { name: destination.name, exact: true })).toHaveAttribute('aria-current', 'page');
    }

    // Appearance and sign-out now live in the workspace account menu.
    await page.locator('.app-account > summary').click();
    const appearance = page.getByRole('combobox', { name: labels.appearance, exact: true });
    await expect(appearance).toBeVisible();
    await appearance.focus();
    await expect(appearance).toBeFocused();
    await page.keyboard.press('ArrowDown');
    await expect(appearance).toHaveValue('light');
    const signOut = page.getByRole('button', { name: labels.signOut, exact: true });
    await expect(signOut).toBeVisible();
    await signOut.focus();
    await expect(signOut).toHaveCSS('outline-style', 'solid');
    await page.locator('.app-account > summary').click();

    // The topbar language toggle flips direction and the navigation label.
    const languageToggle = page.getByRole('button', { name: labels.switch, exact: true });
    await languageToggle.focus();
    await expect(languageToggle).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.locator('html')).toHaveAttribute('dir', ar ? 'ltr' : 'rtl');
    await expect(page.getByRole('navigation', { name: ar ? 'Application navigation' : 'قائمة التطبيق', exact: true })).toBeVisible();
    await page.keyboard.press('Enter');
    await expect(page.locator('html')).toHaveAttribute('dir', ar ? 'rtl' : 'ltr');

    // Responsive: no horizontal overflow; narrow viewports use a drawer dialog.
    for (const width of [1280, 1000, 768, 540, 390, 320]) {
      await page.setViewportSize({ width, height: 900 });
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), `horizontal overflow at ${width}px (${language})`).toBe(true);
      if (width <= 768) {
        const trigger = page.getByRole('button', { name: labels.openNav, exact: true });
        await expect(trigger).toBeVisible();
        await trigger.click();
        const drawer = page.getByRole('dialog', { name: labels.landmark, exact: true });
        await expect(drawer).toBeVisible();
        for (const destination of destinations) {
          await expect(drawer.getByRole('link', { name: destination.name, exact: true })).toBeVisible();
        }
        await drawer.getByRole('link', { name: labels.overview, exact: true }).focus();
        await page.keyboard.press('Escape');
        await expect(drawer).toHaveCount(0);
      } else {
        await expect(navigation).toBeVisible();
      }
    }
  });
}