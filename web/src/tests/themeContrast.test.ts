import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const tokensCss = readFileSync(resolve(repositoryRoot, 'docs/design-system/tokens.css'), 'utf8')
  .replace(/\/\*[\s\S]*?\*\//g, '');
const webBaseCss = readFileSync(resolve(repositoryRoot, 'web/src/styles/base.css'), 'utf8')
  .replace(/\/\*[\s\S]*?\*\//g, '');

type CssBlock = {
  selector: string;
  declarations: Record<string, string>;
};
type PaletteBlock = CssBlock & { name: string };

function parseDeclarations(body: string): Record<string, string> {
  const declarations: Record<string, string> = {};
  for (const match of body.matchAll(/([-\w]+)\s*:\s*([^;]+)\s*(?:;|$)/g)) {
    declarations[match[1]] = match[2].trim();
  }
  return declarations;
}

function parseBlocks(css: string): CssBlock[] {
  return [...css.matchAll(/([^{}]+)\{([^{}]*)\}/g)].map((match) => ({
    selector: match[1].trim(),
    declarations: parseDeclarations(match[2]),
  }));
}

const blocks = parseBlocks(tokensCss);
const baseBlocks = parseBlocks(webBaseCss);
const webRootBlock = baseBlocks.find(({ selector }) => selector === ':root')!;
const primaryButtonBlock = baseBlocks.find(({ selector }) => selector === '.button--primary')!;
const rootBlock = blocks.find(({ selector }) => selector === ':root')!;
const darkBlock = blocks.find(({ selector }) => /^\[data-theme=['"]dark['"]\]$/.test(selector))!;
const systemDarkBlock = blocks.find(({ selector }) => selector.includes("[data-theme='system']"))!;
const darkMetrolistBlock = blocks.find(({ selector }) =>
  selector === "[data-theme='dark'][data-palette='metrolist']",
)!;
const paletteBlocks: PaletteBlock[] = blocks.flatMap((block): PaletteBlock[] => {
  const match = block.selector.match(/^\[data-palette=['"]([^'"]+)['"]\]$/);
  return match ? [{ name: match[1], ...block }] : [];
});
const testedPalettes: PaletteBlock[] = [
  { name: 'default', selector: ':root-default', declarations: {} },
  ...paletteBlocks,
];

if (!webRootBlock || !primaryButtonBlock) {
  throw new Error('base.css must define a :root token scope and the primary button role.');
}
if (!rootBlock || !darkBlock || !systemDarkBlock || !darkMetrolistBlock) {
  throw new Error('tokens.css must define :root, explicit dark, system-dark, and dark Metrolist blocks.');
}
if (paletteBlocks.length === 0 || !paletteBlocks.some(({ name }) => name === 'metrolist')) {
  throw new Error('tokens.css must declare at least one palette and the special Metrolist palette.');
}

// System dark is a higher-specificity root override; it wins over palette tokens
// where both declare a property. The explicit Metrolist dark selector applies
// only to data-theme="dark", not to data-theme="system".
const effectiveModes = [
  { name: 'light', beforePalette: [] as CssBlock[], afterPalette: [] as CssBlock[] },
  { name: 'system-light', beforePalette: [] as CssBlock[], afterPalette: [] as CssBlock[] },
  { name: 'dark', beforePalette: [darkBlock], afterPalette: [] as CssBlock[] },
  { name: 'system-dark', beforePalette: [] as CssBlock[], afterPalette: [systemDarkBlock] },
];

function effectiveTokens(palette: PaletteBlock, mode: (typeof effectiveModes)[number]): Record<string, string> {
  return Object.assign(
    {},
    rootBlock.declarations,
    ...mode.beforePalette.map(({ declarations }) => declarations),
    palette.declarations,
    palette.name === 'metrolist' && mode.name === 'dark'
      ? darkMetrolistBlock.declarations
      : {},
    ...mode.afterPalette.map(({ declarations }) => declarations),
  );
}

function colorChannels(hex: string): [number, number, number] {
  if (!/^#(?:[\da-fA-F]{3}|[\da-fA-F]{6})$/.test(hex)) {
    throw new Error(`Unsupported color value "${hex}"; expected a three- or six-digit sRGB hex color.`);
  }
  const digits = hex.slice(1);
  if (digits.length === 3) {
    return [...digits].map((digit) => Number.parseInt(`${digit}${digit}`, 16)) as [number, number, number];
  }
  return [0, 2, 4].map((start) => Number.parseInt(digits.slice(start, start + 2), 16)) as [number, number, number];
}

function relativeLuminance(hex: string): number {
  const [red, green, blue] = colorChannels(hex).map((channel) => {
    const value = channel / 255;
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
}

function contrastRatio(foreground: string, background: string): number {
  const foregroundLuminance = relativeLuminance(foreground);
  const backgroundLuminance = relativeLuminance(background);
  const lighter = Math.max(foregroundLuminance, backgroundLuminance);
  const darker = Math.min(foregroundLuminance, backgroundLuminance);
  return (lighter + 0.05) / (darker + 0.05);
}

function validateContrast(foreground: string, background: string, required: number) {
  const ratio = contrastRatio(foreground, background);
  return { ratio, passes: ratio >= required };
}

function describePair(
  palette: string,
  mode: string,
  foregroundToken: string,
  foreground: string,
  backgroundToken: string,
  background: string,
  ratio: number,
  required: number,
): string {
  return `Palette=${palette} | Mode=${mode} | Foreground=${foregroundToken} (${foreground}) | Background=${backgroundToken} (${background}) | Contrast=${ratio.toFixed(2)}:1 | Required=${required}:1`;
}

function requiredColor(tokens: Record<string, string>, token: string, palette: string, mode: string): string {
  const value = tokens[token];
  if (!value) throw new Error(`Palette=${palette} | Mode=${mode} | Missing required token ${token}.`);
  return value;
}

describe('shipped design palette contrast', () => {
  it('meets normal text contrast for every primary palette', () => {
    const failures: string[] = [];

    for (const palette of paletteBlocks) {
      for (const token of ['--color-on-primary', '--color-on-primary-container']) {
        if (!palette.declarations[token]) {
          failures.push(`Palette=${palette.name} | Missing explicit foreground declaration ${token}.`);
        }
      }
    }

    for (const palette of testedPalettes) {
      for (const mode of effectiveModes) {
        const tokens = effectiveTokens(palette, mode);
        const pairs = [
          { foreground: '--color-on-primary', background: '--color-primary' },
          { foreground: '--color-on-primary-container', background: '--color-primary-container' },
        ];

        for (const pair of pairs) {
          const foreground = requiredColor(tokens, pair.foreground, palette.name, mode.name);
          const background = requiredColor(tokens, pair.background, palette.name, mode.name);
          const result = validateContrast(foreground, background, 4.5);
          if (!result.passes) {
            failures.push(describePair(
              palette.name, mode.name, pair.foreground, foreground,
              pair.background, background, result.ratio, 4.5,
            ));
          }
        }
      }
    }

    const webButtonForeground = requiredColor(primaryButtonBlock.declarations, 'color', 'web-primary-button', 'light');
    const backgroundRole = requiredColor(primaryButtonBlock.declarations, 'background', 'web-primary-button', 'light')
      .match(/^var\((--[\w-]+)\)$/)?.[1];
    if (!backgroundRole) throw new Error('The primary button background must reference a CSS custom property.');
    const sharedToken = requiredColor(webRootBlock.declarations, backgroundRole, 'web-primary-button', 'light')
      .match(/^var\((--[\w-]+)\)$/)?.[1];
    if (!sharedToken) throw new Error(`The primary button role ${backgroundRole} must resolve through a shared token.`);
    const webButtonBackground = requiredColor(rootBlock.declarations, sharedToken, 'web-primary-button', 'light');
    const webButtonResult = validateContrast(webButtonForeground, webButtonBackground, 4.5);
    if (!webButtonResult.passes) {
      failures.push(describePair(
        'web-primary-button', 'light/dark', 'color', webButtonForeground,
        backgroundRole, webButtonBackground, webButtonResult.ratio, 4.5,
      ));
    }

    expect(failures, failures.join('\n')).toEqual([]);
  });

  it('meets non-text contrast for interactive controls', () => {
    const failures: string[] = [];

    for (const palette of testedPalettes) {
      for (const mode of effectiveModes) {
        const tokens = effectiveTokens(palette, mode);
        const outline = requiredColor(tokens, '--color-outline', palette.name, mode.name);
        for (const surfaceToken of ['--color-background', '--color-surface']) {
          const surface = requiredColor(tokens, surfaceToken, palette.name, mode.name);
          const result = validateContrast(outline, surface, 3);
          if (!result.passes) {
            failures.push(describePair(
              palette.name, mode.name, '--color-outline', outline,
              surfaceToken, surface, result.ratio, 3,
            ));
          }
        }
      }
    }

    expect(failures, failures.join('\n')).toEqual([]);
  });

  it('fails a deliberately low-contrast pair', () => {
    const result = validateContrast('#FFFFFF', '#FDD835', 4.5);
    expect(result.ratio).toBeLessThan(4.5);
    expect(result.passes, describePair(
      'deliberate-low-contrast', 'light', '#FFFFFF', '#FFFFFF',
      '#color-primary', '#FDD835', result.ratio, 4.5,
    )).toBe(false);
  });
});
