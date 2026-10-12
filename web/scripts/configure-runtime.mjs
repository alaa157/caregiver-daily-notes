import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

// Run *after* vite build: API_BASE_URL=https://current-tunnel.example npm run config:runtime
// This updates one public JSON asset, not compiled JavaScript. It must not contain secrets.
const configFile = resolve('dist/config.json');
const raw = process.env.API_BASE_URL;
if (raw === undefined) {
  throw new Error('Set API_BASE_URL explicitly (use an empty string for same-origin API).');
}
const url = raw.trim();
if (url) {
  const parsed = new URL(url);
  const local = parsed.protocol === 'http:' && ['localhost', '127.0.0.1', '[::1]'].includes(parsed.hostname);
  if ((parsed.protocol !== 'https:' && !local) || parsed.username || parsed.password || parsed.search || parsed.hash) {
    throw new Error('API_BASE_URL must be an HTTPS URL (HTTP allowed for local loopback only) without credentials, query, or fragment.');
  }
}
const existing = JSON.parse(await readFile(configFile, 'utf8'));
await writeFile(configFile, `${JSON.stringify({ ...existing, apiBaseUrl: url.replace(/\/+$/, '') }, null, 2)}\n`);
console.log('Updated runtime API URL in dist/config.json; no rebuild required.');
