# Caregiver Daily Notes — web foundation (#13)

React 18 + TypeScript + Vite. This folder is the frontend-only foundation for issues #14–#21; no authentication, backend, AI, or clinical logic is mocked or implemented in issue #13.

## Run and test

From `web/`:

```sh
npm install
npm run dev
npm run typecheck
npm test
npm run build
```

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
- Task #14 must replace the deliberately **anonymous** authentication provider with the real backend session mechanism; `ProtectedRoute` already sends anonymous users to `/login`, and the sign-in page is clearly unavailable until then.
- Features belong under `src/features/<feature>/` and use the centralized API client. Reusable UI is in `src/components`, shared helpers in `src/lib`.
- The language provider exposes `t`, `language`, `direction`, and `setLanguage`, and synchronizes `<html lang>` and `<html dir>` for Arabic and English. Translations live in `src/i18n/translations.ts`; layout CSS uses logical alignment where direction matters.
- Reusable `LoadingState`, `EmptyState`, `ErrorState`, and `FieldError` set up accessible feedback conventions. The app includes a skip link, focus-visible outlines, and responsive mobile/desktop layout.
- Authentication and authorization remain backend-owned. Never place provider keys, LLM calls, diagnosis logic, or safety decisions in the browser.

## Definition of scope

Only `web/` changes are part of #13. Do not change backend/mobile, infra, workflows or repository settings; submit for owner review rather than merging your own PR.
