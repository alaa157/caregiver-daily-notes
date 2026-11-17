import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const testsDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(testsDir, '../../..');
const read = (relativePath: string) => readFileSync(resolve(repoRoot, relativePath), 'utf8');

const publicCss = read('web/src/styles/public.css');
const baseCss = read('web/src/styles/base.css');
const landingCss = read('web/src/features/landing/landing.css');
const mainTsx = read('web/src/main.tsx');

describe('landing Arabic font subsets', () => {
  it('does not register the app font family name from public.css', () => {
    const faces = publicCss.match(/@font-face\s*\{[^}]*\}/g) ?? [];
    expect(faces.length).toBeGreaterThan(0);
    for (const face of faces) {
      expect(face).not.toContain("font-family:'Noto Sans Arabic Variable'");
      expect(face).not.toContain("font-family: 'Noto Sans Arabic Variable'");
    }
  });

  it('defines the two landing subsets under their own family names', () => {
    expect(publicCss).toMatch(/@font-face\{font-family:'Noto Sans Arabic Landing';[^}]*noto-sans-arabic-landing\.woff2/);
    expect(publicCss).toMatch(/@font-face\{font-family:'Noto Sans Arabic Mock';[^}]*noto-sans-arabic-mock\.woff2/);
  });

  it('keeps the app Arabic stack on the Fontsource family only', () => {
    const arabicRule = baseCss.match(/:lang\(ar\)\s*\{[^}]*\}/)?.[0] ?? '';
    expect(arabicRule).toContain("font-family: 'Noto Sans Arabic Variable'");
    expect(arabicRule).not.toContain('Noto Sans Arabic Landing');
    expect(arabicRule).not.toContain('Noto Sans Arabic Mock');
    expect(mainTsx).toContain('@fontsource-variable/noto-sans-arabic');
  });

  it('points the landing page and mock screens at the subset families', () => {
    const pageRule = landingCss.match(/\.landing-page\s*\{[^}]*\}/)?.[0] ?? '';
    expect(pageRule).toContain("'Noto Sans Arabic Landing'");
    expect(landingCss).toMatch(/:lang\(ar\) \.landing-page\s*\{[^}]*'Noto Sans Arabic Landing'/);

    const screenRule = landingCss.match(/\.landing-screen\s*\{[^}]*\}/)?.[0] ?? '';
    expect(screenRule).toContain("'Noto Sans Arabic Mock'");
  });
});
