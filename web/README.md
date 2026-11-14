# Caregiver Daily Notes — web application

React 18 + TypeScript + Vite, with a bilingual public landing page at `/`, caregiver authentication, recipient management,
daily notes/addenda, history, summaries/safety, and care plans. The complete web
quality gate and [integration handoff](HANDOFF.md) cover Tasks 1–9. Backend and
clinical logic remain server-owned.

The public `/` route is implemented in `src/routes/LandingRoute.tsx`; its copy and section layout are documented in [`src/features/landing/README.md`](src/features/landing/README.md). `/app/*` remains protected and continues to use the existing SPA fallback.

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

The [quality suite overview](e2e/README.md) maps the 232 browser cases to the
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

To run all 24 real-backend cases, including the complete login-to-plan journey,
use `npm run test:e2e:live` after starting the disposable backend and synthetic
provider as described in [HANDOFF.md](HANDOFF.md). Live tests are separate from
the default backend-free browser suite.

The `dist/` directory is an independent static site. Deploy it with SPA routing
fallback to `index.html` for client-side routes. The handoff documents API/docs
proxy routing, runtime config, CORS and shared-environment verification. Changes
to `.github/workflows` and deployment are outside this issue.

## Design tokens / dashboard UI tokens

Shared color and sizing tokens live in `../docs/design-system/tokens.css`; dashboard-specific aliases use the `--dash-*` prefix there. Existing `--space-*`, `--radius-*`, `--color-*`, `--caregiver-*`, and `--web-*` names remain the source where already defined. `src/styles/base.css` contains global/shared foundations and theme-preview styles, `src/styles/ui.css` contains shared controls and app/dashboard presentation, and `src/styles/public.css` contains confirmed public landing/illustration presentation. `main.tsx` imports `public.css` after the shared/app sheets to preserve the established cascade. Shared styles such as `.container`, the header/navigation, buttons, `.eyebrow`, `.page-panel`, and feedback controls stay shared; dashboard-only selectors remain in `ui.css`.

The public/app audit found `.eyebrow` on dashboard headings (overview, notes, recipients, plans, and summary), so its shared base remains in `base.css`/`ui.css` rather than moving to the public sheet. `.disclaimer` is also shared with summary presentation. No dashboard feature component uses the public-only hero, illustration, journal, landing-section, or feature-card classes.

The tables below list every `--dash-*` value and the existing colors it references. Color values shown as light/dark are the default semantic-role values in `tokens.css`; the custom properties remain aliases so the selected theme/palette continues to control them. They are written explicitly for manual mirroring in mobile. Per [ARCHITECTURE.md](../ARCHITECTURE.md), the web and mobile frontends are separate and do not share frontend source code.

### Layout, spacing, borders, and elevation

| Token | Value in code | Usage |
|---|---|---|
| `--dash-space-1` | `var(--space-xs)` = `4px` | Tight spacing |
| `--dash-space-2` | `var(--space-sm)` = `8px` | Compact spacing |
| `--dash-space-3` | `12px` | Dense control spacing |
| `--dash-space-4` | `var(--space-md)` = `16px` | Standard compact spacing |
| `--dash-space-5` | `var(--space-lg)` = `24px` | Component spacing |
| `--dash-space-6` | `var(--space-xl)` = `32px` | Large component spacing |
| `--dash-space-7` | `40px` | Section spacing |
| `--dash-space-8` | `var(--space-xxl)` = `48px` | Extra section spacing |
| `--dash-sidebar-expanded` | `256px` | Expanded dashboard sidebar |
| `--dash-sidebar-rail` | `72px` | Icon-rail dashboard sidebar |
| `--dash-radius-card` | `var(--radius-card)` = `14px` | Card corners |
| `--dash-border-width` | `1px` | Standard border width |
| `--dash-border-style` | `solid` | Standard border style |
| `--dash-border-color` | `var(--caregiver-border-soft)` = `#E3E7EA` light / `#2A3336` dark | Standard subtle border |
| `--dash-elevation-1` | `0 1px 2px color-mix(in srgb, var(--caregiver-ink) 8%, transparent)` | Resting surface |
| `--dash-elevation-2` | `0 4px 12px color-mix(in srgb, var(--caregiver-ink) 10%, transparent), 0 1px 3px color-mix(in srgb, var(--caregiver-ink) 5%, transparent)` | Raised card |
| `--dash-elevation-3` | `0 12px 32px color-mix(in srgb, var(--caregiver-ink) 12%, transparent), 0 3px 8px color-mix(in srgb, var(--caregiver-ink) 6%, transparent)` | Dialog/high emphasis |

