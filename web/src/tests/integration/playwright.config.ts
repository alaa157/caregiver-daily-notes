import { defineConfig } from '@playwright/test';
import base from '../../../playwright.config';

// Explicit opt-in: these tests create synthetic accounts/records in a disposable
// local backend. Never point them at the shared or production database.
const target = new URL(process.env.CAREGIVER_TEST_API_URL || 'http://127.0.0.1:8080');
if (!['127.0.0.1', 'localhost', '[::1]'].includes(target.hostname)
  || !['http:', 'https:'].includes(target.protocol)
  || target.username || target.password || target.search || target.hash
  || target.pathname !== '/') {
  throw new Error('CAREGIVER_TEST_API_URL must be a loopback backend origin.');
}

export default defineConfig({
  ...base,
  testDir: '.',
  testMatch: '*.live.ts',
  outputDir: '../../../test-results-integration',
  workers: 1,
  retries: 0,
  timeout: 60_000,
  use: { ...base.use, trace: 'off', screenshot: 'off', video: 'off' },
  // The existing contract suites already exercise all four viewport sizes.
  projects: [base.projects![0], base.projects![3]],
});
