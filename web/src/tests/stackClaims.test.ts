import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const read = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8');

/**
 * The stack story must match the code. Issue #842 chose Option B: drop Redis
 * from every claim instead of adding a client that does not exist.
 */
describe('public stack claims match the implementation (#842)', () => {
  it.each([
    ['README.md', '../../../README.md'],
    ['.env.example', '../../../.env.example'],
    ['backend/pom.xml', '../../../backend/pom.xml'],
    ['application.yml', '../../../backend/src/main/resources/application.yml'],
  ])('%s does not advertise a Redis component', (_name, path) => {
    expect(read(path).toLowerCase()).not.toMatch(/redis|upstash/);
  });

  it('landing page stack list does not advertise Redis', () => {
    expect(read('../routes/LandingRoute.tsx')).not.toMatch(/redis|upstash/i);
  });
});
