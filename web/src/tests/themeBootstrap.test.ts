import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const indexHtml = readFileSync(resolve(repoRoot, 'web/index.html'), 'utf8');
const inlineScripts = [...indexHtml.matchAll(/<script>([\s\S]*?)<\/script>/g)].map((match) => match[1]);
const themeScript = inlineScripts.find((script) => script.includes('caregiver-theme-mode'));

function runBootstrap(options: {
  stored?: string | null;
  prefersDark?: boolean;
  storageThrows?: boolean;
  mediaThrows?: boolean;
} = {}) {
  if (!themeScript) throw new Error('index.html has no theme bootstrap script');
  const dataset: { theme?: string } = {};
  const meta = {
    content: '#fafaf6',
    setAttribute(_name: string, value: string) { this.content = value; },
  };
  const fakeWindow = {
    localStorage: {
      getItem() {
        if (options.storageThrows) throw new Error('storage blocked');
        return options.stored ?? null;
      },
    },
    matchMedia() {
      if (options.mediaThrows) throw new Error('matchMedia blocked');
      return { matches: options.prefersDark === true };
    },
  };
  const fakeDocument = { documentElement: { dataset }, querySelector: () => meta };
  new Function('window', 'document', themeScript)(fakeWindow, fakeDocument);
  return { theme: dataset.theme, color: meta.content };
}

describe('pre-paint theme bootstrap', () => {
  it('runs inline before the module script', () => {
    expect(themeScript).toBeDefined();
    expect(indexHtml.indexOf('caregiver-theme-mode')).toBeLessThan(indexHtml.indexOf('type="module"'));
  });

  it('applies stored dark on a light OS', () => {
    expect(runBootstrap({ stored: 'dark', prefersDark: false }))
      .toEqual({ theme: 'dark', color: '#121616' });
  });

  it('applies stored light on a dark OS', () => {
    expect(runBootstrap({ stored: 'light', prefersDark: true }))
      .toEqual({ theme: 'light', color: '#fafaf6' });
  });

  it.each([
    [undefined, true, { theme: 'system', color: '#121616' }],
    [null, false, { theme: 'system', color: '#fafaf6' }],
    ['sepia', true, { theme: 'system', color: '#121616' }],
    ['', false, { theme: 'system', color: '#fafaf6' }],
  ])('falls back to system for stored value %s', (stored, prefersDark, expected) => {
    expect(runBootstrap({ stored: stored as string | null, prefersDark: prefersDark as boolean }))
      .toEqual(expected);
  });

  it('does not throw when storage or matchMedia is unavailable', () => {
    expect(runBootstrap({ stored: 'dark', storageThrows: true, mediaThrows: true }))
      .toEqual({ theme: 'system', color: '#fafaf6' });
  });
});
