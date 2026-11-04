import { createRequire } from 'node:module';
import { expect, test, type Page } from '@playwright/test';
import { testToken } from '../src/tests/authFixtures';
import { observationFields } from '../src/api/notes';
import { addendumFixture, noteFixture } from '../src/tests/noteFixtures';
import { recipientFixture } from '../src/tests/recipientFixtures';

const require = createRequire(import.meta.url);
type Locale = 'en' | 'ar';
const labels = {
  en: {
    recipient: 'Care recipient', date: 'Observation date', text: 'Free-text observations',
    save: 'Save original note', saved: 'Original note saved.', original: 'Original note — read only',
    append: 'Add a correction', correctText: 'Correct this field: Free-text observations',
    saveAppend: 'Save addendum', appended: 'Addendum saved. The original note is unchanged.',
    switch: 'التغيير إلى العربية',
  },
  ar: {
    recipient: 'مستفيد الرعاية', date: 'تاريخ الملاحظة', text: 'ملاحظات نصية حرة',
    save: 'حفظ الملاحظة الأصلية', saved: 'تم حفظ الملاحظة الأصلية.', original: 'الملاحظة الأصلية — للقراءة فقط',
    append: 'إضافة تصحيح', correctText: 'تصحيح هذا الحقل: ملاحظات نصية حرة',
    saveAppend: 'حفظ الإضافة', appended: 'تم حفظ الإضافة. الملاحظة الأصلية لم تتغير.',
    switch: 'Switch to English',
  },
};

async function checkPageQuality(page: Page, locale: Locale) {
  await expect(page.locator('html')).toHaveAttribute('lang', locale);
  await expect(page.locator('html')).toHaveAttribute('dir', locale === 'ar' ? 'rtl' : 'ltr');
  await page.evaluate(async () => { await document.fonts.ready; });
  await page.addScriptTag({ path: require.resolve('axe-core/axe.min.js') });
  const violations = await page.evaluate(async () => {
    const axe = (window as unknown as Window & { axe: typeof import('axe-core') }).axe;
    const result = await axe.run(document, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
    });
    return result.violations.map(({ id, nodes }) => ({ id, targets: nodes.map(({ target }) => target) }));
  });
  expect(violations).toEqual([]);

  // Root overflow alone can miss a control clipped by an overflow-hidden parent.
  const layout = await page.evaluate(() => ({
    overflow: document.documentElement.scrollWidth > innerWidth,
    outside: [...document.querySelectorAll<HTMLElement>('main input, main textarea, main select, main button, main a[href]')]
      .filter((element) => element.getClientRects().length && getComputedStyle(element).visibility !== 'hidden')
      .filter((element) => { const box = element.getBoundingClientRect(); return box.left < -1 || box.right > innerWidth + 1; })
      .map((element) => element.id || element.textContent?.trim() || element.tagName),
  }));
  expect(layout).toEqual({ overflow: false, outside: [] });
}

