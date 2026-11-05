import { expect, test } from '@playwright/test';
import { testToken } from '../src/tests/authFixtures';

for (const language of ['en', 'ar'] as const) {
  const ar = language === 'ar';
  const labels = {
    landmark: ar ? 'القائمة الرئيسية' : 'Main navigation',
    group: ar ? 'قائمة مساحة العمل' : 'Workspace navigation',
    home: ar ? 'الرئيسية' : 'Home',
    people: ar ? 'المستفيدون' : 'People',
    notes: ar ? 'الملاحظات' : 'Notes',
    history: ar ? 'سجل الملاحظات' : 'History',
    plans: ar ? 'خطط الرعاية' : 'Care plans',
    appearance: ar ? 'المظهر' : 'Appearance',
    switch: ar ? 'Switch to English' : 'التغيير إلى العربية',
    signOut: ar ? 'تسجيل الخروج' : 'Sign out',
  };
  const destinations = [
    { name: labels.home, href: '/app' },
    { name: labels.people, href: '/app/recipients' },
    { name: labels.notes, href: '/app/notes/new' },
    { name: labels.history, href: '/app/notes' },
  ];

  test(`authenticated navigation remains semantic, keyboard usable, and responsive (${language})`, async ({ page }) => {
    await page.addInitScript(({ locale, jwt }) => {
      localStorage.setItem('caregiver-language', locale);
      sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: jwt, baseUrl: '' }));
    }, { locale: language, jwt: testToken() });
    await page.route('**/api/**', (route) => route.fulfill({ status: 404, json: { code: 'NOT_FOUND' } }));
    await page.goto('/app');

    const navigation = page.getByRole('navigation', { name: labels.landmark, exact: true });
    const primary = navigation.getByRole('group', { name: labels.group, exact: true });
    await expect(navigation).toBeVisible();
    await expect(page.locator('.brand')).toHaveAttribute('href', '/app');
    await expect(primary.getByRole('link', { name: labels.home, exact: true })).toHaveAttribute('href', '/app');
    await expect(primary.getByRole('link')).toHaveCount(4);
    for (const destination of destinations) {
      const link = primary.getByRole('link', { name: destination.name, exact: true });
      await expect(link).toHaveAttribute('href', destination.href);
      await expect(link).toBeVisible();
    }
    const plans = navigation.getByRole('link', { name: labels.plans, exact: true });
    await expect(plans).toHaveAttribute('href', '/app/notes/plans');
    await expect(primary.getByRole('link', { name: labels.plans, exact: true })).toHaveCount(0);
    await expect(primary.getByRole('link', { name: labels.home, exact: true })).toHaveAttribute('aria-current', 'page');

    for (const destination of destinations) {
      const link = primary.getByRole('link', { name: destination.name, exact: true });
      await link.focus();
      await expect(link).toBeFocused();
      await expect(link).toHaveCSS('outline-style', 'solid');
      await page.keyboard.press('Enter');
      await expect(page).toHaveURL(new RegExp(`${destination.href.replaceAll('/', '\\/')}$`));
      await expect(primary.getByRole('link', { name: destination.name, exact: true })).toHaveAttribute('aria-current', 'page');
      await expect(primary.getByRole('link', { name: destination.name, exact: true })).toHaveClass(/active/);
    }
    await plans.focus();
    await expect(plans).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/app\/notes\/plans$/);
    await expect(plans).toHaveAttribute('aria-current', 'page');

    const appearance = page.getByRole('combobox', { name: labels.appearance, exact: true });
    await appearance.focus();
    await expect(appearance).toBeFocused();
    await page.keyboard.press('ArrowDown');
    await expect(appearance).toHaveValue('light');
    const languageToggle = page.getByRole('button', { name: labels.switch, exact: true });
    await languageToggle.focus();
    await expect(languageToggle).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(page.locator('html')).toHaveAttribute('dir', ar ? 'ltr' : 'rtl');
    await expect(page.getByRole('navigation', { name: ar ? 'Main navigation' : 'القائمة الرئيسية', exact: true })).toBeVisible();
    await page.keyboard.press('Enter');
    await expect(page.locator('html')).toHaveAttribute('dir', ar ? 'rtl' : 'ltr');

    const signOut = page.getByRole('button', { name: labels.signOut, exact: true });
    await signOut.focus();
    await expect(signOut).toBeFocused();
    await expect(signOut).toHaveCSS('outline-style', 'solid');

    for (const width of [1280, 1000, 768, 540, 390, 320]) {
      await page.setViewportSize({ width, height: 900 });
      await expect(navigation).toBeVisible();
      for (const destination of destinations) await expect(primary.getByRole('link', { name: destination.name, exact: true })).toBeVisible();
      await expect(appearance).toBeVisible();
      await expect(page.getByRole('button', { name: labels.switch, exact: true })).toBeVisible();
      await expect(signOut).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), `horizontal overflow at ${width}px (${language})`).toBe(true);
      const bounds = await navigation.evaluate((element) => {
        const { left, right } = element.getBoundingClientRect();
        return { left, right, width: window.innerWidth };
      });
      expect(bounds.left, `navigation clipped at ${width}px (${language})`).toBeGreaterThanOrEqual(0);
      expect(bounds.right, `navigation clipped at ${width}px (${language})`).toBeLessThanOrEqual(bounds.width);
    }
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/login$/);
  });
}
