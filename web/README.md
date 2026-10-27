# Caregiver Daily Notes — web application

React 18 + TypeScript + Vite, with caregiver authentication, recipient management,
daily notes/addenda, history, summaries/safety, and care plans. Task #20 adds the
complete web quality gate. Backend and clinical logic remain server-owned.

## Run and test

From `web/`:

```sh
npm ci
npm run dev
npm run typecheck
npm test
npm run build
```

The lockfile pins the dependency tree. DM Sans and Noto Sans Arabic are served
with the app through Fontsource; fonts require no third-party CDN requests.

The default Playwright run includes the foundation and authentication smoke
specs plus the recipient, note, history, summary, and care-plan browser
contracts. It runs against the production build in Chromium at 1280, 768, 390,
and 320 pixels, in Arabic and English. Coverage includes caregiver workflows,
keyboard focus and form errors, Arabic RTL and mixed-direction content,
horizontal overflow, reduced motion, and axe WCAG 2/2.1 A/AA accessibility
checks on major screens and states.

The [quality suite overview](e2e/README.md) maps the 224 browser cases to the
workflows they cover. Language-switch tests also verify that unsaved mixed
Arabic/English drafts survive direction changes and save exactly once.
The production build type-checks the browser specs and Playwright configuration.

```sh
npx playwright install chromium
npm run test:e2e
```

If Chromium is already installed by your development environment, use it without
downloading another browser:

```sh
PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/chromium npm run test:e2e
```

The browser checks use synthetic, intercepted API responses and do not require
a backend. After building, individual feature suites can still be run with their
scoped Playwright configs, for example:

```sh
npx playwright test --config src/tests/recipients/playwright.config.ts
```

The opt-in [real-backend integration suite](src/tests/integration/README.md)
checks login and recipient workflows against a running disposable local backend,
including server-enforced ownership and preservation of an existing note.

The `dist/` directory is an independent static site. Deploy it with SPA routing fallback to `index.html` for client-side routes. The existing root CI job does not yet include frontend checks; changes to `.github/workflows` are outside this issue.

## Public runtime configuration (important for rotating tunnel URLs)

Every page load fetches `config.json` using `cache: 'no-store'` **before** mounting the app. The *only* backend URL setting is `apiBaseUrl` in this file, not `import.meta.env` / `VITE_*` / compiled JavaScript. The checked-in `public/config.json` contains `{"apiBaseUrl":""}` for a same-origin proxy. To call the current HTTPS backend tunnel, set:

```json
{ "apiBaseUrl": "https://current-backend.trycloudflare.com" }
```

Change **only the hosted `config.json`** to rotate the tunnel URL without rebuilding the app. When using a static deployment, upload/republish just this config asset, and configure its CDN/cache policy to revalidate rather than cache it permanently. Alternatively, generate a fresh post-build asset from an environment variable:

```sh
npm run build
API_BASE_URL=https://current-backend.trycloudflare.com npm run config:runtime
```

This modifies `dist/config.json`, not the built JS bundle; repeating that step requires **no rebuild**. A failed or malformed config shows a retryable error rather than silently sending requests to a guessed backend. Only public origins/paths are permitted in this JSON — **never tokens, JWTs, passwords, or API keys**. HTTPS is required for remote backend URLs; HTTP is accepted for loopback development only.

## API contract assumptions and future integration

- The backend is a separate Spring Boot REST API using JWT authentication. No endpoint routes, payload fields, token lifecycle, or ownership semantics are invented by this foundation; feature contributors must consult the actual backend controllers/OpenAPI contract before wiring requests.
- `src/lib/api.tsx` provides `useApiClient().request<TResponse, TBody>({ path, method, body, signal, accessToken })`, with JSON transport, an optional `/api` prefix in `apiBaseUrl`, typed HTTP errors, and request cancellation. Calls are same-origin when `apiBaseUrl` is an empty string.
- Task #14 implements login, a tab-scoped JWT session, logout, expiry handling and protected routes. See [authentication](src/features/auth/README.md) for contract, security limits, integration and validation details.
- Features belong under `src/features/<feature>/` and use the centralized API client. Reusable UI is in `src/components`, shared helpers in `src/lib`.
- The language provider exposes `t`, `language`, `direction`, and `setLanguage`, and synchronizes `<html lang>` and `<html dir>` for Arabic and English. Translations live in `src/i18n/translations.ts`; layout CSS uses logical alignment where direction matters.
- Reusable `LoadingState`, `EmptyState`, `ErrorState`, and `FieldError` set up accessible feedback conventions. The app includes a skip link, focus-visible outlines, and responsive mobile/desktop layout.
- `Button`/`ButtonLink`, `Icon`, `FeatureCard`, and `PagePanel` provide shared presentation primitives. Decorative illustrations contain no patient data.
- Route changes focus the main landmark and update the translated document title. Arabic typography uses its own font, spacing, and line height; layout uses logical CSS properties. Animations respect reduced-motion preferences.
- Authentication and authorization remain backend-owned. Never place provider keys, LLM calls, diagnosis logic, or safety decisions in the browser.

## Definition of scope

Task #20 changes are limited to `web/`. Submit for owner review and merge.