for (const initial of ['en', 'ar'] as const) {
  test(`switches ${initial} drafts between languages without losing mixed text or duplicating saves`, async ({ page }) => {
    const next: Locale = initial === 'en' ? 'ar' : 'en';
    const first = labels[initial];
    const second = labels[next];
    const draft = '  تقرير عربي / English ID ABC-123 (10:30)\n<b>literal text</b>\n' + 'Mixedالعربية'.repeat(35) + '  ';
    const correction = '  تصحيح مستقل — English correction (09:15)\n' + 'LongTokenعربي'.repeat(25) + '  ';
    const recipient = { ...recipientFixture, name: 'مريم Hassan (ID 12345) ' + 'اسمطويلLongName'.repeat(5) };
    let note = { ...noteFixture };
    const addenda: typeof addendumFixture[] = [];
    const correctionHistory = () => ({
      originalNote: note,
      events: [],
      effectiveObservations: Object.fromEntries(observationFields.map((field) => {
        const observation = note.observations[field];
        return [field, {
          state: observation.state,
          value: observation.state === 'RECORDED' ? observation.value : null,
          rawValue: observation.state === 'LEGACY_AMBIGUOUS' ? observation.rawValue : null,
          lastOperation: null,
          eventId: null,
          actorId: null,
          updatedAt: note.createdAt,
        }];
      })),
    });
    const writes: { path: string; body: Record<string, unknown>; key: string | undefined }[] = [];
    const unexpected: string[] = [];
    const pageErrors: string[] = [];
    page.on('pageerror', (error) => pageErrors.push(error.message));

    await page.addInitScript(({ locale, token }) => {
      if (!localStorage.getItem('caregiver-language')) localStorage.setItem('caregiver-language', locale);
      sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token, baseUrl: '' }));
    }, { locale: initial, token: testToken() });

    // Synthetic API contracts only. Unrecognised requests fail instead of
    // silently receiving a generic fixture that could hide a routing regression.
    await page.route('**/api/**', async (route) => {
      const request = route.request();
      const path = new URL(request.url()).pathname;
      expect(request.headers().authorization).toMatch(/^Bearer /);
      if (request.method() === 'GET') {
        if (path === '/api/recipients') return route.fulfill({ json: [recipient] });
        if (path === `/api/recipients/${recipient.id}`) return route.fulfill({ json: recipient });
        if (path === `/api/notes/${note.id}`) return route.fulfill({ json: note });
        if (path === `/api/notes/${note.id}/addenda`) return route.fulfill({ json: addenda });
        if (path === `/api/notes/${note.id}/corrections`) return route.fulfill({ json: correctionHistory() });
      }
      if (request.method() === 'POST' && ['/api/notes', `/api/notes/${note.id}/addenda`].includes(path)) {
        const body = request.postDataJSON();
        writes.push({ path, body, key: request.headers()['idempotency-key'] });
        if (path === '/api/notes') {
          note = {
            ...note,
            ...body,
            observations: {
              ...note.observations,
              ...body.observations,
              medicationVerification: body.medicationVerification ?? note.observations.medicationVerification,
            },
          };
          return route.fulfill({ status: 201, json: note });
        }
        const addition = {
          ...addendumFixture,
          ...body,
          observations: {
            ...addendumFixture.observations,
            ...body.observations,
            medicationVerification: body.medicationVerification ?? addendumFixture.observations.medicationVerification,
          },
          providedFields: Object.keys(body.observations ?? {}),
          medicationVerificationProvided: body.medicationVerification !== undefined,
        };
        addenda.push(addition);
        note = { ...note, addendumCount: addenda.length };
        return route.fulfill({ status: 201, json: addition });
      }
      unexpected.push(`${request.method()} ${path}`);
      return route.fulfill({ status: 501, json: { code: 'UNEXPECTED_TEST_REQUEST' } });
    });

    await page.goto('/app/notes/new');
    await page.getByLabel(first.recipient, { exact: true }).selectOption(recipient.id);
    await page.getByLabel(first.date, { exact: true }).fill(note.date);
    await page.getByRole('group', { name: `${first.text} — ${initial === 'ar' ? 'حالة الملاحظة' : 'Observation status'}`, exact: true }).getByText(initial === 'ar' ? 'مسجّل' : 'Recorded', { exact: true }).click();
    await page.getByRole('textbox', { name: first.text, exact: true }).fill(draft);
    await checkPageQuality(page, initial);

    await page.getByRole('button', { name: first.switch, exact: true }).click();
    await page.locator('#note-recipientId').selectOption(recipient.id);
    await page.locator('#note-date').fill(note.date);
    await page.getByRole('group', { name: `${second.text} — ${next === 'ar' ? 'حالة الملاحظة' : 'Observation status'}`, exact: true }).getByText(next === 'ar' ? 'مسجّل' : 'Recorded', { exact: true }).click();
    await page.getByRole('textbox', { name: second.text, exact: true }).fill(draft);
    await expect(page.getByLabel(second.date, { exact: true })).toHaveAttribute('dir', 'ltr');
    expect(writes).toHaveLength(0);
    await checkPageQuality(page, next);

    await page.getByRole('button', { name: second.save, exact: true }).focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('status')).toHaveText(second.saved);
    await expect(page.getByRole('status')).toBeFocused();
    const original = () => page.getByRole('article', { name: labels[initial].original, exact: true });
    await expect(page.getByRole('article', { name: second.original, exact: true }).locator('.note-verbatim').last()).toHaveJSProperty('textContent', draft);
    expect(writes).toHaveLength(1);
    expect(writes[0].body).toMatchObject({
      recipientId: recipient.id, date: note.date,
      observations: { freeText: { state: 'RECORDED', value: draft } },
    });

    await page.getByRole('link', { name: second.append, exact: true }).focus();
    await page.keyboard.press('Enter');
    await page.getByRole('checkbox', { name: second.correctText, exact: true }).check();
    await page.getByRole('group', { name: `${second.text} — ${next === 'ar' ? 'حالة الملاحظة' : 'Observation status'}`, exact: true }).getByText(next === 'ar' ? 'مسجّل' : 'Recorded', { exact: true }).click();
    await page.getByRole('textbox', { name: second.text, exact: true }).fill(correction);
    await page.getByRole('button', { name: second.switch, exact: true }).click();
    await page.getByRole('checkbox', { name: `${first.correctText}`, exact: true }).check();
    await page.getByRole('group', { name: `${first.text} — ${initial === 'ar' ? 'حالة الملاحظة' : 'Observation status'}`, exact: true }).getByText(initial === 'ar' ? 'مسجّل' : 'Recorded', { exact: true }).click();
    await page.getByRole('textbox', { name: first.text, exact: true }).fill(correction);
    await checkPageQuality(page, initial);

    await page.getByRole('button', { name: first.saveAppend, exact: true }).focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('status')).toHaveText(first.appended);
    await expect(page.getByRole('status')).toBeFocused();
    await expect(original().locator('.note-verbatim').last()).toHaveJSProperty('textContent', draft);
    await expect(page.getByRole('article').last().locator('.note-verbatim').last()).toHaveJSProperty('textContent', correction);
    expect(writes).toHaveLength(2);
    expect(writes[1].body).toEqual({
      observations: { freeText: { state: 'RECORDED', value: correction } },
    });
    expect(writes.every(({ key }) => /^[a-f0-9]{32}$/.test(key ?? ''))).toBe(true);
    expect(writes[1].key).not.toBe(writes[0].key);
    await checkPageQuality(page, initial);

    await page.reload();
    await expect(original().locator('.note-verbatim').last()).toHaveJSProperty('textContent', draft);
    await expect(page.getByRole('article').last().locator('.note-verbatim').last()).toHaveJSProperty('textContent', correction);
    await checkPageQuality(page, initial);
    expect(writes).toHaveLength(2);
    expect(unexpected).toEqual([]);
    expect(pageErrors).toEqual([]);
  });
}
