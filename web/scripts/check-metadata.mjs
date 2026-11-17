import { existsSync, readFileSync } from 'node:fs';
import { basename, resolve } from 'node:path';

// Runs automatically as `postbuild` (see package.json). Fails the build if a
// production placeholder survived HTML substitution or if a social image URL
// no longer points at a real PNG in the output. Using an npm lifecycle hook
// instead of appending to `build` keeps `npm run build -- --mode e2e` working:
// npm forwards those extra args to `build` only, not to `postbuild`.
const indexFile = resolve('dist/index.html');
if (!existsSync(indexFile)) {
  console.error('check-metadata: dist/index.html not found — run the build first.');
  process.exit(1);
}
const html = readFileSync(indexFile, 'utf8');
const problems = [];

if (/REPLACE-WITH-/i.test(html)) {
  problems.push('an unresolved REPLACE-WITH- placeholder is present in dist/index.html');
}

const imagePattern = /(?:property="og:image"|name="twitter:image")\s+content="([^"]+)"/g;
const images = [...html.matchAll(imagePattern)].map((match) => match[1]);
if (images.length === 0) {
  problems.push('no og:image/twitter:image tags were found in dist/index.html');
}
for (const url of images) {
  let parsed;
  try {
    parsed = new URL(url);
  } catch {
    problems.push(`${url} is not an absolute URL`);
    continue;
  }
  if (!/\.png$/i.test(parsed.pathname)) {
    problems.push(`${url} does not reference a PNG`);
  }
  if (!existsSync(resolve('dist', basename(parsed.pathname)))) {
    problems.push(`${url} references an asset that is missing from dist/`);
  }
}

if (problems.length > 0) {
  console.error(`check-metadata: ${problems.join('; ')}.`);
  process.exit(1);
}
console.log('check-metadata: no placeholders remain and both social images are absolute URLs to real PNGs.');
