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

function countTokenImports(source: string): number {
  const count = source.split('\n').filter((line) => line.includes('design-system/tokens.css')).length;
  return count;
}

function definedTokenNames(source: string): Set<string> {
  const names = new Set<string>();
  for (const match of source.matchAll(/--[A-Za-z0-9-]+:/g)) {
    names.add(match[0].slice(0, -1));
  }
  return names;
}

function tokenValueMap(source: string): Map<string, string> {
  const values = new Map<string, string>();
  for (const match of source.matchAll(/--([A-Za-z0-9-]+):\s*([^;]+);/g)) {
    values.set(match[1], match[2].trim());
  }
  return values;
}

function collectVarNames(source: string): string[] {
  const names: string[] = [];
  for (const match of source.matchAll(/var\((--[A-Za-z0-9-]+)\)/g)) {
    names.push(match[1]);
  }
  return names;
}

function rootBlock(source: string): string {
  const start = source.indexOf('{');
  const end = source.indexOf('}', start);
  return source.slice(start, end + 1);
}

const tokenNames = definedTokenNames(tokensCssSource);
const tokenValues = tokenValueMap(tokensCssSource);

describe('design tokens', () => {
  it('loads the shared token stylesheet once', () => {
    expect(countTokenImports(mainTsxSource)).toBe(1);
  });

  it('uses existing semantic variables for global colors', () => {
    const root = rootBlock(baseCssSource);
    const rootVarNames = collectVarNames(root);

    for (const name of rootVarNames) {
      expect(tokenNames.has(name)).toBe(true);
    }

    const rootDeclarations = root.matchAll(/--([A-Za-z0-9-]+):\s*([^;]+);/g);
    for (const decl of rootDeclarations) {
      const propValue = decl[2].trim();
      const hasMatchingToken = [...tokenValues.values()].some((v) => v === propValue);
      if (hasMatchingToken) {
        expect(propValue).not.toMatch(/^#[0-9a-fA-F]{3,8}$/);
      }
    }
  });
});
