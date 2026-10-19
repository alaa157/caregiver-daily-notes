import { createRequire } from 'node:module';
import { expect, test, type Page } from '@playwright/test';
import { recipientFixture } from '../recipientFixtures';
import { addendumFixture, noteFixture, noteObservations } from '../noteFixtures';

const require = createRequire(import.meta.url);
const root = '/app/notes';
const detail = `${root}/${noteFixture.id}`;
const encode = (value: unknown) => Buffer.from(JSON.stringify(value)).toString('base64url');
const token = () => [encode({ alg: 'HS256' }), encode({ sub: '12345678-1234-1234-1234-123456789abc', exp: Math.floor(Date.now() / 1000) + 3600 }), 'synthetic-signature'].join('.');
async function accessibility(page: Page) {
  await page.evaluate(async () => { await document.fonts.ready; });
  await page.evaluate(async () => { await Promise.allSettled(document.getAnimations().filter((animation) => animation.effect?.getTiming().iterations !== Infinity).map((animation) => animation.finished)); });
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
  const label = {
    create: ar ? 'تسجيل ملاحظة يومية' : 'Record a daily note', recipient: ar ? 'المستفيد من الرعاية' : 'Care recipient', date: ar ? 'تاريخ الملاحظة' : 'Observation date',
    mood: ar ? 'الحالة المزاجية' : 'Mood', appetite: ar ? 'الشهية' : 'Appetite', sleep: ar ? 'النوم' : 'Sleep', mobility: ar ? 'الحركة' : 'Mobility',
    medication: ar ? 'تناول الدواء' : 'Medication taken', verified: ar ? 'تم التحقق من تقرير الدواء' : 'Medication report verified', pain: ar ? 'درجة الألم (٠–١٠)' : 'Pain score (0–10)', falls: ar ? 'الإبلاغ عن سقوط' : 'Fall reported', text: ar ? 'الملاحظات النصية' : 'Free-text observations',
    save: ar ? 'حفظ الملاحظة الأصلية' : 'Save original note', saved: ar ? 'تم حفظ الملاحظة الأصلية.' : 'Original note saved.',
    original: ar ? 'الملاحظة الأصلية — للقراءة فقط' : 'Original note — read only', correct: ar ? 'تصحيح هذا الحقل' : 'Correct this field',
    append: ar ? 'إضافة تصحيح' : 'Add a correction', saveAppend: ar ? 'حفظ الإضافة' : 'Save addendum', appended: ar ? 'تم حفظ الإضافة. لم تتغير الملاحظة الأصلية.' : 'Addendum saved. The original note is unchanged.',
    retry: ar ? 'إعادة محاولة الحفظ نفسه' : 'Retry the same save', open: ar ? 'فتح ملاحظة هذا التاريخ' : 'Open note for this date', new: ar ? 'تسجيل ملاحظة أخرى' : 'Record another note', reload: ar ? 'إعادة المحاولة' : 'Retry',
  };
  test.describe(`daily notes ${language}`, () => {
    test.beforeEach(async ({ page }) => {
      await page.addInitScript(({ locale, jwt }) => {
        localStorage.setItem('caregiver-language', locale);
        sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: jwt, baseUrl: '' }));
      }, { locale: language, jwt: token() });
    });
    async function metadata(page: Page) {
      await page.getByLabel(label.recipient, { exact: true }).selectOption(recipientFixture.id);
      await page.getByLabel(label.date, { exact: true }).fill(noteFixture.date);
    }
    test('creates all observations, preserves mixed text, and appends a correction without changing the original', async ({ page }, info) => {
      let original = { ...noteFixture };
      const addenda: typeof addendumFixture[] = [];
      const writes: { method: string; body: Record<string, unknown>; key: string | undefined }[] = [];
      await page.route('**/api/**', async (route) => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        expect(request.headers()['authorization']).toMatch(/^Bearer /);
        if (request.method() === 'POST') {
          const body = request.postDataJSON();
          writes.push({ method: request.method(), body, key: request.headers()['idempotency-key'] });
          expect(body).not.toHaveProperty('caregiverId');
          if (path === '/api/notes') { original = { ...noteFixture, ...body }; return route.fulfill({ status: 201, json: original }); }
          const addition = { ...addendumFixture, ...body };
          addenda.push(addition);
          return route.fulfill({ status: 201, json: addition });
        }
        return route.fulfill({ json: path === '/api/recipients' ? [recipientFixture] : path.startsWith('/api/recipients/') ? recipientFixture : path.endsWith('/addenda') ? addenda : original });
      });
      await page.goto(`${root}/new`);
      await expect(page).toHaveTitle(new RegExp(label.create));
      await expect(page.locator('html')).toHaveAttribute('dir', ar ? 'rtl' : 'ltr');
      await page.getByRole('button', { name: label.save, exact: true }).click();
      await expect(page.getByLabel(label.recipient, { exact: true })).toBeFocused();
      await accessibility(page);
      await metadata(page);
      for (const [name, value] of [[label.mood, noteObservations.mood!], [label.appetite, noteObservations.appetite!], [label.sleep, noteObservations.sleep!], [label.mobility, noteObservations.mobility!]] as const) await page.getByLabel(name, { exact: true }).fill(value);
      await page.getByLabel(label.medication, { exact: true }).selectOption('UNKNOWN');
      await page.getByLabel(label.verified, { exact: true }).selectOption('false');
      await page.getByLabel(label.pain, { exact: true }).fill('0');
      await page.getByLabel(label.falls, { exact: true }).selectOption('false');
      await page.getByLabel(label.text, { exact: true }).fill(noteObservations.freeText!);
      await page.screenshot({ path: info.outputPath(`note-form-${language}.png`), fullPage: true });
      await page.getByRole('button', { name: label.save, exact: true }).click();
      await expect(page.getByText(label.saved, { exact: true })).toBeFocused();
      expect(writes[0].body).toEqual({ ...noteObservations, recipientId: recipientFixture.id, date: noteFixture.date });
      expect(writes[0].key).toMatch(/^[a-f0-9]{32}$/);
      const article = page.getByRole('article', { name: label.original, exact: true });
      await expect(article.locator('.note-verbatim').last()).toHaveJSProperty('textContent', noteObservations.freeText);
      await expect(page.getByRole('textbox')).toHaveCount(0);
      await accessibility(page);
      await page.getByRole('link', { name: label.append, exact: true }).focus();
      await page.keyboard.press('Enter');
      await page.getByRole('button', { name: label.saveAppend, exact: true }).click();
      await expect(page.getByRole('checkbox').first()).toBeFocused();
      await accessibility(page);
      await page.getByRole('checkbox', { name: `${label.correct}: ${label.medication}`, exact: true }).focus();
      await page.keyboard.press('Space');
      await page.getByLabel(label.medication, { exact: true }).selectOption('MISSED');
      await page.getByRole('checkbox', { name: `${label.correct}: ${label.verified}`, exact: true }).check();
      await page.getByLabel(label.verified, { exact: true }).selectOption('false');
      await page.getByRole('checkbox', { name: `${label.correct}: ${label.text}`, exact: true }).check();
      await page.getByLabel(label.text, { exact: true }).fill(addendumFixture.freeText!);
      await page.getByRole('button', { name: label.saveAppend, exact: true }).click();
      await expect(page.getByText(label.appended, { exact: true })).toBeFocused();
      expect(writes[1].body).toEqual({ medicationStatus: 'MISSED', medicationVerified: false, freeText: addendumFixture.freeText });
      expect(writes[1].key).not.toBe(writes[0].key);
      await expect(page.getByRole('article', { name: label.original, exact: true }).locator('.note-verbatim').last()).toHaveJSProperty('textContent', noteObservations.freeText);
      await expect(page.getByRole('article')).toHaveCount(2);
      await accessibility(page);
      await page.screenshot({ path: info.outputPath(`note-details-${language}.png`), fullPage: true });
    });
    test('retries a lost save with the same key and locked values', async ({ page }) => {
      const writes: { key: string | undefined; body: unknown }[] = [];
      await page.route('**/api/**', async (route) => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (request.method() === 'POST') {
          writes.push({ key: request.headers()['idempotency-key'], body: request.postDataJSON() });
          if (writes.length === 1) return route.abort('failed');
          return route.fulfill({ status: 201, json: noteFixture });
        }
        return route.fulfill({ json: path === '/api/recipients' ? [recipientFixture] : path.startsWith('/api/recipients/') ? recipientFixture : path.endsWith('/addenda') ? [] : noteFixture });
      });
      await page.goto(`${root}/new`);
      await metadata(page);
      await page.getByRole('button', { name: label.save, exact: true }).click();
      await expect(page.getByRole('alert')).toBeFocused();
      await expect(page.getByLabel(label.text, { exact: true })).toBeDisabled();
      await accessibility(page);
      await page.getByRole('button', { name: label.retry, exact: true }).click();
      await expect(page.getByText(label.saved, { exact: true })).toBeVisible();
      expect(writes).toHaveLength(2);
      expect(writes[1]).toEqual(writes[0]);
    });
    test('handles empty, retryable errors, missing notes and date conflicts accessibly', async ({ page }) => {
      let phase: 'error' | 'empty' | 'ready' = 'error';
      await page.route('**/api/**', async (route) => {
        const path = new URL(route.request().url()).pathname;
        if (path === '/api/recipients') return phase === 'error' ? route.fulfill({ status: 500, json: { message: 'private diagnostics' } }) : route.fulfill({ json: phase === 'empty' ? [] : [recipientFixture] });
        if (path.includes('/by-recipient/')) return route.fulfill({ status: 404, json: {} });
        return route.fulfill({ status: 409, json: { code: 'NOTE_CONFLICT', message: 'private diagnostics' } });
      });
      await page.goto(`${root}/new`);
      await expect(page.getByRole('alert')).toBeVisible();
      await accessibility(page);
      phase = 'empty';
      await page.getByRole('button', { name: label.reload, exact: true }).click();
      await expect(page.getByText(ar ? 'لا يوجد مستفيدون متاحون من الرعاية.' : 'No care recipients are available.', { exact: true })).toBeVisible();
      await accessibility(page);
      phase = 'ready';
      await page.reload();
      await metadata(page);
      await page.getByRole('button', { name: label.open, exact: true }).click();
      await expect(page.getByRole('alert')).toBeFocused();
      await accessibility(page);
      await page.getByRole('button', { name: label.save, exact: true }).click();
      await expect(page.getByRole('alert')).toBeFocused();
      await expect(page.getByText('private diagnostics', { exact: true })).toHaveCount(0);
      await expect(page.getByLabel(label.text, { exact: true })).toBeEnabled();
      await accessibility(page);
    });
    test('retries uncertain addenda without changing the original and clears rejected sessions', async ({ page }) => {
      let count = 0;
      let rejectSession = false;
      const keys: string[] = [];
      await page.route('**/api/**', async (route) => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (rejectSession) return route.fulfill({ status: 401, json: {} });
        if (request.method() === 'POST') {
          keys.push(request.headers()['idempotency-key']); count++;
          return count === 1 ? route.fulfill({ status: 500, json: {} }) : route.fulfill({ status: 201, json: addendumFixture });
        }
        return route.fulfill({ json: path.startsWith('/api/recipients/') ? recipientFixture : path.endsWith('/addenda') ? (count ? [addendumFixture] : []) : noteFixture });
      });
      await page.goto(`${detail}/addenda/new`);
      await page.getByRole('checkbox', { name: `${label.correct}: ${label.text}`, exact: true }).check();
      await page.getByLabel(label.text, { exact: true }).fill(addendumFixture.freeText!);
      await page.getByRole('button', { name: label.saveAppend, exact: true }).click();
      await expect(page.getByRole('alert')).toBeFocused();
      await accessibility(page);
      await page.getByRole('button', { name: label.retry, exact: true }).click();
      await expect(page.getByText(label.appended, { exact: true })).toBeVisible();
      expect(keys[1]).toBe(keys[0]);
      await expect(page.getByRole('article', { name: label.original, exact: true }).locator('.note-verbatim').last()).toHaveJSProperty('textContent', noteObservations.freeText);
      rejectSession = true;
      await page.reload();
      await expect(page).toHaveURL(/\/login$/);
      await expect(page.getByRole('alert')).toBeVisible();
      await accessibility(page);
    });
  });
}
