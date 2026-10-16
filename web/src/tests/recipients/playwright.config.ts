import { defineConfig } from '@playwright/test';
import base from '../../../playwright.config';

// Keep Task 3 browser coverage inside the issue's allowed test directory.
export default defineConfig({ ...base, testDir: '.', testMatch: '*.browser.ts' });
