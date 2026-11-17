import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const testsDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(testsDir, '../../..');
const read = (relativePath: string) => readFileSync(resolve(repoRoot, relativePath), 'utf8');

const baseCss = read('web/src/styles/base.css');
const uiCss = read('web/src/styles/ui.css');
const publicCss = read('web/src/styles/public.css');
const allCss = `${baseCss}\n${uiCss}\n${publicCss}\n${read('web/src/features/settings/settings.css')}`;

const ruleFor = (source: string, selector: string) =>
  source.split('\n').find((line) => line.trim().startsWith(selector)) ?? '';

describe('Arabic letter-spacing', () => {
  it('resets the brand tagline tracking under :lang(ar)', () => {
    // :lang(ar) .brand-tagline (0,2,0) outranks ui.css .brand-tagline (0,1,0),
    // so the zero-tracking override wins in Arabic regardless of source order.
    expect(ruleFor(baseCss, ':lang(ar) .brand-tagline')).toContain('letter-spacing: 0');
    expect(ruleFor(uiCss, '.brand-tagline')).toContain('letter-spacing: .03em');
  });

  it('has no tracked selector from issue #855 left without an Arabic override', () => {
    // Three of the four selectors named in the issue were removed by the #833
    // public.css dead-class prune, so only .brand-tagline needed a fix.
    expect(publicCss).not.toMatch(/hero-eyebrow|section-heading|home-invitation/);
    for (const selector of ['.brand-tagline', '.eyebrow', '.brand-text', 'h1', 'h2']) {
      expect(allCss, `missing :lang(ar) zero-tracking rule for ${selector}`)
        .toMatch(new RegExp(`:lang\\(ar\\)[^\\n]*${selector}[^\\n]*letter-spacing: 0`));
    }
  });

  it('leaves English tracking values untouched', () => {
    expect(ruleFor(baseCss, '.eyebrow')).toContain('letter-spacing: .14em');
    expect(ruleFor(uiCss, '.eyebrow')).toContain('letter-spacing: .13em');
    expect(read('web/src/features/settings/settings.css')).toContain('letter-spacing: -.045em');
  });
});
