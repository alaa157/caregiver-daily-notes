import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const testFilePath = fileURLToPath(import.meta.url);
const testsDir = dirname(testFilePath);
const repoRoot = resolve(testsDir, '../../..');

function read(relativePath: string): string {
  return readFileSync(resolve(repoRoot, relativePath), 'utf8');
}

const mainTsxSource = read('web/src/main.tsx');
const baseCssSource = read('web/src/styles/base.css');
const tokensCssSource = read('docs/design-system/tokens.css');

const aliasesInTokens: Record<string, string> = {};
for (const match of tokensCssSource.matchAll(/--((?:web-[A-Za-z0-9-]+|font-family-app)):\s*([^;]+);/g)) {
  aliasesInTokens[`--${match[1]}`] = match[2].trim();
}

const baseRootStart = baseCssSource.indexOf(':root {');
const baseRootEnd = baseCssSource.indexOf('}', baseRootStart);
const baseRootBlock = baseCssSource.slice(baseRootStart, baseRootEnd + 1);

const baseRootVars: Record<string, string> = {};
for (const match of baseRootBlock.matchAll(/--([A-Za-z0-9-]+):\s*([^;]+);/g)) {
  baseRootVars[`--${match[1]}`] = match[2].trim();
}

const baseRootProps: Record<string, string> = {};
for (const match of baseRootBlock.matchAll(/([A-Za-z-]+):\s*([^;]+);/g)) {
  baseRootProps[match[1]] = match[2].trim();
}

const baseRootVarRefs: string[] = [];
for (const match of baseRootBlock.matchAll(/var\((--[A-Za-z0-9-]+)\)/g)) {
  baseRootVarRefs.push(match[1]);
}

const tokensJson = JSON.parse(read('docs/design-system/design-tokens.json'));
const jsonWebAliases = (tokensJson.webAliases?.color ?? {}) as Record<string, { $value: string }>;
const jsonRadius = (tokensJson.webAliases?.radius?.default ?? {}) as { $value: string };

