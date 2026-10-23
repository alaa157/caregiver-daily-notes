import { defineConfig } from '@playwright/test';
import base from '../../../playwright.config';

const backend = new URL(process.env.CAREGIVER_TEST_API_URL || 'http://127.0.0.1:8080');
if (!['127.0.0.1', 'localhost', '[::1]'].includes(backend.hostname)
  || !['http:', 'https:'].includes(backend.protocol) || backend.pathname !== '/'
  || backend.username || backend.password || backend.search || backend.hash) {
  throw new Error('Use a loopback backend origin with a disposable synthetic database.');
}
export default defineConfig({
  ...base, testDir: '.', testMatch: '*.live.ts', outputDir: '../../../test-results/history-live', workers: 1, retries: 0, timeout: 60_000,
  use: { ...base.use, trace: 'off', screenshot: 'off', video: 'off' },
  projects: [base.projects![0], base.projects![3]],
});