### Surfaces and chips

| Token | Value in code | Default light / dark |
|---|---|---|
| `--dash-color-page` | `var(--caregiver-background)` | `#F7F6F3` / `#121616` |
| `--dash-color-surface` | `var(--caregiver-surface)` | `#FAF9F5` / `#1A1F1F` |
| `--dash-color-surface-raised` | `var(--color-surface-container)` | `#FAF9F5` / `#1A1F1F` |
| `--dash-chip-neutral-background` | `var(--caregiver-pill-gray-container)` | `#E8EDF3` / `#2A3336` |
| `--dash-chip-neutral-foreground` | `var(--caregiver-pill-gray-content)` | `#34495E` / `#B0BEC5` |
| `--dash-chip-neutral-border` | `var(--caregiver-border-soft)` | `#E3E7EA` / `#2A3336` |
| `--dash-chip-recorded-background` | `var(--caregiver-success-container)` | `#E3F1E8` / `#1B3A24` |
| `--dash-chip-recorded-foreground` | `var(--caregiver-success)` | `#1F5C38` / `#81C784` |
| `--dash-chip-recorded-border` | `var(--caregiver-border-soft)` | `#E3E7EA` / `#2A3336` |
| `--dash-chip-missing-background` | `var(--caregiver-warning-container)` | `#FBF0D9` / `#3A2A0A` |
| `--dash-chip-missing-foreground` | `var(--caregiver-warning)` | `#7A4B00` / `#FFB74D` |
| `--dash-chip-missing-border` | `var(--caregiver-warning)` | `#7A4B00` / `#FFB74D` |
| `--dash-chip-unavailable-background` | `var(--color-surface-variant)` | `#E3E7EA` / `#2A3336` |
| `--dash-chip-unavailable-foreground` | `var(--caregiver-muted)` | `#5B6B70` / `#B0BEC5` |
| `--dash-chip-unavailable-border` | `var(--caregiver-border)` | `#C5CDD1` / `#3A4548` |

The existing sage identity remains `--web-color-green: #285b45`, `--web-color-green-dark: #1d4635`, `--caregiver-primary: #0E6B66`, and `--caregiver-primary-soft: #DCE5D6` (dark soft surface: `#1A3A38`). Dashboard surface and chip aliases refer to the current semantic theme roles rather than fixed colors.

### Dense-UI typography

| Token family | Size | Line-height (Latin / Arabic) | Weight | Intended usage |
|---|---:|---:|---:|---|
| `--dash-type-caption-*` | `12px` | `1.65` / `1.8` | `400` | Metadata, helper text, compact status |
| `--dash-type-label-*` | `13px` | `1.5` / `1.8` | `600` | Dense controls and labels |
| `--dash-type-body-*` | `14px` | `1.65` / `1.9` | `400` | Dashboard body text |
| `--dash-type-title-*` | `16px` | `1.5` / `1.65` | `600` | Card and form titles |
| `--dash-type-section-*` | `20px` | `1.4` / `1.6` | `650` | Dashboard section headings |

Each family defines `size`, `line-height`, `line-height-ar`, and `weight` custom properties in `tokens.css`. Existing dashboard spacing, neutral page background, shared borders, and status chips now consume the corresponding tokens without changing their current appearance.

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
- Features belong under `src/features/<feature>/` and use the centralized API client. The workspace overview, recipient and note integration details live in their feature READMEs; reusable UI is in `src/components`, shared helpers in `src/lib`.
- `/app` presents the authenticated date-scoped workspace overview using real recipient and notes-history APIs; see [`src/features/overview/README.md`](src/features/overview/README.md) for route, query-state, and missing-data semantics.
- `Button`/`ButtonLink`, `Icon`, `FeatureCard`, and `PagePanel` provide shared presentation primitives. Decorative illustrations contain no patient data.
- Route changes focus the main landmark and update the translated document title. Arabic typography uses its own font, spacing, and line height; layout uses logical CSS properties. Animations respect reduced-motion preferences.
- Authentication and authorization remain backend-owned. Never place provider keys, LLM calls, diagnosis logic, or safety decisions in the browser.

## Definition of scope

Task #21 changes are limited to `web/`. Submit for owner review and merge.