describe('design tokens', () => {
  describe('stylesheet import (main.tsx)', () => {
    it('loads the shared token stylesheet exactly once', () => {
      const importLines = mainTsxSource.split('\n').filter((line) =>
        line.trim().startsWith('import') && line.includes('design-system/tokens.css')
      );
      expect(importLines.length).toBe(1);
    });

    it('uses the exact relative path for the token stylesheet', () => {
      const importLine = mainTsxSource.split('\n').find((line) =>
        line.includes('design-system/tokens.css')
      );
      expect(importLine).toBeDefined();
      expect(importLine).toContain("'../../docs/design-system/tokens.css'");
    });

    it('imports tokens.css before base.css', () => {
      const lines = mainTsxSource.split('\n');
      let tokensIndex = -1;
      let baseIndex = -1;
      for (let i = 0; i < lines.length; i++) {
        if (lines[i].includes('design-system/tokens.css')) tokensIndex = i;
        if (lines[i].includes("'./styles/base.css'")) baseIndex = i;
      }
      expect(tokensIndex).toBeGreaterThanOrEqual(0);
      expect(baseIndex).toBeGreaterThanOrEqual(0);
      expect(tokensIndex).toBeLessThan(baseIndex);
    });

    it('token stylesheet resolves to an actual file', () => {
      expect(() => read('docs/design-system/tokens.css')).not.toThrow();
      const tokensContent = read('docs/design-system/tokens.css');
      expect(tokensContent.includes('--web-color-ink:#213b32;')).toBe(true);
      expect(tokensContent.includes('--web-radius:18px;')).toBe(true);
    });
  });

  describe('existing token preservation', () => {
    it('does not change existing token names or values from base branch', () => {
      const existingNames = [
        '--color-primary', '--color-on-primary', '--color-background', '--color-surface',
        '--color-error', '--caregiver-ink', '--caregiver-muted',
        '--caregiver-background', '--caregiver-surface', '--caregiver-border',
        '--radius-card', '--radius-control', '--space-xs', '--space-md', '--space-lg',
        '--font-weight-regular', '--font-weight-medium', '--font-weight-bold',
      ];
      for (const name of existingNames) {
        const pattern = new RegExp(`${name}:[^;]+;`);
        const match = tokensCssSource.match(pattern);
        expect(match).not.toBeNull();
      }
    });

    it('preserves light theme values', () => {
      const rootStart = tokensCssSource.indexOf(':root {');
      const darkStart = tokensCssSource.indexOf('[data-theme=\'dark\'] {');
      const lightBlock = tokensCssSource.slice(rootStart, darkStart);
      expect(lightBlock.includes('--color-background:#F7F6F3;')).toBe(true);
      expect(lightBlock.includes('--color-surface:#FAF9F5;')).toBe(true);
      expect(lightBlock.includes('--caregiver-border:#C5CDD1;')).toBe(true);
    });

    it('preserves dark theme values through the shared dark palette', () => {
      const paletteStart = tokensCssSource.indexOf('/* Dark palette');
      const darkStart = tokensCssSource.indexOf('[data-theme=\'dark\'] {');
      const systemStart = tokensCssSource.indexOf('/* System mode follows OS dark');
      const paletteBlock = tokensCssSource.slice(paletteStart, darkStart);
      const darkBlock = tokensCssSource.slice(darkStart, systemStart);
      expect(paletteBlock.includes('--dark-color-background:#121616;')).toBe(true);
      expect(paletteBlock.includes('--dark-color-surface:#1A1F1F;')).toBe(true);
      expect(paletteBlock.includes('--dark-caregiver-border:#3A4548;')).toBe(true);
      expect(darkBlock.includes('--color-background:var(--dark-color-background);')).toBe(true);
      expect(darkBlock.includes('--caregiver-border:var(--dark-caregiver-border);')).toBe(true);
    });

    it('preserves system mode media query values through the shared dark palette', () => {
      const systemStart = tokensCssSource.indexOf('@media (prefers-color-scheme:dark)');
      const paletteStart = tokensCssSource.indexOf('/* Selected accent palettes');
      const systemBlock = tokensCssSource.slice(systemStart, paletteStart);
      expect(systemBlock.includes('--color-background:var(--dark-color-background);')).toBe(true);
      expect(systemBlock.includes('--color-surface:var(--dark-color-surface);')).toBe(true);
    });

    it('defines dark values once and keeps both theme paths in sync', () => {
      const darkStart = tokensCssSource.indexOf('[data-theme=\'dark\'] {');
      const systemStart = tokensCssSource.indexOf('/* System mode follows OS dark');
      const paletteStart = tokensCssSource.indexOf('/* Selected accent palettes');
      const tokenNames = (block: string) => new Set(
        [...block.matchAll(/(--(?:color|caregiver)-[a-z0-9-]+)\s*:/g)].map((match) => match[1]),
      );
      const darkTokens = tokenNames(tokensCssSource.slice(darkStart, systemStart));
      const systemTokens = tokenNames(tokensCssSource.slice(systemStart, paletteStart));
      expect(darkTokens.size).toBeGreaterThan(0);
      expect(systemTokens).toEqual(darkTokens);
      for (const name of darkTokens) {
        expect(tokensCssSource).toContain(`--dark-${name.slice(2)}:`);
      }
    });

    it('names the shipped font stack in the app font token', () => {
      expect(tokensCssSource.includes(
        "--font-family-app:'DM Sans Variable','Noto Sans Arabic Variable',system-ui,sans-serif;")).toBe(true);
      expect(tokensCssSource.includes('--font-weight-regular:400;')).toBe(true);
    });
  });

  describe('semantic alias existence', () => {
    it('has web aliases for every base.css root color/radius declaration', () => {
      const expectedAliases = [
        '--web-color-ink', '--web-color-background', '--web-color-muted',
        '--web-color-green', '--web-color-green-dark', '--web-color-border',
        '--web-color-surface', '--web-color-soft', '--web-radius',
      ];
      for (const alias of expectedAliases) {
        expect(aliasesInTokens[alias]).toBeDefined();
      }
    });

    it('every alias used by base.css root is declared in tokens.css', () => {
      for (const ref of baseRootVarRefs) {
        expect(aliasesInTokens[ref]).toBeDefined();
      }
    });

    it('has no unresolved custom-property references in migrated root declarations', () => {
      for (const ref of baseRootVarRefs) {
        expect(aliasesInTokens[ref]).toBeDefined();
      }
    });

    it('base.css root uses aliases instead of hard-coded literals', () => {
      expect(baseRootVars['--color-ink']).toBe('var(--web-color-ink)');
      expect(baseRootVars['--color-muted']).toBe('var(--web-color-muted)');
      expect(baseRootVars['--color-green']).toBe('var(--web-color-green)');
      expect(baseRootVars['--color-green-dark']).toBe('var(--web-color-green-dark)');
      expect(baseRootVars['--color-border']).toBe('var(--web-color-border)');
      expect(baseRootVars['--color-surface']).toBe('var(--web-color-surface)');
      expect(baseRootVars['--color-soft']).toBe('var(--web-color-soft)');
      expect(baseRootVars['--radius']).toBe('var(--web-radius)');
    });

    it('base.css color and background properties use aliases', () => {
      expect(baseRootProps['color']).toBe('var(--web-color-ink)');
      expect(baseRootProps['background']).toBe('var(--web-color-background)');
    });
  });

  describe('no remaining duplicate root literals', () => {
    it('root color/radius declarations contain no hard-coded hex literals', () => {
      const hexInRoot = baseRootBlock.match(/#[0-9a-fA-F]{3,8}/g);
      expect(hexInRoot).toBeNull();
    });

    it('root radius declaration contains no hard-coded pixel literal', () => {
      const radiusDecl = baseRootBlock.match(/--radius:\s*([^;]+);/);
      expect(radiusDecl?.[1].trim()).toBe('var(--web-radius)');
      expect(radiusDecl?.[1].trim()).not.toMatch(/^\d+px$/);
    });
  });

  describe('exact value equivalence', () => {
    it('web alias values exactly match original base.css literals', () => {
      const expected: Record<string, string> = {
        '--web-color-ink': '#213b32',
        '--web-color-background': '#fafaf6',
        '--web-color-muted': '#59695f',
        '--web-color-green': '#285b45',
        '--web-color-green-dark': '#1d4635',
        '--web-color-border': '#dde4d8',
        '--web-color-surface': '#fff',
        '--web-color-soft': '#eef2e8',
        '--web-radius': '18px',
      };
      for (const [alias, expectedValue] of Object.entries(expected)) {
        expect(aliasesInTokens[alias]).toBe(expectedValue);
      }
    });

    it('JSON web aliases match CSS token values', () => {
      expect(jsonWebAliases.ink.$value).toBe('#213b32');
      expect(jsonWebAliases.background.$value).toBe('#fafaf6');
      expect(jsonWebAliases.muted.$value).toBe('#59695f');
      expect(jsonWebAliases.green.$value).toBe('#285b45');
      expect(jsonWebAliases.greenDark.$value).toBe('#1d4635');
      expect(jsonWebAliases.border.$value).toBe('#dde4d8');
      expect(jsonWebAliases.surface.$value).toBe('#fff');
      expect(jsonWebAliases.soft.$value).toBe('#eef2e8');
      expect(jsonRadius.$value).toBe('18px');
    });
  });

  describe('typography preservation', () => {
    it('retains DM Sans Variable font import', () => {
      expect(mainTsxSource.includes("@fontsource-variable/dm-sans")).toBe(true);
    });

    it('retains Noto Sans Arabic Variable font import', () => {
      expect(mainTsxSource.includes("@fontsource-variable/noto-sans-arabic")).toBe(true);
    });

    it('base.css consumes the app font token instead of a hardcoded stack', () => {
      expect(baseRootProps['font-family']).toBe('var(--font-family-app)');
      expect(aliasesInTokens['--font-family-app']).toContain("'DM Sans Variable'");
      expect(aliasesInTokens['--font-family-app']).toContain("'Noto Sans Arabic Variable'");
    });

    it('base.css Arabic override is the Arabic-first ordering of the same families', () => {
      expect(baseCssSource).toContain(
        "font-family: 'Noto Sans Arabic Variable', 'DM Sans Variable', system-ui, sans-serif;");
    });

    it('font token names DM Sans and Noto Sans Arabic, not IBM Plex', () => {
      expect(tokensCssSource).toContain(
        "--font-family-app:'DM Sans Variable','Noto Sans Arabic Variable',system-ui,sans-serif;");
      expect(tokensCssSource).not.toContain('IBM Plex Sans Arabic');
    });
  });

  describe('JSON synchronization', () => {
    it('design-tokens.json parses successfully', () => {
      expect(() => JSON.parse(read('docs/design-system/design-tokens.json'))).not.toThrow();
    });

    it('every documented web alias color has a matching CSS definition', () => {
      const cssAliases = aliasesInTokens;
      for (const key of Object.keys(jsonWebAliases)) {
        const cssName = key === 'greenDark' ? '--web-color-green-dark' : `--web-color-${key}`;
        expect(cssAliases[cssName]).toBeDefined();
      }
    });

    it('every documented web alias radius has a matching CSS definition', () => {
      expect(aliasesInTokens['--web-radius']).toBe(jsonRadius.$value);
    });
  });
});
