import { expect, test } from '@playwright/test';
import { correctionHistoryFixture } from './historyFixtures';
import { noteFixture } from './noteFixtures';
import { recipientFixture } from './recipientFixtures';

const recipientDetail = `/app/recipients/${recipientFixture.id}`;
const noteDetail = `/app/notes/${noteFixture.id}`;
const noteAppend = `${noteDetail}/addenda/new`;
const token = () => {
  const encode = (value: unknown) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return [
    encode({ alg: 'HS256' }),
    encode({ sub: '12345678-1234-1234-1234-123456789abc', exp: Math.floor(Date.now() / 1000) + 3600 }),
    'synthetic-signature',
  ].join('.');
};

test('protected caregiver detail and addendum routes survive direct loads and refresh', async ({ page }) => {
  await page.addInitScript((jwt) => {
    localStorage.setItem('caregiver-language', 'en');
    sessionStorage.setItem('caregiver-auth-session-v1', JSON.stringify({ token: jwt, baseUrl: '' }));
  }, token());

  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/recipients') return route.fulfill({ json: [recipientFixture] });
    if (path.startsWith('/api/recipients/')) return route.fulfill({ json: recipientFixture });
    if (path.endsWith('/addenda')) return route.fulfill({ json: [] });
    if (path.endsWith('/corrections')) return route.fulfill({ json: correctionHistoryFixture() });
    return route.fulfill({ json: noteFixture });
  });

  await page.goto(recipientDetail);
  await expect(page.getByRole('heading', { name: recipientFixture.name })).toBeVisible();
  const recipientReload = await page.reload();
  expect(recipientReload?.ok()).toBe(true);
  await expect(page.getByRole('heading', { name: recipientFixture.name })).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`${recipientDetail}$`));

  await page.goto(noteDetail);
  await expect(page.getByRole('heading', { name: 'Original note — read only' })).toBeVisible();
  const noteReload = await page.reload();
  expect(noteReload?.ok()).toBe(true);
  await expect(page.getByRole('heading', { name: 'Original note — read only' })).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`${noteDetail}$`));

  await page.goto(noteAppend);
  await expect(page.getByRole('form', { name: 'Add a correction' })).toBeVisible();
  const appendReload = await page.reload();
  expect(appendReload?.ok()).toBe(true);
  await expect(page.getByRole('form', { name: 'Add a correction' })).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`${noteAppend}$`));
});
