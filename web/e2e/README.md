# Web quality suite — Task #20

From `web/`, install dependencies and Chromium, then run the complete suite:

```sh
npm ci
npx playwright install chromium
npm run test:e2e
```

`npm run test:e2e` type-checks and builds the production app, starts Vite preview,
and discovers both `e2e/**/*.spec.ts` and `src/tests/**/*.browser.ts`. The scoped
feature configs remain available for focused checks after building.

An existing Chromium installation can be selected without changing dependencies:

```sh
PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/path/to/chromium npm run test:e2e
```

## Coverage

Each case runs in English and Arabic at 1280, 768, 390, and 320 pixels. Counts
include these language and viewport combinations.

| Workflow | Browser cases | Main assertions |
| --- | ---: | --- |
| Foundation and authentication | 48 | Protected routes, login/logout/expiry, keyboard navigation, validation, language persistence, reduced motion, axe |
| Recipient management | 32 | Create/edit/deactivate, modal focus trap and restoration, loading/error/empty states, axe |
| Notes and addenda | 32 | Immutable originals, separate corrections, exact mixed text, idempotent retry, validation, axe |
| History | 32 | Recipient/date filters, URL and Back restoration, pagination, corrections, invalid/empty/error states, axe |
| Summary and safety | 32 | 7/14/30-day requests, evidence and uncertainty, persistent safety on failures, cancellation, keyboard, axe |
| Care plans | 40 | Parameter-only edits, explicit accept/dismiss, version history, protected medication fields, conflicts, axe |
| Language changes during a draft | 8 | Note and correction drafts survive both direction changes; long mixed text and controls fit the viewport; keyboard saves occur once; originals remain intact after reload; axe |
| **Total** | **224** | |

The suites check Arabic `lang`/`dir`, bidi isolation or `dir="auto"` for content,
and horizontal overflow. The draft workflow additionally checks that visible
controls remain within the viewport, even when text contains long unbroken
Arabic/English strings. Axe checks WCAG 2/2.1 A/AA tags on the exercised screens,
forms, errors, and confirmations, including color contrast.

Assertions wait for visible UI state, focus, or responses; they do not use fixed
sleeps. Each test has a separate browser context and synthetic API fixtures.
History tests fix calendar time to the fixture period, including session expiry,
so resetting to the rolling 30-day range remains repeatable on later dates.
Request and interaction timers continue running normally.
The draft workflow rejects unexpected API routes and checks the exact saved
text, distinct save keys, and write count.

## Validation boundary

These are frontend browser contract tests with synthetic identities and records.
They exercise the rendered production app but do not establish backend ownership,
external AI availability, deployed CORS, or production database behavior. Opt-in
live suites remain under `src/tests/*-live/` and `src/tests/integration/`; they are
excluded from the default run.

Task #20 was checked with Chromium 133.0.6943.0. Standard Playwright-managed
Chromium remains the default for development environments.
