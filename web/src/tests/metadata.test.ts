import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

// Resolve through fileURLToPath: Vite rewrites static `new URL(..., import.meta.url)`
// asset references, which breaks readFileSync in the test transform.
const webRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const readWeb = (relativePath: string) => readFileSync(resolve(webRoot, relativePath), 'utf8');

const packageJson = JSON.parse(readWeb('package.json')) as {
  scripts?: { postbuild?: string };
};

const html = readWeb('index.html');
const attribute = (pattern: RegExp) => html.match(pattern)?.[1] ?? '';

describe('index.html social metadata', () => {
  it('never ships a production-domain placeholder', () => {
    expect(html.toLowerCase()).not.toContain('replace-with-production-domain');
  });

  it.each([
    ['og:url', /<meta\s+property="og:url"\s+content="([^"]+)"/],
    ['canonical', /<link\s+rel="canonical"\s+href="([^"]+)"/],
  ])('%s resolves through the build-time site URL', (_name, pattern) => {
    expect(attribute(pattern)).toBe('%VITE_SITE_URL%/');
  });

  it.each([
    ['og:image', /<meta\s+property="og:image"\s+content="([^"]+)"/],
    ['twitter:image', /<meta\s+name="twitter:image"\s+content="([^"]+)"/],
  ])('%s is an absolute URL to a real PNG in public/', (_name, pattern) => {
    const value = attribute(pattern);
    expect(value).toMatch(/^%VITE_SITE_URL%\/\S+\.png$/i);
    const asset = value.slice('%VITE_SITE_URL%/'.length);
    expect(existsSync(resolve(webRoot, 'public', asset))).toBe(true);
  });

  it('defines a placeholder-free default for the build-time site URL', () => {
    const config = readWeb('vite.config.ts');
    expect(config.toLowerCase()).not.toContain('replace-with-production-domain');
    expect(config).toMatch(/VITE_SITE_URL\s*\|\|=\s*'https:\/\/[^']+'/);
  });

  it('wires an automated post-build guard that fails on leftover placeholders', () => {
    expect(readWeb('package.json'))
      .toContain('"postbuild"');
    expect(packageJson.scripts?.postbuild).toMatch(/check-metadata\.mjs/);

    const guard = readWeb('scripts/check-metadata.mjs');
    expect(guard).toMatch(/REPLACE-WITH-/i);
    expect(guard).toContain('dist/index.html');
    expect(guard).toContain('process.exit(1)');
  });
});
