import { defineConfig } from '@playwright/test';
import base from '../../../playwright.config';

// Explicit opt-in: these suites create records in a disposable local database.
const target = new URL(process.env.CAREGIVER_TEST_API_URL || 'http://127.0.0.1:8080');
if (!['127.0.0.1', 'localhost', '[::1]'].includes(target.hostname)
  || !['http:', 'https:'].includes(target.protocol) || target.pathname !== '/'
  || target.username || target.password || target.search || target.hash) {
  throw new Error('Use a disposable loopback backend origin.');
}

export default defineConfig({
  ...base,
  testDir: '..',
  testMatch: [
    '**/integration/*.live.ts', '**/notes-live/*.live.ts', '**/history-live/*.live.ts',
    '**/summary-live/*.live.ts', '**/plans-live/*.live.ts', '**/workflow-live/*.live.ts',
  ],
  outputDir: '../../../test-results/live',
  workers: 1,
  retries: 0,
  timeout: 90_000,
  use: { ...base.use, trace: 'off', screenshot: 'off', video: 'off' },
  projects: [base.projects![0], base.projects![3]],
});
