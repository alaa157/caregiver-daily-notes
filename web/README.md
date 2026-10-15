# Caregiver Daily Notes — web foundation (#13)

React 18 + TypeScript + Vite. Task #13 provides the foundation; Task #14 adds caregiver authentication. Recipients, notes, history, summaries and plans remain in their separate issues. Backend and clinical logic remain server-owned.

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

Browser checks run against the production build in Chromium at 1280, 768, 390,
and 320 pixels, in Arabic and English. They cover navigation, the anonymous
route guard, language persistence, keyboard focus, horizontal overflow,
reduced motion, and axe WCAG A/AA accessibility checks including contrast.

```sh
npx playwright install chromium
npm run test:e2e
```

If Chromium is already installed by your development environment, use it without
downloading another browser:

```sh
PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/chromium npm run test:e2e
```

No real or simulated backend endpoints are used by these browser checks.

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
- `Button`/`ButtonLink`, `Icon`, `FeatureCard`, and `PagePanel` provide shared presentation primitives. Decorative illustrations contain no patient data. Upcoming features are labeled clearly; sign-in remains unavailable.
- Route changes focus the main landmark and update the translated document title. Arabic typography uses its own font, spacing, and line height; layout uses logical CSS properties. Animations respect reduced-motion preferences.
- Authentication and authorization remain backend-owned. Never place provider keys, LLM calls, diagnosis logic, or safety decisions in the browser.

## Definition of scope

Only `web/` changes are part of #13. Do not change backend/mobile, infra, workflows or repository settings; submit for owner review rather than merging your own PR.
