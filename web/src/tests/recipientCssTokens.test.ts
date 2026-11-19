import { readFileSync, readdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const read = (relativePath: string) => readFileSync(resolve(repoRoot, relativePath), 'utf8');
const recipientsCss = read('web/src/features/recipients/recipients.css');
const tokensCss = read('docs/design-system/tokens.css').replace(/\/\*[\s\S]*?\*\//g, '');

const HEX = /#[0-9a-fA-F]{3,8}/g;
/** Recorded counts for stylesheets this issue does not change; the guard stops growth.
 * Re-recorded after the auth-screen (#959) and landing-page (#958) ports, which
 * intentionally carry their own palette literals. */
const baselineHexCounts: Record<string, number> = {
  'ai/ai-settings.css': 2,
  'auth/auth.css': 29,
  'history/history.css': 9,
  'landing/landing.css': 87,
  'notes/notes.css': 51,
  'plans/plans.css': 13,
  'recipients/recipients.css': 0,
};

function featureCssFiles(): string[] {
  const featuresDir = resolve(repoRoot, 'web/src/features');
  return readdirSync(featuresDir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .flatMap((entry) => readdirSync(resolve(featuresDir, entry.name))
      .filter((file) => file.endsWith('.css'))
      .map((file) => `${entry.name}/${file}`));
}

function tokenValue(blockSelector: string, token: string): string {
  const start = tokensCss.indexOf(blockSelector + ' {');
  expect(start).toBeGreaterThanOrEqual(0);
  const block = tokensCss.slice(start, tokensCss.indexOf('}', start));
  const match = block.match(new RegExp(`${token}\\s*:\\s*([^;]+)`));
  if (!match) throw new Error(`${token} missing in ${blockSelector}`);
  const value = match[1].trim();
  // Theme blocks alias the raw palette through var(--dark-…); resolve one level.
  const reference = value.match(/^var\((--[^)]+)\)$/);
  const hex = (reference ? tokensCss : value).match(
    reference ? new RegExp(`${reference[1]}\\s*:\\s*(#[0-9a-fA-F]{3,8})`) : /^(#[0-9a-fA-F]{3,8})$/,
  );
  if (!hex) throw new Error(`${token} has no hex value in ${blockSelector}`);
  return hex[1];
}

function luminance(hex: string): number {
  const raw = hex.slice(1);
  const full = raw.length === 3 ? raw.split('').map((c) => c + c).join('') : raw;
  const channels = [0, 2, 4]
    .map((index) => parseInt(full.slice(index, index + 2), 16) / 255)
    .map((value) => value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4);
  return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
}

function contrastRatio(foreground: string, background: string): number {
  const first = luminance(foreground);
  const second = luminance(background);
  return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
}

describe('recipient stylesheet theme tokens', () => {
  it('contains no hardcoded hex literals', () => {
    expect(recipientsCss.match(HEX)).toBeNull();
  });

  it('does not add hex literals to any feature stylesheet', () => {
    for (const file of featureCssFiles()) {
      const count = read(`web/src/features/${file}`).match(HEX)?.length ?? 0;
      expect(count, `${file} must not gain new hex literals`).toBeLessThanOrEqual(baselineHexCounts[file] ?? 0);
    }
  });

  it('uses the themed outline token for every focus ring', () => {
    const focusRules = recipientsCss.split('\n').filter((line) => line.includes(':focus-visible'));
    expect(focusRules).toHaveLength(6);
    for (const rule of focusRules) {
      expect(rule).toContain('outline: 3px solid var(--color-outline)');
    }
  });

  it('keeps the focus ring above 3:1 against the input surface in both themes', () => {
    for (const scope of [':root', "[data-theme='dark']"]) {
      const ring = tokenValue(scope, '--color-outline');
      const surface = tokenValue(scope, '--caregiver-surface');
      expect(contrastRatio(ring, surface), `${scope} focus ring`).toBeGreaterThanOrEqual(3);
    }
  });

  it('removed the dead danger-button rules', () => {
    expect(recipientsCss).not.toContain('recipient-danger');
  });
});
