import { existsSync, readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const packageJson = JSON.parse(readFileSync(new URL('../../package.json', import.meta.url), 'utf8')) as {
  postbuild?: string;
};

const html = readFileSync(new URL('../../index.html', import.meta.url), 'utf8');
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
    expect(existsSync(new URL(`../../public/${asset}`, import.meta.url))).toBe(true);
  });

  it('defines a placeholder-free default for the build-time site URL', () => {
    const config = readFileSync(new URL('../../vite.config.ts', import.meta.url), 'utf8');
    expect(config.toLowerCase()).not.toContain('replace-with-production-domain');
    expect(config).toMatch(/VITE_SITE_URL\s*\|\|=\s*'https:\/\/[^']+'/);
  });

  it('wires an automated post-build guard that fails on leftover placeholders', () => {
    expect(readFileSync(new URL('../../package.json', import.meta.url), 'utf8'))
      .toContain('"postbuild"');
    expect(packageJson.postbuild).toMatch(/check-metadata\.mjs/);

    const guard = readFileSync(new URL('../../scripts/check-metadata.mjs', import.meta.url), 'utf8');
    expect(guard).toMatch(/REPLACE-WITH-/i);
    expect(guard).toContain('dist/index.html');
    expect(guard).toContain('process.exit(1)');
  });
});
